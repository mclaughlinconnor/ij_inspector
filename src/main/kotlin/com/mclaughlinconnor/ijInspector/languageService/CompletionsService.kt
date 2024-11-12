package com.mclaughlinconnor.ijInspector.languageService

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.completion.CompletionInitializationContext.IDENTIFIER_END_OFFSET
import com.intellij.codeInsight.completion.impl.CompletionServiceImpl
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.lang.LanguageDocumentation
import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.util.Consumer
import com.intellij.util.containers.toArray
import com.mclaughlinconnor.ijInspector.lsp.*
import com.mclaughlinconnor.ijInspector.lsp.CompletionContext
import com.mclaughlinconnor.ijInspector.rpc.Connection
import com.mclaughlinconnor.ijInspector.utils.TextEditUtil
import com.mclaughlinconnor.ijInspector.utils.Utils.Companion.createDocument
import com.mclaughlinconnor.ijInspector.utils.Utils.Companion.obtainLookup


const val MAX_CACHE_VALUES = 200

@Suppress("UnstableApiUsage")
class CompletionsService(
    private val myProject: Project,
    private val myConnection: Connection,
    private val documentService: DocumentService,
) {
    private val messageFactory: com.mclaughlinconnor.ijInspector.rpc.MessageFactory =
        com.mclaughlinconnor.ijInspector.rpc.MessageFactory()
    private val myApplication: Application = ApplicationManager.getApplication()
    private val myDocumentationService: DocumentationService = DocumentationService(myProject)
    private val fileCache: MutableList<String> = mutableListOf()
    private val editorFactory: EditorFactory = EditorFactory.getInstance()

    private fun createIndicator(
        editor: Editor, completionType: CompletionType, initContext: CompletionInitializationContextImpl,
    ): CompletionProgressIndicator {
        val lookup: LookupImpl = obtainLookup(editor, initContext.project)
        val handler = CodeCompletionHandlerBase.createHandler(completionType, true, false, true)

        val indicator = IndicatorFactory.buildIndicator(
            editor,
            initContext.caret,
            initContext.invocationCount,
            handler,
            initContext.offsetMap,
            initContext.hostOffsets,
            false,
            lookup
        )

        CompletionServiceImpl.setCompletionPhase(CompletionPhase.Synchronous(indicator))

        return indicator
    }

    private fun createParameters(
        initContext: CompletionInitializationContextImpl, indicator: CompletionProgressIndicator,
    ): CompletionParameters {
        val applyPsiChanges = CompletionInitializationUtil.insertDummyIdentifier(initContext, indicator)
        val hostCopyOffsets = applyPsiChanges.get()

        val finalOffsets = CompletionInitializationUtil.toInjectedIfAny(initContext.file, hostCopyOffsets)
        val parameters = CompletionInitializationUtil.createCompletionParameters(initContext, indicator, finalOffsets)
        parameters.setIsTestingMode(false)

        return parameters
    }

    fun doAutocomplete(
        id: Int, position: Position, context: CompletionContext?, filePath: String, completionType: CompletionType,
    ) {
        documentService.immediatelyReEmitMostRecentDidChange {
            context?.triggerCharacter = null
            context?.triggerKind = CompletionTriggerKindEnum.Invoked

            val document = createDocument(myProject, filePath) ?: return@immediatelyReEmitMostRecentDidChange
            val documentHashCode = pushToCache(document.text)

            fetchCompletions(position, filePath, completionType) { completions, _, _, _ ->
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
        documentService.immediatelyReEmitMostRecentDidChange {
            val filePath = toResolve.data.filePath
            val position = toResolve.data.position
            val triggerCharacter = toResolve.data.triggerCharacter
            fetchCompletions(
                position,
                filePath,
                completionType,
            ) { completions, _, document, initContext ->
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
                        completion, filePath, position, triggerCharacter, toResolve.data.documentHashCode, cursorPrefix
                    )

                    (((item.detail ?: "") == (toResolve.detail ?: "") && (item.labelDetails?.detail
                        ?: "") == (toResolve.labelDetails?.detail ?: "") && (item.labelDetails?.description
                        ?: "") == (toResolve.labelDetails?.description
                        ?: "") && item.insertText == toResolve.insertText && (item.documentation?.value
                        ?: "") == (toResolve.documentation?.value ?: "")))
                } ?: return@fetchCompletions

                val psiFile = PsiDocumentManager.getInstance(myProject).getPsiFile(document) ?: return@fetchCompletions
                val editor = EditorFactory.getInstance().createEditor(document, myProject) ?: return@fetchCompletions

                myApplication.executeOnPooledThread {
                    ProgressManager.getInstance().executeProcessUnderProgress(
                        {
                            myApplication.runReadAction {
                                resolveDocumentation(resultToResolve, toResolve)
                            }
                        }, EmptyProgressIndicator()
                    )

                    var cursorOffset = document.getLineStartOffset(position.line) + position.character
                    val isPrefix =
                        resultToResolve.lookupElement.lookupString.startsWith(resultToResolve.prefixMatcher.prefix)
                    if (isPrefix) {
                        cursorOffset += resultToResolve.lookupElement.lookupString.length - resultToResolve.prefixMatcher.prefix.length
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
        position: Position,
        filePath: String,
        completionType: CompletionType,
        callback: (completions: MutableList<CompletionResult>, editor: Editor, document: Document, initContext: CompletionInitializationContext) -> Unit,
    ) {
        val results: MutableList<CompletionResult> = ArrayList()
        val consumer: Consumer<CompletionResult> = Consumer { result -> results.add(result) }

        myApplication.invokeLater {
            val document = createDocument(myProject, filePath) ?: return@invokeLater

            val cursorOffset = document.getLineStartOffset(position.line) + position.character

            val editor = editorFactory.createEditor(document, myProject) ?: return@invokeLater
            val caret = editor.caretModel.primaryCaret
            caret.moveToOffset(cursorOffset)

            val initContext = CompletionInitializationUtil.createCompletionInitializationContext(
                myProject, editor, caret, 1, completionType
            )

            val indicator = createIndicator(editor, completionType, initContext)
            val parameters = createParameters(initContext, indicator)

            myApplication.executeOnPooledThread {
                ProgressManager.getInstance().runProcess({
                    myApplication.runReadAction {
                        CompletionService.getCompletionService().performCompletion(parameters, consumer)
                    }
                }, EmptyProgressIndicator())

                myApplication.invokeLater {
                    val entries = results.subList(0, results.size.coerceAtMost(20))
                    callback(entries, editor, document, initContext)
                }
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
        completions: MutableList<CompletionResult>,
        filePath: String,
        position: Position,
        triggerCharacter: Char,
        documentHashCode: Int,
    ): String {
        val items = ArrayList<CompletionItem>()
        val list = CompletionList(true, items)

        var initialCompletionDocumentContent = ""
        findDocumentByHashCode(documentHashCode)?.let {
            myApplication.runWriteAction {
                initialCompletionDocumentContent = it
            }
        }

        val document = DocumentImpl(initialCompletionDocumentContent)
        val lineStart = document.getLineStartOffset(position.line)
        val offset = lineStart + position.character
        val cursorPrefix = document.text.substring(lineStart, offset).trim()

        for (completion in completions) {
            list.pushItem(
                formatResult(
                    completion, filePath, position, triggerCharacter, documentHashCode, cursorPrefix
                )
            )
        }

        val response = Response(responseId, list)

        return messageFactory.newMessage(response)
    }

    private fun formatResult(
        completion: CompletionResult,
        filePath: String,
        position: Position,
        triggerCharacter: Char,
        documentHashCode: Int,
        cursorPrefix: String,
    ): CompletionItem {
        val presentation = LookupElementPresentation()
        completion.lookupElement.renderElement(presentation)

        val insertText = completion.lookupElement.lookupString
        val label = presentation.itemText ?: completion.lookupElement.lookupString
        var details: CompletionItemLabelDetails? = null
        if (presentation.tailText != null || presentation.typeText != null) {
            details = CompletionItemLabelDetails(
                presentation.tailText, presentation.typeText
            )
        }

        return CompletionItem(
            label = label,
            insertText = insertText,
            labelDetails = details,
            filterText = cursorPrefix,
            data = CompletionItemData(filePath, position, triggerCharacter, documentHashCode),
        )
    }

    private fun resolveDocumentation(result: CompletionResult, completion: CompletionItem) {
        val element = result.lookupElement.psiElement ?: return

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
        completions: MutableList<CompletionResult>,
        resultToResolve: CompletionResult,
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
                completions.map { r -> r.lookupElement }.toMutableList(),
                resultToResolve.lookupElement,
                triggerCharacter,
                editor,
                psiFile,
                cursorOffset,
                initContext.offsetMap.getOffset(IDENTIFIER_END_OFFSET),
                initContext.offsetMap
            )
        }

        WriteCommandAction.runWriteCommandAction(myProject) {
            resultToResolve.lookupElement.handleInsert(insertionContext!!)
        }
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

                val cursorCharsFromStart = afterCursorOffset!! - fragments[i].startOffset1
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


// var inputBySorter = MultiMap.createLinked<CompletionSorterImpl, LookupElement>()
// for (element: LookupElement? in source) {
//     inputBySorter.putValue(obtainSorter(element), element)
// }
// for (sorter: CompletionSorterImpl? in inputBySorter.keySet()) {
//     inputBySorter.put(sorter, sortByPresentation(inputBySorter[sorter]))
// }
