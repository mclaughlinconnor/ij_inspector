package com.mclaughlinconnor.ijInspector.languageService

import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.mclaughlinconnor.ijInspector.lsp.*
import com.mclaughlinconnor.ijInspector.rpc.Connection
import com.mclaughlinconnor.ijInspector.rpc.MessageFactory
import com.mclaughlinconnor.ijInspector.utils.Utils.Companion.createDocument

class HoverService(
    private val myProject: Project,
    private val myConnection: Connection
) {
    private val messageFactory: MessageFactory = MessageFactory()
    private val myApplication: Application = ApplicationManager.getApplication()
    private val myDocumentationService = DocumentationService(myProject)

    fun doHover(requestId: Int, params: HoverParams) {
        val filePath = params.textDocument.uri.substring("file://".length)
        val position = params.position
        val document = createDocument(myProject, filePath) ?: return

        val cursorOffset = document.getLineStartOffset(position.line) + position.character

        myApplication.invokeLater {
            val psiFile = PsiDocumentManager.getInstance(myProject).getPsiFile(document) ?: return@invokeLater

            val documentation = myDocumentationService.fetchSourceDocumentationForElement(cursorOffset, psiFile)

            val hover = Hover(MarkupContent(MarkupKindEnum.MARKDOWN, documentation))
            val response = Response(requestId, hover)

            myConnection.write(messageFactory.newMessage(response))
        }
    }
}