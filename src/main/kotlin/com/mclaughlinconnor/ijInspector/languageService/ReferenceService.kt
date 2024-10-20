package com.mclaughlinconnor.ijInspector.languageService

import com.intellij.find.FindManager
import com.intellij.find.findUsages.*
import com.intellij.find.impl.FindManagerImpl
import com.intellij.ide.impl.DataManagerImpl
import com.intellij.injected.editor.VirtualFileWindow
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.findDocument
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.PsiReference
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.SearchScope
import com.intellij.usages.*
import com.intellij.usages.impl.UsageViewManagerImpl
import com.intellij.util.ArrayUtil
import com.intellij.util.ConcurrencyUtil
import com.intellij.util.containers.ContainerUtil
import com.mclaughlinconnor.ijInspector.lsp.*
import com.mclaughlinconnor.ijInspector.rpc.Connection
import com.mclaughlinconnor.ijInspector.utils.Utils.Companion.createDocument
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Supplier


// TODO: support streaming the results using $/progress
class ReferenceService(
    private val myProject: Project,
    private val myConnection: Connection
) {
    private val definitionService: DefinitionService = DefinitionService(myProject, myConnection)
    private val injectedLanguageManager = InjectedLanguageManager.getInstance(myProject)
    private val messageFactory: com.mclaughlinconnor.ijInspector.rpc.MessageFactory =
        com.mclaughlinconnor.ijInspector.rpc.MessageFactory()
    private val myApplication: Application = ApplicationManager.getApplication()

    fun doReferences(requestId: Int, params: ReferenceParams) {
        val filePath = params.textDocument.uri.substring("file://".length)
        val position = params.position
        val document = createDocument(myProject, filePath) ?: return

        val cursorOffset = document.getLineStartOffset(position.line) + position.character

        myApplication.invokeLater {
            val psiFile = PsiDocumentManager.getInstance(myProject).getPsiFile(document) ?: return@invokeLater

            var element = psiFile.findElementAt(cursorOffset)

            while (element != null) {
                when (element) {
                    is PsiReference -> {
                        element = element.resolve()
                        break
                    }

                    is PsiNamedElement -> {
                        break
                    }

                    else -> element = element.parent
                }
            }

            val locations: MutableList<Location> = mutableListOf()

            if (params.context.includeDeclaration) {
                definitionService.fetchDefinitions(psiFile, cursorOffset, { l -> locations.add(l) }, {})
            }

            val onComplete = { myConnection.write(messageFactory.newMessage(Response(requestId, locations))) }
            val handleUsage = { result: UsageInfo2UsageAdapter -> usageToLocation(result)?.let { locations.add(it) } }

            if (element == null) {
                onComplete()
            }

            // Possibly find references for both injected and non-injected?
            val injectedElement = injectedLanguageManager.findInjectedElementAt(psiFile, cursorOffset)
            if (injectedElement != null) {
                element = injectedElement
            }

            findReferences(element!!, handleUsage, onComplete)
        }
    }

    private fun usageToLocation(usage: UsageInfo2UsageAdapter): Location? {
        var path: String? = null
        var range: Range? = null

        myApplication.runReadAction {
            var startOffset = usage.navigationRange.startOffset
            var endOffset = usage.navigationRange.endOffset

            var virtualFile = usage.file
            var document = virtualFile.findDocument() ?: return@runReadAction

            if (virtualFile is VirtualFileWindow) {
                val vText = virtualFile.documentWindow.text
                val parentFile = virtualFile.delegate
                document = parentFile.findDocument() ?: return@runReadAction
                val pText = document.text

                val vOffset = pText.indexOf(vText)

                startOffset += vOffset
                endOffset += vOffset

                virtualFile = parentFile
            }

            path = virtualFile.path

            val startLine = document.getLineNumber(startOffset)
            val endLine = document.getLineNumber(endOffset)

            val startColumn = startOffset - document.getLineStartOffset(startLine)
            val endColumn = endOffset - document.getLineStartOffset(endLine)

            val startPosition = Position(startLine, startColumn)
            val endPosition = Position(endLine, endColumn)

            range = Range(startPosition, endPosition)
        }

        if (range == null) {
            return null
        }

        return Location("file://${path}", range!!)
    }

    private fun findReferences(
        element: PsiElement, handleUsage: (UsageInfo2UsageAdapter) -> Any?, onComplete: () -> Unit
    ) {
        val handler = getHandler(element) ?: return

        val options = handler.findUsagesOptions // could be configured?

        val primaryTargets = convertToUsageTargets(listOf(*handler.primaryElements), options)
        val secondaryTargets = convertToUsageTargets(listOf(*handler.secondaryElements), options)

        val searchFor: Array<UsageTarget?> = ArrayUtil.mergeArrays(primaryTargets, secondaryTargets)
        val scopeSupplier: Supplier<SearchScope> = getMaxSearchScopeToWarnOfFallingOutOf(searchFor)

        val boundaryScope = ReadAction.compute<SearchScope, RuntimeException> { scopeSupplier.get() }
        val atomicUsageCount = AtomicInteger(0)

        val usageSearcher = FindUsagesManager.createUsageSearcher(
            handler, handler.primaryElements, handler.secondaryElements, options
        )
        myApplication.executeOnPooledThread {
            val indicator = ProgressManager.getInstance().progressIndicator ?: EmptyProgressIndicator()

            val future = ConcurrencyUtil.newSingleThreadExecutor("References").submit {
                try {
                    ProgressManager.getInstance().runProcess({
                        usageSearcher.generate { usage: Usage? ->
                            if (!UsageViewManagerImpl.isInScope(usage!!, boundaryScope)) {
                                return@generate true
                            }

                            val incrementCounter = !UsageViewManager.isSelfUsage(usage, searchFor)
                            if (incrementCounter) {
                                val usageCount: Int = atomicUsageCount.incrementAndGet()
                                if (usageCount > UsageLimitUtil.USAGES_LIMIT) {
                                    return@generate false
                                }

                                ApplicationManager.getApplication().runReadAction {
                                    if (usage is UsageInfo2UsageAdapter) {
                                        handleUsage(usage)
                                    }
                                }
                            }

                            return@generate true
                        }
                    }, indicator)
                } catch (ignored: ProcessCanceledException) {
                }
            }

            try {
                future.get(2, TimeUnit.SECONDS)
            } catch (e: TimeoutException) {
                indicator.cancel()
                future.cancel(true)
            } finally {
                onComplete()
            }
        }
    }

    private fun getHandler(element: PsiElement?): FindUsagesHandler? {
        if (element == null) {
            return null
        }

        var psiElement: PsiElement? = element

        val findUsagesManager = (FindManager.getInstance(myProject) as FindManagerImpl).findUsagesManager

        var handler: FindUsagesHandler? = null

        while (handler == null) {
            if (psiElement == null) {
                break
            }

            handler = findUsagesManager.getFindUsagesHandler(
                psiElement, FindUsagesHandlerFactory.OperationMode.USAGES_WITH_DEFAULT_OPTIONS
            )

            if (handler == null) {
                psiElement = psiElement.parent
                if (psiElement is PsiReference) {
                    psiElement = psiElement.resolve() ?: psiElement
                }
            }
        }

        return handler
    }

    private fun convertToUsageTargets(
        elementsToSearch: Iterable<PsiElement?>, findUsagesOptions: FindUsagesOptions
    ): Array<UsageTarget> {
        val targets = ContainerUtil.map(elementsToSearch) { element: PsiElement? ->
            PsiElement2UsageTargetAdapter(element!!, findUsagesOptions, false)
        }
        return targets.toTypedArray<UsageTarget>()
    }

    private fun getMaxSearchScopeToWarnOfFallingOutOf(searchFor: Array<UsageTarget?>): Supplier<SearchScope> {
        val target = if (searchFor.isNotEmpty()) searchFor[0] else null
        val dataProvider = DataManagerImpl.getDataProviderEx(target)
        val scope = if (dataProvider != null) UsageView.USAGE_SCOPE.getData(dataProvider) else null
        if (scope != null) {
            return Supplier { scope }
        }
        val bgtProvider =
            if (dataProvider != null) PlatformCoreDataKeys.BGT_DATA_PROVIDER.getData(dataProvider) else null
        return Supplier<SearchScope> {
            val scope2 = if (bgtProvider != null) UsageView.USAGE_SCOPE.getData(bgtProvider) else null
            if (scope2 != null) return@Supplier scope2
            GlobalSearchScope.everythingScope(myProject) // by default do not warn of falling out of scope
        }
    }

}