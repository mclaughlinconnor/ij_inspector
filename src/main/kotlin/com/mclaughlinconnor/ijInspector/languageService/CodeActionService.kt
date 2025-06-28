package com.mclaughlinconnor.ijInspector.languageService

import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.impl.HighlightInfo.IntentionActionDescriptor
import com.intellij.codeInsight.intention.EmptyIntentionAction
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.IntentionActionDelegate
import com.intellij.codeInsight.intention.impl.ShowIntentionActionsHandler
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.profile.codeInspection.InspectionProjectProfileManager
import com.intellij.psi.PsiDocumentManager
import com.intellij.util.application
import com.mclaughlinconnor.ijInspector.lsp.*
import com.mclaughlinconnor.ijInspector.rpc.Connection
import com.mclaughlinconnor.ijInspector.rpc.MessageFactory
import com.mclaughlinconnor.ijInspector.utils.Utils
import com.mclaughlinconnor.ijInspector.utils.lspPositionToOffset

const val CODE_ACTION_COMMAND = "ij_inspector/codeAction"

class CodeActionService(
    private val myProject: Project,
    private val myConnection: Connection,
    private val documentService: DocumentService,
    inlayHintService: InlayHintService
) {
    private var myApplication: Application = ApplicationManager.getApplication()
    private val messageFactory: MessageFactory = MessageFactory()
    private val commandService: CommandService =
        CommandService(myProject, myConnection, documentService, inlayHintService)
    private val inspectionProfile = InspectionProjectProfileManager.getInstance(myProject).currentProfile

    fun doCodeActions(requestId: Int, params: CodeActionParams) {
        val filePath = params.textDocument.uri.substring("file://".length)
        val document = Utils.createDocument(myProject, filePath) ?: return

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
            val psiFile = PsiDocumentManager.getInstance(myProject).getPsiFile(document) ?: return@invokeLater
            val editor = documentService.openEditors[filePath] ?: return@invokeLater

            val startOffset = lspPositionToOffset(document, params.range.start)
            val endOffset = lspPositionToOffset(document, params.range.end)

            val startOffsetLineStart = document.getLineStartOffset(params.range.start.line)
            val endOffsetLineEnd = document.getLineEndOffset(params.range.end.line)

            val caret = editor.caretModel.primaryCaret
            caret.moveToOffset(startOffset)
            if (startOffset != endOffset) {
                caret.setSelection(startOffset, endOffset)
            }

            val codeActions: MutableList<CodeAction> = mutableListOf()

            for (highlight in highlights) {
                if (!(startOffsetLineStart <= highlight.endOffset && endOffsetLineEnd >= highlight.startOffset)) {
                    continue
                }

                highlight.findRegisteredQuickFix<Any> { descriptor: IntentionActionDescriptor, _: TextRange? ->
                    if (!descriptor.action.isAvailable(myProject, editor, psiFile)) {
                        return@findRegisteredQuickFix
                    }

                    val quickFix = descriptor.action

                    if (quickFix is EmptyIntentionAction) {
                        return@findRegisteredQuickFix
                    }

                    myApplication.runWriteAction {
                        quickFix.generatePreview(myProject, editor, psiFile)
                    }
                    val diagnostic = DiagnosticService.constructDiagnostic(inspectionProfile, highlight, document)
                    val action = constructCodeAction(quickFix, psiFile.virtualFile.path, diagnostic)
                    codeActions.add(action)
                    commandService.addCommand(quickFix, diagnostic, startOffset, endOffset)

                    return@findRegisteredQuickFix
                }
            }

            val cachedIntentions = ShowIntentionActionsHandler.calcCachedIntentions(myProject, editor, psiFile)

            for (fixes in listOf(
                cachedIntentions.inspectionFixes,
                cachedIntentions.errorFixes,
                cachedIntentions.intentions
            )) {
                for (descriptor in fixes) {
                    val quickFix = descriptor.action

                    if (
                        quickFix is EmptyIntentionAction
                        || (quickFix is IntentionActionDelegate && quickFix.delegate is EmptyIntentionAction)
                    ) {
                        continue
                    }

                    val action = constructCodeAction(quickFix, psiFile.virtualFile.path)
                    codeActions.add(action)
                    commandService.addCommand(quickFix, null, startOffset, endOffset)
                }
            }

            val response = Response(requestId, codeActions)
            myConnection.write(messageFactory.newMessage(response))
        }
    }

    private fun constructCodeAction(
        quickFix: IntentionAction,
        path: String,
        diagnostic: Diagnostic? = null,
    ): CodeAction {
        return CodeAction(
            title = quickFix.text,
            kind = CodeActionKindEnum.QuickFix,
            diagnostics = if (diagnostic != null) listOf(diagnostic) else null,
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