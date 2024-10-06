package com.mclaughlinconnor.ij_inspector.application

import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.project.Project
import com.mclaughlinconnor.ij_inspector.application.lsp.DidChangeTextDocumentParams

class DocumentService(private val myProject: Project) {
    private val myApplication: Application = ApplicationManager.getApplication()

    fun handleChange(filePath: String, params: DidChangeTextDocumentParams) {
        val document = Utils.createDocument(myProject, filePath) ?: return
        myApplication.invokeLater {
            EditorFactory.getInstance().createEditor(document, myProject) ?: return@invokeLater
            WriteCommandAction.runWriteCommandAction(myProject) {
                document.setText(params.contentChanges[0].text) // only support whole document updates
            }
        }
    }

    fun doOpen(filePath: String) {
        val document = Utils.createDocument(myProject, filePath) ?: return
        myApplication.invokeLater {
            EditorFactory.getInstance().createEditor(document, myProject) ?: return@invokeLater
        }
    }
}