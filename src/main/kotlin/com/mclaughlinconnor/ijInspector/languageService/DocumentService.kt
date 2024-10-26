package com.mclaughlinconnor.ijInspector.languageService

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl
import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.mclaughlinconnor.ijInspector.lsp.DidChangeTextDocumentParams
import com.mclaughlinconnor.ijInspector.utils.Utils

class DocumentService(
    private val myProject: Project
) {
    private lateinit var codeAnalyzer: DaemonCodeAnalyzerImpl
    private val fileEditorManager = FileEditorManager.getInstance(myProject)
    private val myApplication: Application = ApplicationManager.getApplication()
    private val psiDocumentManager = PsiDocumentManager.getInstance(myProject)

    init {
        myApplication.runReadAction {
            codeAnalyzer = DaemonCodeAnalyzer.getInstance(myProject) as DaemonCodeAnalyzerImpl
        }
    }

    fun handleChange(filePath: String, params: DidChangeTextDocumentParams) {
        val document = Utils.createDocument(myProject, filePath) ?: return

        myApplication.invokeLater {
            val file = PsiDocumentManager.getInstance(myProject).getPsiFile(document) ?: return@invokeLater
            fileEditorManager.openFile(file.virtualFile, true)
            fileEditorManager.setSelectedEditor(file.virtualFile, TextEditorProvider.getInstance().editorTypeId)

            WriteCommandAction.runWriteCommandAction(myProject) {
                document.setText(params.contentChanges[0].text) // only support whole document updates
                psiDocumentManager.commitDocument(document)
            }
        }
    }

    fun doOpen(filePath: String) {
        val document = Utils.createDocument(myProject, filePath) ?: return
        myApplication.invokeLater {
            val file = PsiDocumentManager.getInstance(myProject).getPsiFile(document) ?: return@invokeLater
            fileEditorManager.openFile(file.virtualFile, true)
            fileEditorManager.setSelectedEditor(file.virtualFile, TextEditorProvider.getInstance().editorTypeId)
        }
    }
}