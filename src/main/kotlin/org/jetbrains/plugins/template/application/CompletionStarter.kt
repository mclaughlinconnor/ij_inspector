package org.jetbrains.plugins.template.application

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.completion.impl.CompletionServiceImpl
import com.intellij.codeInsight.lookup.LookupArranger.DefaultArranger
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupFocusDegree
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.ide.CliResult
import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ApplicationStarter
import com.intellij.openapi.application.ReadAction
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

    override val isHeadless: Boolean
        get() = true

    override suspend fun processExternalCommandLine(args: List<String>, currentDirectory: String?): CliResult {
        return CliResult(0, "Completion")
    }

    override fun canProcessExternalCommandLine(): Boolean = true

    override fun main(args: List<String>) {
        val projectPath = args[1]
        val filePath = args[2]
        val completionType = CompletionType.BASIC

        myApplication = ApplicationManager.getApplication()

        val project = ProjectManager.getInstance().loadAndOpenProject(projectPath) ?: return
        myApplication.executeOnPooledThread(Server(project, filePath, completionType))

        DumbService.getInstance(project).runWhenSmart {
            println("Ready.")
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
        private var myProject: Project = project
        private var myFilePath: String = filePath
        private var myCompletionType: CompletionType = completionType
        private lateinit var myInputStream: InputStream
        private lateinit var myOutputStream: OutputStream

        override fun run() {
            val serverSocket = ServerSocket(2517)
            println("Waiting for connection...")
            val socket: Socket = serverSocket.accept()
            myOutputStream = socket.getOutputStream()
            myInputStream = socket.getInputStream()

            println("Connected.")
            myOutputStream.write("Type a cursor offset to get completions at that offset.\n".toByteArray())

            val reader = BufferedReader(InputStreamReader(myInputStream))

            while (true) {
                val input = reader.readLine().trim()
                val cursorOffset = input.toIntOrNull()

                if (cursorOffset == null) {
                    myOutputStream.write("Must be a number\n".toByteArray())
                    continue
                }

                doAutocomplete(myProject, cursorOffset, myFilePath, myCompletionType)
            }
        }

        private fun doAutocomplete(
            project: Project, cursorOffset: Int, filePath: String, completionType: CompletionType
        ) {
            myOutputStream.write("Starting completions...\n".toByteArray())
            DumbService.getInstance(project).runWhenSmart {
                val results: MutableList<CompletionResult> = ArrayList()
                val consumer: Consumer<CompletionResult> = Consumer { result -> results.add(result) }

                val virtualFile = LocalFileSystem.getInstance().findFileByPath(filePath) ?: return@runWhenSmart

                val document = ReadAction.compute<Document?, RuntimeException> {
                    FileDocumentManager.getInstance().getDocument(virtualFile, project)
                }
                if (document == null) return@runWhenSmart

                myApplication.invokeLater {
                    val editor = EditorFactory.getInstance().createEditor(document, project) ?: return@invokeLater
                    val caret = editor.caretModel.primaryCaret
                    caret.moveToOffset(cursorOffset)

                    val initContext = CompletionInitializationUtil.createCompletionInitializationContext(
                        project, editor, caret, 1, completionType
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
}