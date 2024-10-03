package com.mclaughlinconnor.ij_inspector.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ApplicationStarter
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.mclaughlinconnor.ij_inspector.application.lsp.CompletionParams
import com.mclaughlinconnor.ij_inspector.application.lsp.Position
import com.mclaughlinconnor.ij_inspector.application.lsp.Request

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
        val completionType = CompletionType.BASIC

        val project = ProjectManager.getInstance().loadAndOpenProject(projectPath) ?: return
        val server = Server(project, completionType)

        myApplication = ApplicationManager.getApplication()
        myApplication.executeOnPooledThread(server)

        DumbService.getInstance(project).runWhenSmart {
            server.setReady()
        }
    }

    inner class Server(project: Project, completionType: CompletionType) : Runnable {
        private var ready: Boolean = false
        private var myProject: Project = project
        private var myCompletionType: CompletionType = completionType
        private lateinit var myConnection: Connection
        private val objectMapper = ObjectMapper()
        private val completionsService = CompletionsService(myProject)

        fun setReady() {
            ready = true
        }

        override fun run() {
            myConnection = Connection.getInstance()
            myConnection.init()

            while (true) {
                val body = myConnection.nextMessage()

                if (!ready) {
                    continue
                }

                val json = objectMapper.readValue(body, Request::class.java)
                if (json.method == "textDocument/completion") {
                    val params: CompletionParams = objectMapper.convertValue(json.params, CompletionParams::class.java)
                    val fileUri = params.textDocument.uri.substring("file://".length)
                    doAutocomplete(json.id, params.position, fileUri, myCompletionType)
                    continue
                }
            }
        }


        private fun doAutocomplete(
            id: Int, position: Position, filePath: String, completionType: CompletionType
        ) {
            completionsService.doAutocomplete(id, position, filePath, completionType)
        }

        // private fun doTextChange(
        //     startOffset: Int, endOffset: Int, filePath: String, replacementText: String
        // ) {
        //     val virtualFile = LocalFileSystem.getInstance().findFileByPath(filePath) ?: return

        //     val document = ReadAction.compute<Document?, RuntimeException> {
        //         FileDocumentManager.getInstance().getDocument(virtualFile, myProject)
        //     }
        //     if (document == null) return

        //     myApplication.invokeLater {
        //         myApplication.runWriteAction {
        //             WriteCommandAction.runWriteCommandAction(myProject) {
        //                 document.replaceString(startOffset, endOffset, replacementText)
        //                 myOutputStream.write("${document.text}\n".toByteArray())
        //             }
        //         }
        //     }
        // }
    }
}