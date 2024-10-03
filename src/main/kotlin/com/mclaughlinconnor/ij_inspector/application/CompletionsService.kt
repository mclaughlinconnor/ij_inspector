package com.mclaughlinconnor.ij_inspector.application

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.completion.impl.CompletionServiceImpl
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.util.Consumer
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
        editor: Editor,
        completionType: CompletionType,
        initContext: CompletionInitializationContextImpl
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
        initContext: CompletionInitializationContextImpl,
        indicator: CompletionProgressIndicator
    ): CompletionParameters {
        val applyPsiChanges = CompletionInitializationUtil.insertDummyIdentifier(initContext, indicator)
        val hostCopyOffsets = applyPsiChanges.get()

        val finalOffsets = CompletionInitializationUtil.toInjectedIfAny(initContext.file, hostCopyOffsets)
        val parameters =
            CompletionInitializationUtil.createCompletionParameters(initContext, indicator, finalOffsets)
        parameters.setIsTestingMode(false)

        return parameters
    }

    private fun createDocument(filePath: String): Document? {
        val virtualFile = LocalFileSystem.getInstance().findFileByPath(filePath) ?: return null

        val document = ReadAction.compute<Document?, RuntimeException> {
            FileDocumentManager.getInstance().getDocument(virtualFile, myProject)
        }

        return document
    }

    fun doAutocomplete(
        id: Int, position: Position, filePath: String, completionType: CompletionType
    ) {
        val results: MutableList<CompletionResult> = ArrayList()
        val consumer: Consumer<CompletionResult> = Consumer { result -> results.add(result) }
        val document = createDocument(filePath) ?: return
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

            val response = formatResults(id, results)
            connection.write(response)
        }
    }

    private fun formatResults(responseId: Int, completions: MutableList<CompletionResult>): String {
        val items = ArrayList<CompletionItem>()
        val list = CompletionList(false, items)
        for (completion in completions) {
            val presentation = LookupElementPresentation()
            completion.lookupElement.renderElement(presentation)

            val details = CompletionItemLabelDetails(presentation.tailText, presentation.typeText)

            list.pushItem(
                CompletionItem(
                    presentation.itemText ?: completion.lookupElement.lookupString,
                    completion.lookupElement.lookupString,
                    completion.lookupElement.lookupString,
                    completion.lookupElement.lookupString,
                    details,
                )
            )
        }

        val response = Response(responseId, list)

        return messageFactory.newMessage(response)
    }
}

// var inputBySorter = MultiMap.createLinked<CompletionSorterImpl, LookupElement>()
// for (element: LookupElement? in source) {
//     inputBySorter.putValue(obtainSorter(element), element)
// }
// for (sorter: CompletionSorterImpl? in inputBySorter.keySet()) {
//     inputBySorter.put(sorter, sortByPresentation(inputBySorter[sorter]))
// }
