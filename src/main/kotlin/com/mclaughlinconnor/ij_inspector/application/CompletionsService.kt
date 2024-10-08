package com.mclaughlinconnor.ij_inspector.application

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.completion.CompletionInitializationContext.IDENTIFIER_END_OFFSET
import com.intellij.codeInsight.completion.impl.CompletionServiceImpl
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.util.Consumer
import com.mclaughlinconnor.ij_inspector.application.Utils.Companion.createDocument
import com.mclaughlinconnor.ij_inspector.application.Utils.Companion.obtainLookup
import com.mclaughlinconnor.ij_inspector.application.lsp.*


@Suppress("UnstableApiUsage")
class CompletionsService(
    private val myProject: Project,
) {
    private val myApplication: Application = ApplicationManager.getApplication()
    private val messageFactory: MessageFactory = MessageFactory()
    private val connection: Connection = Connection.getInstance()

    private fun createIndicator(
        editor: Editor, completionType: CompletionType, initContext: CompletionInitializationContextImpl
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
        initContext: CompletionInitializationContextImpl, indicator: CompletionProgressIndicator
    ): CompletionParameters {
        val applyPsiChanges = CompletionInitializationUtil.insertDummyIdentifier(initContext, indicator)
        val hostCopyOffsets = applyPsiChanges.get()

        val finalOffsets = CompletionInitializationUtil.toInjectedIfAny(initContext.file, hostCopyOffsets)
        val parameters = CompletionInitializationUtil.createCompletionParameters(initContext, indicator, finalOffsets)
        parameters.setIsTestingMode(false)

        return parameters
    }

    fun doAutocomplete(
        id: Int, position: Position, context: CompletionContext?, filePath: String, completionType: CompletionType
    ) {
        fetchCompletions(position, filePath, completionType) { completions, editor, document, initContext ->
            val response =
                formatResults(id, completions, filePath, position, context?.triggerCharacter?.get(0) ?: '\u0000')
            connection.write(response)
        }
    }

    fun resolveCompletion(
        id: Int,
        completionType: CompletionType,
        toResolve: CompletionItem
    ) {
        val filePath = toResolve.data.filePath
        val position = toResolve.data.position
        val triggerCharacter = toResolve.data.triggerCharacter
        fetchCompletions(position, filePath, completionType) { completions, _, document, initContext ->
            val resultToResolve = completions.find { completion ->
                val item = formatResult(completion, filePath, position, triggerCharacter)

                (item.detail == toResolve.detail
                        && item.labelDetails.detail == toResolve.labelDetails.detail
                        && item.labelDetails.description == toResolve.labelDetails.description
                        && item.insertText == toResolve.insertText
                        && item.documentation == toResolve.documentation)
            } ?: return@fetchCompletions

            val psiFile = PsiDocumentManager.getInstance(myProject).getPsiFile(document) ?: return@fetchCompletions
            val editor = EditorFactory.getInstance().createEditor(document, myProject) ?: return@fetchCompletions

            val cursorChange =
                resultToResolve.lookupElement.lookupString.length - resultToResolve.prefixMatcher.prefix.length
            val cursorOffset = document.getLineStartOffset(position.line) + position.character + cursorChange
            val caret = editor.caretModel.primaryCaret
            caret.moveToOffset(cursorOffset)

            val insertionContext = InsertionContextFactory.buildInsertionContext(
                completions.map { r -> r.lookupElement }.toMutableList(),
                resultToResolve.lookupElement,
                triggerCharacter,
                editor,
                psiFile,
                cursorOffset,
                initContext.offsetMap.getOffset(IDENTIFIER_END_OFFSET),
                initContext.offsetMap
            )

            val beforeText = editor.document.text
            WriteCommandAction.runWriteCommandAction(myProject) {
                resultToResolve.lookupElement.handleInsert(insertionContext)
            }
            val afterText = editor.document.text
            val afterCursorOffset = editor.caretModel.currentCaret.offset

            val editPair = TextEditUtil.computeTextEdits(beforeText, afterText)
            val edits = editPair.first
            val fragments = editPair.second

            val alreadyInsertedLength =
                resultToResolve.lookupElement.lookupString.length - resultToResolve.prefixMatcher.prefix.length

            for (i in 0..<fragments.size) {
                if (fragments[i].startOffset2 <= afterCursorOffset && editor.caretModel.currentCaret.offset <= fragments[i].endOffset2) {
                    val primaryEdit = edits[i]

                    val documentAfter = DocumentImpl(afterText)

                    val lineEndOffset = documentAfter.getLineEndOffset(primaryEdit.range.start.line)
                    val suffixLength = lineEndOffset - afterCursorOffset + "\n".length

                    val suffix = primaryEdit.newText.substring(
                        primaryEdit.newText.length - (suffixLength),
                        primaryEdit.newText.length
                    )
                    val suffixRange = Range(
                        Position(
                            primaryEdit.range.start.line,
                            primaryEdit.newText.length - (suffixLength + 1) - alreadyInsertedLength
                        ),
                        primaryEdit.range.end
                    )

                    if (toResolve.additionalTextEdits == null) {
                        toResolve.additionalTextEdits = ArrayList()
                    }
                    toResolve.additionalTextEdits!!.add(TextEdit(suffixRange, suffix))

                    primaryEdit.newText = primaryEdit.newText.substring(0, primaryEdit.newText.length - suffixLength)

                    toResolve.textEdit = primaryEdit
                    edits.removeAt(i)

                    break
                }
            }

            toResolve.additionalTextEdits = edits

            val response = Response(id, toResolve)
            connection.write(messageFactory.newMessage(response))
        }
    }

    private fun fetchCompletions(
        position: Position,
        filePath: String,
        completionType: CompletionType,
        callback: (completions: MutableList<CompletionResult>, editor: Editor, document: Document, initContext: CompletionInitializationContext) -> Unit
    ) {
        val results: MutableList<CompletionResult> = ArrayList()
        val consumer: Consumer<CompletionResult> = Consumer { result -> results.add(result) }
        val document = createDocument(myProject, filePath) ?: return
        val cursorOffset = document.getLineStartOffset(position.line) + position.character

        myApplication.invokeLater {
            val editor = EditorFactory.getInstance().createEditor(document, myProject) ?: return@invokeLater
            val caret = editor.caretModel.primaryCaret
            caret.moveToOffset(cursorOffset)

            val initContext = CompletionInitializationUtil.createCompletionInitializationContext(
                myProject, editor, caret, 1, completionType
            )

            val indicator = createIndicator(editor, completionType, initContext)
            val parameters = createParameters(initContext, indicator)

            CompletionService.getCompletionService().performCompletion(parameters, consumer)

            callback(results, editor, document, initContext)
        }
    }

    private fun formatResults(
        responseId: Int,
        completions: MutableList<CompletionResult>,
        filePath: String,
        position: Position,
        triggerCharacter: Char
    ): String {
        val items = ArrayList<CompletionItem>()
        val list = CompletionList(false, items)
        for (completion in completions) {
            list.pushItem(formatResult(completion, filePath, position, triggerCharacter))
        }

        val response = Response(responseId, list)

        return messageFactory.newMessage(response)
    }

    private fun formatResult(
        completion: CompletionResult,
        filePath: String,
        position: Position,
        triggerCharacter: Char
    ): CompletionItem {
        val presentation = LookupElementPresentation()
        completion.lookupElement.renderElement(presentation)

        val details = CompletionItemLabelDetails(presentation.tailText ?: "", presentation.typeText ?: "")
        val insertText = completion.lookupElement.lookupString

        return CompletionItem(
            presentation.itemText ?: completion.lookupElement.lookupString,
            completion.lookupElement.lookupString,
            completion.lookupElement.lookupString,
            insertText,
            details,
            null,
            null,
            CompletionItemData(filePath, position, triggerCharacter),
        )
    }
}

// var inputBySorter = MultiMap.createLinked<CompletionSorterImpl, LookupElement>()
// for (element: LookupElement? in source) {
//     inputBySorter.putValue(obtainSorter(element), element)
// }
// for (sorter: CompletionSorterImpl? in inputBySorter.keySet()) {
//     inputBySorter.put(sorter, sortByPresentation(inputBySorter[sorter]))
// }
