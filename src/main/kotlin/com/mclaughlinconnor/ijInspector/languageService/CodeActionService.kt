package com.mclaughlinconnor.ijInspector.languageService

import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.impl.HighlightInfo.IntentionActionDescriptor
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.util.application
import com.mclaughlinconnor.ijInspector.lsp.*
import com.mclaughlinconnor.ijInspector.rpc.Connection
import com.mclaughlinconnor.ijInspector.rpc.MessageFactory
import com.mclaughlinconnor.ijInspector.utils.Utils

const val CODE_ACTION_COMMAND = "codeAction"

class CodeActionService(private val myProject: Project, private val myConnection: Connection) {
    private var myApplication: Application = ApplicationManager.getApplication()
    private val messageFactory: MessageFactory = MessageFactory()
    private val commandService: CommandService = CommandService(myProject, myConnection)

    fun doCodeActions(requestId: Int, params: CodeActionParams) {
        val document = Utils.createDocument(myProject, params.textDocument.uri.substring("file://".length)) ?: return

        val startOffset = document.getLineStartOffset(params.range.start.line)
        val endOffset = document.getLineEndOffset(params.range.end.line)

        var highlights: List<HighlightInfo> = listOf()
        application.runReadAction {
            @Suppress("UnstableApiUsage")
            highlights = DaemonCodeAnalyzerImpl.getHighlights(
                document,
                HighlightSeverity.GENERIC_SERVER_ERROR_OR_WARNING,
                myProject
            )
        }

        myApplication.invokeLater {
            val editor = EditorFactory.getInstance().createEditor(document, myProject) ?: return@invokeLater
            val psiFile = PsiDocumentManager.getInstance(myProject).getPsiFile(document) ?: return@invokeLater

            val quickFixes: MutableList<IntentionAction> = mutableListOf()

            for (highlight in highlights) {
                if (!(startOffset <= highlight.endOffset && endOffset >= highlight.startOffset)) {
                    continue
                }

                highlight.findRegisteredQuickFix<Any> { descriptor: IntentionActionDescriptor, _: TextRange? ->
                    if (!descriptor.action.isAvailable(myProject, editor, psiFile)) {
                        return@findRegisteredQuickFix
                    }

                    quickFixes.add(descriptor.action)
                    return@findRegisteredQuickFix
                }
            }

            val codeActions: MutableList<CodeAction> = mutableListOf()
            for (quickFix in quickFixes) {
                val action = constructCodeAction(quickFix, psiFile.virtualFile.path)
                codeActions.add(action)
                commandService.addCommand(quickFix)
            }

            val response = Response(requestId, codeActions)
            myConnection.write(messageFactory.newMessage(response))
        }
    }

    private fun constructCodeAction(quickFix: IntentionAction, path: String): CodeAction {
        return CodeAction(
            title = quickFix.text,
            kind = CodeActionKindEnum.QuickFix,
            diagnostics = listOf(),
            isPreferred = true,
            disabled = null,
            edit = null,
            command = Command(
                title = quickFix.text,
                command = CODE_ACTION_COMMAND,
                arguments = listOf(quickFix.hashCode().toString(), path),
            ),
            data = null,
        )
    }
}