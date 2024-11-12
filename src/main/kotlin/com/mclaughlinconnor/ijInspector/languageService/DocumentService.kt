package com.mclaughlinconnor.ijInspector.languageService

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl
import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiDocumentManager
import com.mclaughlinconnor.ijInspector.lsp.CreateFilesParams
import com.mclaughlinconnor.ijInspector.lsp.DeleteFilesParams
import com.mclaughlinconnor.ijInspector.lsp.DidChangeTextDocumentParams
import com.mclaughlinconnor.ijInspector.lsp.RenameFilesParams
import com.mclaughlinconnor.ijInspector.utils.Utils
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

@OptIn(FlowPreview::class)
class DocumentService(
    private val myProject: Project
) {
    @Suppress("UnstableApiUsage")
    private lateinit var codeAnalyzer: DaemonCodeAnalyzerImpl
    private val fileEditorManager = FileEditorManager.getInstance(myProject)
    private val localFileSystem = LocalFileSystem.getInstance()
    private val myApplication: Application = ApplicationManager.getApplication()
    private val psiDocumentManager = PsiDocumentManager.getInstance(myProject)
    private val didChangeBuffer = MutableSharedFlow<DidChangeTextDocumentParams>(0, 10, BufferOverflow.DROP_OLDEST)
    private var mostRecentDidChange: DidChangeTextDocumentParams? = null

    init {
        myApplication.runReadAction {
            @Suppress("UnstableApiUsage")
            codeAnalyzer = DaemonCodeAnalyzer.getInstance(myProject) as DaemonCodeAnalyzerImpl
        }

        myApplication.executeOnPooledThread {
            runBlocking {
                launch {
                    didChangeBuffer
                        .debounce(150)
                        .collect { params ->
                            val filePath = params.textDocument.uri.substring("file://".length)
                            doHandleChange(filePath, params, null)
                        }
                }
            }
        }
    }

    private fun doHandleChange(filePath: String, params: DidChangeTextDocumentParams, callback: (() -> Unit)?) {
        val document = Utils.createDocument(myProject, filePath) ?: return

        myApplication.invokeLater {
            val file = PsiDocumentManager.getInstance(myProject).getPsiFile(document) ?: return@invokeLater
            fileEditorManager.openFile(file.virtualFile, true)
            fileEditorManager.setSelectedEditor(file.virtualFile, TextEditorProvider.getInstance().editorTypeId)

            WriteCommandAction.runWriteCommandAction(myProject) {
                document.setText(params.contentChanges[0].text) // only support whole document updates
                psiDocumentManager.commitDocument(document)
            }

            if (callback != null) {
                myApplication.executeOnPooledThread(callback)
            }
        }
    }

    fun handleChange(params: DidChangeTextDocumentParams) {
        mostRecentDidChange = params
        didChangeBuffer.tryEmit(params)
    }

    fun immediatelyReEmitMostRecentDidChange(callback: () -> Unit) {
        if (mostRecentDidChange == null) {
            callback()
            return
        }

        val filePath = mostRecentDidChange!!.textDocument.uri.substring("file://".length)
        doHandleChange(filePath, mostRecentDidChange!!, callback)

        mostRecentDidChange = null
    }

    fun doOpen(filePath: String) {
        val document = Utils.createDocument(myProject, filePath) ?: return
        myApplication.invokeLater {
            val file = PsiDocumentManager.getInstance(myProject).getPsiFile(document) ?: return@invokeLater
            fileEditorManager.openFile(file.virtualFile, true)
            fileEditorManager.setSelectedEditor(file.virtualFile, TextEditorProvider.getInstance().editorTypeId)
        }
    }

    fun didCreateFiles(params: CreateFilesParams) {
        for (file in params.files) {
            localFileSystem.refreshAndFindFileByPath(file.uri.substring("file://".length))
        }
    }

    fun didDeleteFiles(params: DeleteFilesParams) {
        for (file in params.files) {
            localFileSystem.refreshAndFindFileByPath(file.uri.substring("file://".length))
        }
    }

    fun didRenameFiles(params: RenameFilesParams) {
        for (file in params.files) {
            localFileSystem.refreshAndFindFileByPath(file.newUri.substring("file://".length))
            localFileSystem.refreshAndFindFileByPath(file.oldUri.substring("file://".length))
        }
    }
}