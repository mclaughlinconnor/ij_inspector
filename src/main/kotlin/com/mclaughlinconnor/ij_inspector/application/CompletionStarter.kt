package com.mclaughlinconnor.ij_inspector.application

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.completion.impl.CompletionServiceImpl
import com.intellij.codeInsight.lookup.LookupArranger.DefaultArranger
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupFocusDegree
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ApplicationStarter
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.util.Consumer
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket

/**
 * Gets completions at the cursor using the project and filename provided as arguments.
 *
 * The application's command *must* end with `"inspect"` and be less than 20 characters, otherwise, it will not
 * launch headlessly.
 *
 * @see com.intellij.idea.AppMode.isHeadless(java.util.List<java.lang.String>)
 */
@Suppress("UnstableApiUsage")
class CompletionStarter : ApplicationStarter {
    private lateinit var myApplication: Application

    override fun main(args: List<String>) {
        val projectPath = args[1]
        val filePath = args[2]
        val completionType = CompletionType.BASIC

        myApplication = ApplicationManager.getApplication()

        val project = ProjectManager.getInstance().loadAndOpenProject(projectPath) ?: return
        val server = Server(project, filePath, completionType)
        myApplication.executeOnPooledThread(server)

        DumbService.getInstance(project).runWhenSmart {
            server.setReady()
        }
    }

    private fun obtainLookup(editor: Editor, project: Project): LookupImpl {
        val existing = LookupManager.getActiveLookup(editor) as LookupImpl?
        if (existing != null && existing.isCompletion) {
            existing.markReused()
            existing.lookupFocusDegree = LookupFocusDegree.FOCUSED
            return existing
        }

        val lookup = LookupManager.getInstance(project).createLookup(
            editor, LookupElement.EMPTY_ARRAY, "", DefaultArranger()
        ) as LookupImpl
        if (editor.isOneLineMode) {
            lookup.setCancelOnClickOutside(true)
            lookup.setCancelOnOtherWindowOpen(true)
        }
        lookup.lookupFocusDegree = LookupFocusDegree.UNFOCUSED
        return lookup
    }

    inner class Server(project: Project, filePath: String, completionType: CompletionType) : Runnable {
        private var ready: Boolean = false
        private var myProject: Project = project
        private var myFilePath: String = filePath
        private var myCompletionType: CompletionType = completionType
        private lateinit var myInputStream: InputStream
        private lateinit var myOutputStream: OutputStream

        fun setReady() {
            ready = true
        }

        override fun run() {
            val serverSocket = ServerSocket(2517)
            println("Waiting for connection...")
            val socket: Socket = serverSocket.accept()
            myOutputStream = socket.getOutputStream()
            myInputStream = socket.getInputStream()

            myOutputStream.write("Type a cursor offset to get completions at that offset.\n".toByteArray())

            val reader = BufferedReader(InputStreamReader(myInputStream))

            while (true) {
                val input = reader.readLine().trim()
                if (!ready) {
                    myOutputStream.write("Intellij engine is not ready yet.\n".toByteArray())
                    continue
                }

                val cursorOffset = input.toIntOrNull()

                if (cursorOffset != null) {
                    doAutocomplete(cursorOffset, myFilePath, myCompletionType)
                    continue
                }

                val args = input.split("\\t")
                if (args.size != 3) {
                    myOutputStream.write("Three args must be provided\n".toByteArray())
                    continue
                }

                val startOffset = args[0].toIntOrNull()
                val endOffset = args[1].toIntOrNull()
                val replacementText = args[2]

                if (startOffset == null || endOffset == null) {
                    myOutputStream.write("Start and end offsets must not be null\n".toByteArray())
                    continue
                }

                doTextChange(startOffset, endOffset, myFilePath, replacementText)
            }
        }

        private fun doTextChange(
            startOffset: Int, endOffset: Int, filePath: String, replacementText: String
        ) {
            val virtualFile = LocalFileSystem.getInstance().findFileByPath(filePath) ?: return

            val document = ReadAction.compute<Document?, RuntimeException> {
                FileDocumentManager.getInstance().getDocument(virtualFile, myProject)
            }
            if (document == null) return

            myApplication.invokeLater {
                myApplication.runWriteAction {
                    WriteCommandAction.runWriteCommandAction(myProject) {
                        document.replaceString(startOffset, endOffset, replacementText)
                        myOutputStream.write("${document.text}\n".toByteArray())
                    }
                }
            }
        }

        private fun doAutocomplete(
            cursorOffset: Int, filePath: String, completionType: CompletionType
        ) {
            myOutputStream.write("Starting completions...\n".toByteArray())
            val results: MutableList<CompletionResult> = ArrayList()
            val consumer: Consumer<CompletionResult> = Consumer { result -> results.add(result) }

            val virtualFile = LocalFileSystem.getInstance().findFileByPath(filePath) ?: return

            val document = ReadAction.compute<Document?, RuntimeException> {
                FileDocumentManager.getInstance().getDocument(virtualFile, myProject)
            }
            if (document == null) return

            myApplication.invokeLater {
                val editor = EditorFactory.getInstance().createEditor(document, myProject) ?: return@invokeLater
                val caret = editor.caretModel.primaryCaret
                caret.moveToOffset(cursorOffset)

                val initContext = CompletionInitializationUtil.createCompletionInitializationContext(
                    myProject, editor, caret, 1, completionType
                )

                val lookup: LookupImpl = obtainLookup(editor, initContext.project)
                val handler = CodeCompletionHandlerBase.createHandler(completionType, true, false, true)

                val indicator = IndicatorFactory.buildIndicator(
                    editor,
                    initContext.caret,
                    initContext.invocationCount,
                    handler,
                    initContext.offsetMap,
                    initContext.hostOffsets,
                    false,
                    lookup
                )

                CompletionServiceImpl.setCompletionPhase(CompletionPhase.Synchronous(indicator))

                val applyPsiChanges = CompletionInitializationUtil.insertDummyIdentifier(initContext, indicator)
                val hostCopyOffsets = applyPsiChanges.get()

                val finalOffsets = CompletionInitializationUtil.toInjectedIfAny(initContext.file, hostCopyOffsets)
                val parameters =
                    CompletionInitializationUtil.createCompletionParameters(initContext, indicator, finalOffsets)
                parameters.setIsTestingMode(false)

                val completionService = CompletionService.getCompletionService()
                completionService.performCompletion(parameters, consumer)

                for (result in results) {
                    var renderedCompletion: String

                    val prefix = result.prefixMatcher.prefix
                    val text = result.lookupElement.lookupString

                    if (prefix != "" && text.startsWith(prefix)) {
                        val suffix = text.substring(prefix.length)
                        renderedCompletion = "[$prefix]$suffix"
                    } else {
                        renderedCompletion = text
                    }

                    myOutputStream.write(("$renderedCompletion\n").toByteArray())
                }
            }
        }
    }
}