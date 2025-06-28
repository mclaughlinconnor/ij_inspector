package com.mclaughlinconnor.ijInspector.languageService

import com.intellij.codeInsight.daemon.impl.EditorTracker
import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.util.application
import com.mclaughlinconnor.ijInspector.lsp.*
import com.mclaughlinconnor.ijInspector.rpc.Connection
import com.mclaughlinconnor.ijInspector.utils.Utils
import com.mclaughlinconnor.ijInspector.utils.lspRangeToOffsets
import com.mclaughlinconnor.ijInspector.utils.offsetsToLspRange

class DocumentService(
    private val myProject: Project,
    private val myConnection: Connection,
    private val inlayHintService: InlayHintService
) {
    private val editorFactory = EditorFactory.getInstance()

    @Suppress("UnstableApiUsage")
    private val editorTracker = EditorTracker.Companion.getInstance(myProject)
    private val fileEditorManager = FileEditorManager.getInstance(myProject)
    private val localFileSystem = LocalFileSystem.getInstance()
    private val myApplication: Application = ApplicationManager.getApplication()
    private val psiDocumentManager = PsiDocumentManager.getInstance(myProject)
    private val openFiles: MutableList<PsiFile> = mutableListOf()
    private val openFilesDiagnostics: MutableMap<PsiFile, List<Diagnostic>> = HashMap()
    private val openFilesRangeMarkers: MutableMap<PsiFile, List<RangeMarker>> = HashMap()
    var openEditors: HashMap<String, Editor> = HashMap()

    fun shutdown() {
        for (openEditor in openEditors) {
            editorFactory.releaseEditor(openEditor.value)
        }
    }

    private fun doHandleChange(
        filePath: String,
        params: DidChangeTextDocumentParams,
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
        }
    }

    fun updateDiagnostics(psiFile: PsiFile, diagnostics: List<Diagnostic>) {
        if (openFiles.contains(psiFile)) {
            openFilesDiagnostics[psiFile] = diagnostics

            val document = psiFile.fileDocument
            val markers = mutableListOf<RangeMarker>()
            for (diagnostic in diagnostics) {
                val offsets = lspRangeToOffsets(document, diagnostic.range)
                markers.add(document.createRangeMarker(offsets.first, offsets.second))
            }

            openFilesRangeMarkers[psiFile] = markers
        }
    }

    private fun shiftDiagnostic(psiFile: PsiFile) {
        val diagnostics = openFilesDiagnostics[psiFile] ?: return
        val rangeMarkers = openFilesRangeMarkers[psiFile] ?: return
        val document = psiFile.fileDocument

        for (i in diagnostics.indices) {
            diagnostics[i].range = offsetsToLspRange(document, rangeMarkers[i].textRange)
        }

        openFilesDiagnostics[psiFile] = diagnostics
    }

    fun handleChange(
        params: DidChangeTextDocumentParams,
        publishDiagnostics: ((DocumentService, Connection, PsiFile, List<Diagnostic>) -> Unit),
    ) {
        val filePath = params.textDocument.uri.substring("file://".length)
        val virtualFile = LocalFileSystem.getInstance().findFileByPath(filePath) ?: return

        application.invokeLater {
            application.runReadAction {
                val file = PsiManager.getInstance(myProject).findFile(virtualFile) ?: return@runReadAction

                doHandleChange(filePath, params) {
                    shiftDiagnostic(file)
                    publishDiagnostics(this, myConnection, file, openFilesDiagnostics[file] ?: listOf())
                    inlayHintService.instructRefreshInlayHints()
                }
            }
        }
    }

    fun doOpen(filePath: String, triggerDiagnostics: ((openFiles: List<PsiFile>) -> Unit)) {
        val document = Utils.createDocument(myProject, filePath) ?: return
        myApplication.invokeLater {
            val file = PsiDocumentManager.getInstance(myProject).getPsiFile(document) ?: return@invokeLater

            if (!openEditors.contains(filePath)) {
                val editor = editorFactory.createEditor(document, myProject)
                openEditors[filePath] = editor

                @Suppress("UnstableApiUsage")
                val activeEditors: MutableList<Editor> = editorTracker.activeEditors.toMutableList()
                activeEditors.add(editor)

                @Suppress("UnstableApiUsage")
                editorTracker.activeEditors = activeEditors

                inlayHintService.registerEditorForListening(editor)
            }


            fileEditorManager.openFile(file.virtualFile, true)
            fileEditorManager.setSelectedEditor(file.virtualFile, TextEditorProvider.getInstance().editorTypeId)

            triggerDiagnostics(listOf(file))
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