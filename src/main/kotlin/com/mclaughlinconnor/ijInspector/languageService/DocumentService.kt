package com.mclaughlinconnor.ijInspector.languageService

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl
import com.intellij.codeInsight.daemon.impl.EditorTracker
import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
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

const val OPEN_FILES_LIMIT = 10

@OptIn(FlowPreview::class)
class DocumentService(
    private val myProject: Project,
    private val diagnosticService: DiagnosticService,
) {
    @Suppress("UnstableApiUsage")
    private lateinit var codeAnalyzer: DaemonCodeAnalyzerImpl
    private val editorFactory = EditorFactory.getInstance()

    @Suppress("UnstableApiUsage")
    private val editorTracker = EditorTracker.Companion.getInstance(myProject)
    private val fileEditorManager = FileEditorManager.getInstance(myProject)
    private val localFileSystem = LocalFileSystem.getInstance()
    private val myApplication: Application = ApplicationManager.getApplication()
    private val psiDocumentManager = PsiDocumentManager.getInstance(myProject)
    private val didChangeBuffer = MutableSharedFlow<DidChangeTextDocumentParams>(0, 20, BufferOverflow.DROP_OLDEST)
    private var mostRecentDidChange: DidChangeTextDocumentParams? = null
    private val openFiles: MutableList<PsiFile> = mutableListOf()
    private var openEditors: MutableList<Editor> = mutableListOf()

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
                            doHandleChange(filePath, params)
                        }
                }
            }
        }
    }

    private fun doHandleChange(
        filePath: String,
        params: DidChangeTextDocumentParams,
        triggerDiagnostics: Boolean = true,
        callback: (() -> Unit)? = null,
    ) {
        val document = Utils.createDocument(myProject, filePath) ?: return

        myApplication.invokeLater {
            val file = PsiDocumentManager.getInstance(myProject).getPsiFile(document) ?: return@invokeLater
            fileEditorManager.openFile(file.virtualFile, true)

            WriteCommandAction.runWriteCommandAction(myProject) {
                document.setText(params.contentChanges[0].text) // only support whole document updates
                psiDocumentManager.commitDocument(document)
            }

            if (callback != null) {
                callback()
            }

            if (triggerDiagnostics) {
                diagnosticService.triggerDiagnostics(openFiles)
            }
        }
    }

    fun handleChange(params: DidChangeTextDocumentParams) {
        mostRecentDidChange = params
        didChangeBuffer.tryEmit(params)
    }

    fun immediatelyReEmitMostRecentDidChange(callback: () -> Unit) {
        if (mostRecentDidChange == null) {
            myApplication.invokeLater(callback)
            return
        }

        val filePath = mostRecentDidChange!!.textDocument.uri.substring("file://".length)
        doHandleChange(filePath, mostRecentDidChange!!, false, callback)

        mostRecentDidChange = null
    }

    fun doOpen(filePath: String) {
        val document = Utils.createDocument(myProject, filePath) ?: return
        myApplication.invokeLater {
            val file = PsiDocumentManager.getInstance(myProject).getPsiFile(document) ?: return@invokeLater

            if (!openFiles.contains(file)) {
                if (openFiles.size >= OPEN_FILES_LIMIT) {
                    openFiles.removeFirst()
                }

                openFiles.add(file)
            }

            openEditors = (openFiles.map { editorFactory.createEditor(it.fileDocument, myProject) }).toMutableList()
            @Suppress("UnstableApiUsage")
            editorTracker.activeEditors = openEditors

            fileEditorManager.openFile(file.virtualFile, true)
            fileEditorManager.setSelectedEditor(file.virtualFile, TextEditorProvider.getInstance().editorTypeId)

            diagnosticService.triggerDiagnostics(openFiles, 10 * 1000)
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