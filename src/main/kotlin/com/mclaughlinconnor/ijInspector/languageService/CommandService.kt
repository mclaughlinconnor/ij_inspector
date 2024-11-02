package com.mclaughlinconnor.ijInspector.languageService

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.impl.ShowIntentionActionsHandler
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.findDocument
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.*
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.util.application
import com.intellij.util.containers.toArray
import com.mclaughlinconnor.ijInspector.lsp.*
import com.mclaughlinconnor.ijInspector.rpc.Connection
import com.mclaughlinconnor.ijInspector.rpc.MessageFactory
import com.mclaughlinconnor.ijInspector.utils.RequestId
import com.mclaughlinconnor.ijInspector.utils.TextEditUtil
import com.mclaughlinconnor.ijInspector.utils.Utils

const val MAX_COMMANDS = 200

class CommandService(private val myProject: Project, private val connection: Connection) {
    private val documentChanges: MutableList<AbstractTextDocumentEdit> = mutableListOf()
    private val editorFactory: EditorFactory = EditorFactory.getInstance()
    private val messageFactory: MessageFactory = MessageFactory()
    private val psiDocumentManager = PsiDocumentManager.getInstance(myProject)
    private var isListening = true

    fun executeCommand(requestId: Int, params: ExecuteCommandParams) {
        val stringHashCode = params.arguments?.getOrNull(0) ?: return writeEmptyResponse(requestId)
        val hashCode: Int
        try {
            hashCode = stringHashCode.toInt()
        } catch (nfe: NumberFormatException) {
            writeEmptyResponse(requestId)
            return
        }

        val command = findCommand(hashCode) ?: return writeEmptyResponse(requestId)

        val path = params.arguments.getOrNull(1) ?: return writeEmptyResponse(requestId)
        val document = Utils.createDocument(myProject, path) ?: return writeEmptyResponse(requestId)

        application.invokeLater {
            val editor =
                EditorFactory.getInstance().createEditor(document, myProject) ?: return@invokeLater writeEmptyResponse(
                    requestId
                )
            val psiFile = psiDocumentManager.getPsiFile(document) ?: return@invokeLater writeEmptyResponse(requestId)

            invokeAction(command, editor, psiFile)

            writeEmptyResponse(requestId)
        }
    }

    fun addCommand(command: IntentionAction) {
        if (commands.size >= MAX_COMMANDS) {
            commands.removeFirst()
        }

        commands.add(command)
    }

    private fun invokeAction(command: IntentionAction, editor: Editor, psiFile: PsiFile) {
        val messageBus = myProject.messageBus.connect()
        documentChanges.clear()

        val documentListener = MyDocumentListener()
        val disposable = Disposer.newDisposable()
        editorFactory.eventMulticaster.addDocumentListener(documentListener, disposable)
        messageBus.subscribe(VirtualFileManager.VFS_CHANGES, MyVfsListener())


        // TODO: this can be _really_ slow, so add some progress messages.
        @Suppress("DialogTitleCapitalization")
        ShowIntentionActionsHandler.chooseActionAndInvoke(psiFile, editor, command, command.text)

        editorFactory.eventMulticaster.removeDocumentListener(documentListener)
        disposable.dispose()
        messageBus.disconnect()

        val workspaceEdit = WorkspaceEdit(documentChanges = documentChanges.toArray(arrayOf()))
        val request =
            Request(RequestId.getNextRequestId(), ApplyWorkspaceEditParams(null, workspaceEdit), "workspace/applyEdit")
        val message = messageFactory.newMessage(request)
        connection.write(message)
    }

    private fun findCommand(hashCode: Int): IntentionAction? {
        for (command in commands) {
            if (command.hashCode() == hashCode) {
                return command
            }
        }

        return null
    }

    private fun writeEmptyResponse(requestId: Int) {
        val response = Response(requestId, null)
        val message = messageFactory.newMessage(response)

        connection.write(message)
    }

    inner class MyVfsListener : BulkFileListener {
        private val preChangeText: MutableMap<String, String> = HashMap()
        private val fileIndex = ProjectRootManager.getInstance(myProject).fileIndex

        override fun after(events: List<VFileEvent>) {
            for (event in events) {
                if (event.file?.let { fileIndex.isInProject(it) } != true) {
                    continue
                }

                if (event is VFileContentChangeEvent) {
                    val file = event.file
                    val document = file.findDocument() ?: continue

                    val preText = preChangeText[event.file.path] ?: continue
                    val postText = document.text

                    val results = TextEditUtil.computeTextEdits(preText, postText)
                    val edits = results.first

                    documentChanges.add(
                        TextDocumentEdit(
                            TextDocumentIdentifier("file://${file.path}"),
                            edits.toArray(arrayOf())
                        )
                    )

                    continue
                }

                if (event is VFileCopyEvent) {
                    val document = event.file.findDocument() ?: continue
                    documentChanges.add(CreateFile("file://${event.path}", null))
                    documentChanges.add(
                        TextDocumentEdit(
                            TextDocumentIdentifier("file://${event.path}"), arrayOf(
                                TextEdit(
                                    Range(), document.text
                                )
                            )
                        )
                    )
                    continue
                }

                if (event is VFileCreateEvent) {
                    documentChanges.add(CreateFile("file://${event.path}", null))
                }

                if (event is VFileDeleteEvent) {
                    documentChanges.add(DeleteFile("file://${event.path}", null))
                }

                if (event is VFileMoveEvent) {
                    documentChanges.add(RenameFile("file://${event.oldPath}", "file://${event.newPath}"))
                    continue
                }
            }
        }

        override fun before(events: MutableList<out VFileEvent>) {
            if (!isListening) {
                return
            }

            for (event in events) {
                if (event.file?.let { fileIndex.isInProject(it) } != true) {
                    continue
                }

                if (event is VFileContentChangeEvent) {
                    val document = event.file.findDocument() ?: continue
                    preChangeText[event.file.path] = document.text
                    continue
                }
            }
        }
    }

    inner class MyDocumentListener : DocumentListener {
        private val preChangeText: MutableMap<String, String> = HashMap()

        override fun beforeDocumentChange(event: DocumentEvent) {
            val document = event.document
            val file = FileDocumentManager.getInstance().getFile(event.document) ?: return
            preChangeText[file.path] = document.text
        }

        override fun documentChanged(event: DocumentEvent) {
            val document = event.document
            val file = FileDocumentManager.getInstance().getFile(event.document) ?: return

            val preText = preChangeText[file.path] ?: return
            val postText = document.text

            val results = TextEditUtil.computeTextEdits(preText, postText)
            val edits = results.first

            documentChanges.add(
                TextDocumentEdit(
                    TextDocumentIdentifier("file://${file.path}"),
                    edits.toArray(arrayOf())
                )
            )
        }
    }


    companion object {
        private val commands: MutableList<IntentionAction> = mutableListOf()
    }
}