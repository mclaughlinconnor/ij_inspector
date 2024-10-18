package com.mclaughlinconnor.ij_inspector.application

import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.findDocument
import com.intellij.psi.*
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

            val element = psiFile.findElementAt(cursorOffset)

            val locations: MutableList<Location> = mutableListOf()
            val handleLocation = { location: Location -> locations.add(location) }
            val onComplete = { connection.write(messageFactory.newMessage(Response(requestId, locations))) }

            fetchDefinitions(element, handleLocation, onComplete)
        }
    }

    fun fetchDefinitions(e: PsiElement?, handleLocation: (Location) -> Any?, onComplete: () -> Any?) {
        var element: PsiElement? = e

        while (element != null && element !is PsiReference) {
            element = element.parent
        }

        if (element !is PsiReference) {
            return
        }

        val references = element.references
        for (ref in references) {
            if (ref is PsiPolyVariantReference) {
                val resolved = ref.multiResolve(true)
                for (r in resolved) {
                    if (r.element != null) {
                        handleLocation(resolvedToLocation(r.element!!))
                    }
                }
                continue
            }

            val resolved = ref.resolve()
            if (resolved != null) {
                handleLocation(resolvedToLocation(resolved))
            }
        }

        onComplete()
    }

    private fun resolvedToLocation(resolved: PsiElement): Location {
        val document = resolved.containingFile.virtualFile.findDocument()!!
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