package com.mclaughlinconnor.ij_inspector.application

import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.mclaughlinconnor.ij_inspector.application.Utils.Companion.createDocument
import com.mclaughlinconnor.ij_inspector.application.lsp.*

class HoverService(private val myProject: Project) {
    private val connection: Connection = Connection.getInstance()
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
            val editor = EditorFactory.getInstance().createEditor(document, myProject) ?: return@invokeLater

            val documentation = myDocumentationService.fetchSourceDocumentationForElement(editor, cursorOffset, psiFile)

            val hover = Hover(MarkupContent(MarkupKindEnum.MARKDOWN, documentation))
            val response = Response(requestId, hover)

            connection.write(messageFactory.newMessage(response))
        }
    }
}