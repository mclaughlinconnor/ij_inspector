package com.mclaughlinconnor.ij_inspector.application

import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.psi.PsiReference
import com.intellij.refactoring.suggested.endOffset
import com.intellij.refactoring.suggested.startOffset
import com.mclaughlinconnor.ij_inspector.application.Utils.Companion.createDocument
import com.mclaughlinconnor.ij_inspector.application.lsp.*

class DefinitionService(private val myProject: Project) {
    private val connection: Connection = Connection.getInstance()
    private val messageFactory: MessageFactory = MessageFactory()
    private val myApplication: Application = ApplicationManager.getApplication()

    fun doDefinition(requestId: Int, params: DefinitionParams) {
        val filePath = params.textDocument.uri.substring("file://".length)
        val position = params.position
        val document = createDocument(myProject, filePath) ?: return

        val cursorOffset = document.getLineStartOffset(position.line) + position.character

        myApplication.invokeLater {
            val psiFile = PsiDocumentManager.getInstance(myProject).getPsiFile(document) ?: return@invokeLater

            var element = psiFile.findElementAt(cursorOffset)
            while (element != null && element !is PsiReference) {
                element = element.parent
            }

            if (element !is PsiReference) {
                return@invokeLater
            }

            val resolved = element.resolve() ?: return@invokeLater
            val location = resolvedToLocation(resolved, document)

            val response = Response(requestId, location)

            connection.write(messageFactory.newMessage(response))
        }
    }

    private fun resolvedToLocation(resolved: PsiElement, document: Document): Location {
        val target = getNameIdentifier(resolved)

        val startOffset = target.startOffset
        val endOffset = target.endOffset

        val startLine = document.getLineNumber(startOffset)
        val endLine = document.getLineNumber(endOffset)

        val startColumn = startOffset - document.getLineStartOffset(startLine)
        val endColumn = endOffset - document.getLineStartOffset(endLine)

        val startPosition = Position(startLine, startColumn)
        val endPosition = Position(endLine, endColumn)

        return Location("file://${resolved.containingFile.virtualFile.path}", Range(startPosition, endPosition))
    }

    private fun getNameIdentifier(element: PsiElement): PsiElement {
        if (element is PsiNameIdentifierOwner) {
            return element.nameIdentifier!!
        }

        return element
    }
}