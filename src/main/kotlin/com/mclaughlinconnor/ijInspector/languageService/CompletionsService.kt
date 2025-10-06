package com.mclaughlinconnor.ijInspector.languageService

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.completion.CompletionInitializationContext.IDENTIFIER_END_OFFSET
import com.intellij.codeInsight.completion.impl.CompletionServiceImpl
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.codeInsight.lookup.impl.LookupImplVetoPolicy
import com.intellij.lang.LanguageDocumentation
import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.progress.util.ProgressIndicatorBase
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.util.Consumer
import com.intellij.util.containers.toArray
import com.mclaughlinconnor.ijInspector.lsp.*
import com.mclaughlinconnor.ijInspector.lsp.CompletionContext
import com.mclaughlinconnor.ijInspector.rpc.Connection
import com.mclaughlinconnor.ijInspector.rpc.MessageFactory
import com.mclaughlinconnor.ijInspector.utils.TextEditUtil
import com.mclaughlinconnor.ijInspector.utils.Utils.Companion.createDocument
import com.mclaughlinconnor.ijInspector.utils.Utils.Companion.obtainLookup
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

fun <T> Flow<T>.debounceWithCallback(
    timeout: Duration,
    onDebounced: suspend (T) -> Unit
): Flow<T> = channelFlow {
    var lastValue: T? = null
    var debounceJob: Job? = null

    collect { value ->
        debounceJob?.let {
            it.cancel()
            lastValue?.let { removed -> onDebounced(removed) }
        }

        lastValue = value
        debounceJob = launch {
            delay(timeout)
            send(value)
            lastValue = null
        }
    }

    debounceJob?.join()
}

class AutocompleteParams(
    val id: Int = 0,
    val position: Position = Position(),
    val context: CompletionContext? = null,
    val filePath: String = "",
    val completionType: CompletionType = CompletionType.BASIC,
)

const val MAX_CACHE_VALUES = 200

@Suppress("UnstableApiUsage")
class CompletionsService(
    private val myProject: Project,
    private val myConnection: Connection,
    private val myDocumentService: DocumentService
) {
    private val fileCache: MutableList<String> = mutableListOf()
    private val messageFactory: MessageFactory =
        MessageFactory()
    private val myApplication: Application = ApplicationManager.getApplication()
    private val myDocumentationService: DocumentationService = DocumentationService(myProject)
    private val progressManager: ProgressManager = ProgressManager.getInstance()
    private val activeRequests: MutableMap<Int, ProgressIndicator> = HashMap()
    private val autocompleteFlow = MutableSharedFlow<AutocompleteParams>(0, 1000, BufferOverflow.DROP_OLDEST)

    init {
        CoroutineScope(Dispatchers.IO).launch {
            flowAutoComplete()
        }
    }

    private fun createIndicator(
        editor: Editor, completionType: CompletionType, initContext: CompletionInitializationContextImpl,
    ): CompletionProgressIndicator? {
        val lookup: LookupImpl = obtainLookup(editor, initContext.project)
        LookupImplVetoPolicy.FORCE_VETO_HIDING_ON_CHANGE.set(lookup, true)
        val handler = CodeCompletionHandlerBase.createHandler(completionType, true, false, true)

        val document = editor.document
        val psiFile = PsiDocumentManager.getInstance(myProject).getPsiFile(document) ?: return null

        val cursorOffset = initContext.caret.offset
        val position = psiFile.findElementAt(cursorOffset) ?: return null

        val indicator = IndicatorFactory.buildIndicator(
            editor,
            initContext.caret,
            initContext.invocationCount,
            handler,
            initContext.offsetMap,
            initContext.hostOffsets,
            false,
            lookup,
            psiFile,
            position,
            completionType,
            cursorOffset
        )

        CompletionServiceImpl.setCompletionPhase(CompletionPhase.Synchronous(indicator))

        return indicator
    }

    private fun createParameters(
        initContext: CompletionInitializationContextImpl, indicator: CompletionProgressIndicator,
    ): CompletionParameters {
        indicator.checkCanceled()
        try {
            CompletionInitializationUtil.insertDummyIdentifier(initContext, indicator)
        } catch (e: RuntimeException) {
            e
        }

        val applyPsiChanges = CompletionInitializationUtil.insertDummyIdentifier(initContext, indicator)
        val hostCopyOffsets = applyPsiChanges.get()

        val finalOffsets = CompletionInitializationUtil.toInjectedIfAny(initContext.file, hostCopyOffsets)
        val parameters = CompletionInitializationUtil.createCompletionParameters(initContext, indicator, finalOffsets)
        parameters.setIsTestingMode(false)

        return parameters
    }

    fun cancel(id: Int) {
        val indicator = activeRequests[id] ?: return

        indicator.cancel()
        activeRequests.remove(id)

        myConnection.write(messageFactory.newMessage(Response(id)))
    }

    fun autocomplete(
        id: Int, position: Position, context: CompletionContext?, filePath: String, completionType: CompletionType,
    ) {
        val params = AutocompleteParams(id, position, context, filePath, completionType)
        runBlocking {
            autocompleteFlow.emit(params)
        }
    }
    
    @OptIn(FlowPreview::class)
    suspend fun flowAutoComplete() {
        autocompleteFlow
            .debounceWithCallback(
                200.milliseconds,
                onDebounced = { params ->
                    myConnection.write(messageFactory.newMessage(Response(params.id)))
                }
            )
            .collect { params ->
                doDoAutocomplete(params.id, params.position, params.context, params.filePath, params.completionType)
            }
    }

    fun doDoAutocomplete(
        id: Int, position: Position, context: CompletionContext?, filePath: String, completionType: CompletionType,
    ) {
        for (request in activeRequests) {
            request.value.cancel()
            myConnection.write(messageFactory.newMessage(Response(request.key)))
        }

        activeRequests.clear()

        myApplication.invokeLater {
            val indicator = ProgressIndicatorBase()
            activeRequests[id] = indicator

            indicator.checkCanceled()
            val document = createDocument(myProject, filePath) ?: return@invokeLater

            val cursorOffset = document.getLineStartOffset(position.line) + position.character
            val editor = myDocumentService.openEditors[filePath] ?: return@invokeLater
            val caret = editor.caretModel.primaryCaret
            caret.moveToOffset(cursorOffset)

            val initContext = CompletionInitializationUtil.createCompletionInitializationContext(
                myProject,
                editor,
                caret,
                2,
                completionType
            )
            val completionIndicator = createIndicator(editor, completionType, initContext) ?: return@invokeLater
            indicator.addStateDelegate(completionIndicator)

            val execFn = { doAutocomplete(id, position, context, filePath, completionIndicator, initContext) }
            val doneFn = { activeRequests.remove(id) }
            val task = object : Task.Backgroundable(null, "Running diagnostics task...", true) {
                override fun run(indicator: ProgressIndicator) {
                    execFn()
                }

                override fun onFinished() {
                    doneFn()
                }

                override fun onCancel() {
                    doneFn()
                }
            }

            progressManager.runProcessWithProgressAsynchronously(task, indicator)
        }
    }

    private fun doAutocomplete(
        id: Int,
        position: Position,
        context: CompletionContext?,
        filePath: String,
        indicator: CompletionProgressIndicator,
        initContext: CompletionInitializationContextImpl,
    ) {
        indicator.checkCanceled()

        myApplication.invokeLater {
            context?.triggerCharacter = null
            context?.triggerKind = CompletionTriggerKindEnum.Invoked

            val document = createDocument(myProject, filePath) ?: return@invokeLater
            val documentHashCode = pushToCache(document.text)

            fetchCompletions(indicator, initContext) { completions, _ ->
                indicator.checkCanceled()
                val response = formatResults(
                    id, completions, filePath, position, context?.triggerCharacter?.get(0) ?: '\u0000', documentHashCode
                )
                myConnection.write(response)
            }
        }
    }

    fun resolveCompletion(
        id: Int, completionType: CompletionType, toResolve: CompletionItem,
    ) {
        val indicator = ProgressIndicatorBase()
        activeRequests[id] = indicator

        myApplication.invokeLater {
            val filePath = toResolve.data.filePath
            val document = createDocument(myProject, filePath) ?: return@invokeLater
            val position = toResolve.data.position

            val cursorOffset = document.getLineStartOffset(position.line) + position.character
            val editor = myDocumentService.openEditors[filePath] ?: return@invokeLater
            val caret = editor.caretModel.primaryCaret
            caret.moveToOffset(cursorOffset)

            val initContext = CompletionInitializationUtil.createCompletionInitializationContext(
                myProject,
                editor,
                caret,
                2,
                completionType
            )
            val completionIndicator = createIndicator(editor, completionType, initContext) ?: return@invokeLater
            indicator.addStateDelegate(completionIndicator)

            val execFn = { doResolveCompletion(id, toResolve, completionIndicator, initContext) }
            val doneFn = { activeRequests.remove(id) }
            val task = object : Task.Backgroundable(null, "Running diagnostics task...", true) {
                override fun run(indicator: ProgressIndicator) {
                    execFn()
                }

                override fun onFinished() {
                    doneFn()
                }

                override fun onCancel() {
                    doneFn()
                }
            }

            progressManager.runProcessWithProgressAsynchronously(task, indicator)
        }
    }

    private fun doResolveCompletion(
        id: Int,
        toResolve: CompletionItem,
        indicator: CompletionProgressIndicator,
        initContext: CompletionInitializationContextImpl,
    ) {
        val filePath = toResolve.data.filePath
        val position = toResolve.data.position
        val triggerCharacter = toResolve.data.triggerCharacter

        myApplication.invokeLater {
            val document = createDocument(myProject, filePath) ?: return@invokeLater

            fetchCompletions(indicator, initContext) { completions, prefixes ->
                val documentHashCode = toResolve.data.documentHashCode

                var initialCompletionDocumentContent = ""
                findDocumentByHashCode(documentHashCode)?.let {
                    myApplication.runWriteAction {
                        initialCompletionDocumentContent = it
                    }
                }

                var initialCompletionDocument = DocumentImpl(initialCompletionDocumentContent) as Document

                val lineStart = initialCompletionDocument.getLineStartOffset(position.line)
                val offset = lineStart + position.character
                val cursorPrefix = initialCompletionDocument.text.substring(lineStart, offset).trim()

                val resultToResolve = completions.find { completion ->
                    val item = formatResult(
                        completion,
                        filePath,
                        position,
                        triggerCharacter,
                        toResolve.data.documentHashCode,
                        cursorPrefix,
                        null
                    )

                    (((item.detail ?: "") == (toResolve.detail ?: "") && (item.labelDetails?.detail
                        ?: "") == (toResolve.labelDetails?.detail ?: "") && (item.labelDetails?.description
                        ?: "") == (toResolve.labelDetails?.description
                        ?: "") && item.insertText == toResolve.insertText && (item.documentation?.value
                        ?: "") == (toResolve.documentation?.value ?: "")))
                } ?: return@fetchCompletions

                val psiFile = PsiDocumentManager.getInstance(myProject).getPsiFile(document) ?: return@fetchCompletions
                val editor = myDocumentService.openEditors[filePath] ?: return@fetchCompletions

                myApplication.executeOnPooledThread {
                    ProgressManager.getInstance().executeProcessUnderProgress(
                        {
                            myApplication.runReadAction {
                                resolveDocumentation(resultToResolve, toResolve)
                            }
                        }, EmptyProgressIndicator()
                    )

                    var cursorOffset = document.getLineStartOffset(position.line) + position.character
                    val prefix = prefixes[resultToResolve]
                    val isPrefix = prefix != null && resultToResolve.lookupString.startsWith(prefix)
                    if (isPrefix) {
                        cursorOffset += resultToResolve.lookupString.length - prefix!!.length
                    } else {
                        initialCompletionDocument = createDocument(myProject, filePath)!!
                    }

                    val caret = editor.caretModel.primaryCaret

                    myApplication.invokeLater {
                        caret.moveToOffset(cursorOffset)

                        resolveEdits(
                            completions,
                            resultToResolve,
                            toResolve,
                            triggerCharacter,
                            editor,
                            initialCompletionDocument.text,
                            psiFile,
                            cursorOffset,
                            initContext
                        )
                        val response = Response(id, toResolve)
                        myConnection.write(messageFactory.newMessage(response))
                    }
                }
            }
        }
    }

    private fun fetchCompletions(
        indicator: CompletionProgressIndicator,
        initContext: CompletionInitializationContextImpl,
        callback: (completions: MutableList<LookupElement>, prefixes: MutableMap<LookupElement, String>) -> Unit,
    ) {
        indicator.checkCanceled()

        myApplication.invokeLater {
            indicator.checkCanceled()

            val parameters = createParameters(initContext, indicator)

            myApplication.executeOnPooledThread {
                indicator.checkCanceled()

                ProgressManager.getInstance().runProcess({
                    indicator.checkCanceled()
                    myApplication.runReadAction {
                        indicator.checkCanceled()
                        val prefixes: MutableMap<LookupElement, String> = HashMap()
                        val arranger = CompletionLookupArrangerImpl(indicator)
                        val consumer: Consumer<CompletionResult> = Consumer { result ->
                            indicator.checkCanceled()
                            prefixes[result.lookupElement] = result.prefixMatcher.prefix
                            arranger.addElement(result)
                        }

                        indicator.checkCanceled()
                        CompletionService.getCompletionService().performCompletion(parameters, consumer)
                        val entries = arranger.arrangeItems().first
                        callback(entries, prefixes)
                    }
                }, indicator)

            }

        }
    }

    private fun findDocumentByHashCode(hashCode: Int): String? {
        for (text in fileCache) {
            if (text.hashCode() == hashCode) {
                return text
            }
        }

        return null
    }

    private fun formatResults(
        responseId: Int,
        completions: MutableList<LookupElement>,
        filePath: String,
        position: Position,
        triggerCharacter: Char,
        documentHashCode: Int,
    ): String {
        val items = ArrayList<CompletionItem>()
        val list = CompletionList(true, items)

        var initialCompletionDocumentContent = ""
        findDocumentByHashCode(documentHashCode)?.let {
            initialCompletionDocumentContent = it
        }

        val document = DocumentImpl(initialCompletionDocumentContent)
        val lineStart = document.getLineStartOffset(position.line)
        val offset = lineStart + position.character
        val cursorPrefix = document.text.substring(lineStart, offset).trim()

        for ((index, completion) in completions.withIndex()) {
            list.pushItem(
                formatResult(
                    completion, filePath, position, triggerCharacter, documentHashCode, cursorPrefix, index
                )
            )
        }

        val response = Response(responseId, list)

        return messageFactory.newMessage(response)
    }

    private fun formatResult(
        completion: LookupElement,
        filePath: String,
        position: Position,
        triggerCharacter: Char,
        documentHashCode: Int,
        cursorPrefix: String,
        sortIndex: Int?,
    ): CompletionItem {
        val presentation = LookupElementPresentation()
        completion.renderElement(presentation)

        val insertText = completion.lookupString
        val label = presentation.itemText ?: completion.lookupString
        var details: CompletionItemLabelDetails? = null
        if (presentation.tailText != null || presentation.typeText != null) {
            details = CompletionItemLabelDetails(
                presentation.tailText, presentation.typeText
            )
        }

        val item = CompletionItem(
            label = label,
            insertText = insertText,
            labelDetails = details,
            filterText = cursorPrefix,
            data = CompletionItemData(filePath, position, triggerCharacter, documentHashCode),
        )

        if (sortIndex != null) {
            item.sortText = sortIndex.toChar().toString()
        }

        return item
    }

    private fun resolveDocumentation(result: LookupElement, completion: CompletionItem) {
        val element = result.psiElement ?: return

        val provider = LanguageDocumentation.INSTANCE.forLanguage(element.language)
        if (provider != null) {
            val documentation = myDocumentationService.fetchDocumentation(element, null)
            if (documentation == "") {
                return
            }

            completion.documentation = MarkupContent(MarkupKindEnum.MARKDOWN, documentation)
        }
    }

    private fun resolveEdits(
        completions: MutableList<LookupElement>,
        resultToResolve: LookupElement,
        toResolve: CompletionItem,
        triggerCharacter: Char,
        editor: Editor,
        initialCompletionDocumentContent: String,
        psiFile: PsiFile,
        cursorOffset: Int,
        initContext: CompletionInitializationContext,
    ) {
        var insertionContext: InsertionContext? = null
        myApplication.runReadAction {
            insertionContext = InsertionContextFactory.buildInsertionContext(
                completions.toMutableList(),
                resultToResolve,
                triggerCharacter,
                editor,
                psiFile,
                cursorOffset,
                initContext.offsetMap.getOffset(IDENTIFIER_END_OFFSET),
                initContext.offsetMap
            )
        }

        ProgressManager.getInstance().executeProcessUnderProgress({
            WriteCommandAction.runWriteCommandAction(myProject) {
                resultToResolve.handleInsert(insertionContext!!)
            }
        }, EmptyProgressIndicator())

        val afterText = editor.document.text
        var afterCursorOffset: Int? = null
        myApplication.runReadAction {
            afterCursorOffset = editor.caretModel.currentCaret.offset
        }

        val editPair = TextEditUtil.computeTextEdits(initialCompletionDocumentContent, afterText)
        val edits = editPair.first.toMutableList()
        val fragments = editPair.second

        for (i in fragments.indices) {
            if (fragments[i].startOffset2 <= afterCursorOffset!! && afterCursorOffset!! <= fragments[i].endOffset2) {
                val primaryEdit = edits[i]

                val cursorCharsFromStart = afterCursorOffset!! - fragments[i].startOffset2
                primaryEdit.newText =
                    primaryEdit.newText.substring(0, cursorCharsFromStart) + "$0" + primaryEdit.newText.substring(
                        cursorCharsFromStart
                    )
                edits.removeAt(i)

                toResolve.textEdit = primaryEdit
                toResolve.insertTextFormat = InsertTextFormatEnum.Snippet
            }
        }

        toResolve.additionalTextEdits = edits.toArray(arrayOf())
    }

    private fun pushToCache(text: String): Int {
        if (fileCache.size > MAX_CACHE_VALUES) {
            fileCache.removeFirst()
        }

        fileCache.add(text)

        return text.hashCode()
    }
}