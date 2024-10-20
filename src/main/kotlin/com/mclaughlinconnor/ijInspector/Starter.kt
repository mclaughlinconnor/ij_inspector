package com.mclaughlinconnor.ijInspector

import com.fasterxml.jackson.databind.ObjectMapper
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ApplicationStarter
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.jetbrains.rd.util.ConcurrentHashMap
import com.mclaughlinconnor.ijInspector.languageService.*
import com.mclaughlinconnor.ijInspector.lsp.*
import com.mclaughlinconnor.ijInspector.rpc.Connection
import com.mclaughlinconnor.ijInspector.rpc.ConnectionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.launch
import java.util.*

/**
 * Gets completions at the cursor using the project and filename provided as arguments.
 *
 * The application's command *must* end with `"inspect"` and be less than 20 characters, otherwise, it will not
 * launch headlessly.
 *
 * @see com.intellij.idea.AppMode.isHeadless(java.util.List<java.lang.String>)
 */
@Suppress("UnstableApiUsage")
class Starter : ApplicationStarter {
    private var myApplication: Application = ApplicationManager.getApplication()
    private var myConnectionManager: ConnectionManager = ConnectionManager.getInstance()
    private val scope = CoroutineScope(Dispatchers.Default)
    private val servers: Set<Server> = Collections.newSetFromMap(ConcurrentHashMap())

    override fun main(args: List<String>) {
        val projectPath = args[1]
        val completionType = CompletionType.BASIC

        myConnectionManager.start(2517)

        val project = ProjectManager.getInstance().loadAndOpenProject(projectPath) ?: return

        scope.launch {
            while (myConnectionManager.running) {
                val connection = myConnectionManager.nextConnection() ?: break
                val server = Server(connection, project, completionType)
                myApplication.executeOnPooledThread(server)
            }
        }
    }

    inner class Server(private val myConnection: Connection, project: Project, completionType: CompletionType) :
        Runnable {
        private val completionsService = CompletionsService(project, myConnection)
        private val definitionService = DefinitionService(project, myConnection)
        private val documentService = DocumentService(project, myConnection)
        private val hoverService = HoverService(project, myConnection)
        private val objectMapper = ObjectMapper()
        private val referenceService = ReferenceService(project, myConnection)
        private var myCompletionType: CompletionType = completionType
        private var ready: Boolean = false

        init {
            DumbService.getInstance(project).runWhenSmart {
                ready = true
            }
        }

        override fun run() {
            while (true) {
                val body = myConnection.nextMessage() ?: break

                if (!ready) {
                    continue
                }

                val json = objectMapper.readValue(body, Request::class.java)
                if (json.method == "textDocument/completion") {
                    val params: CompletionParams =
                        objectMapper.convertValue(json.params, CompletionParams::class.java)
                    val fileUri = params.textDocument.uri.substring("file://".length)
                    completionsService.doAutocomplete(
                        json.id,
                        params.position,
                        params.context,
                        fileUri,
                        myCompletionType
                    )
                    continue
                }

                if (json.method == "completionItem/resolve") {
                    val params: CompletionItem = objectMapper.convertValue(json.params, CompletionItem::class.java)
                    completionsService.resolveCompletion(json.id, myCompletionType, params)
                    continue
                }

                if (json.method == "textDocument/didChange") {
                    val params: DidChangeTextDocumentParams =
                        objectMapper.convertValue(json.params, DidChangeTextDocumentParams::class.java)
                    val filePath = params.textDocument.uri.substring("file://".length)
                    documentService.handleChange(filePath, params)
                    continue
                }

                if (json.method == "textDocument/didOpen") {
                    val params: DidOpenTextDocumentParams =
                        objectMapper.convertValue(json.params, DidOpenTextDocumentParams::class.java)
                    val filePath = params.textDocument.uri.substring("file://".length)
                    documentService.doOpen(filePath)
                    continue
                }

                if (json.method == "textDocument/hover") {
                    val params: HoverParams =
                        objectMapper.convertValue(json.params, HoverParams::class.java)
                    hoverService.doHover(json.id, params)
                    continue
                }

                if (json.method == "textDocument/definition") {
                    val params: DefinitionParams =
                        objectMapper.convertValue(json.params, DefinitionParams::class.java)
                    definitionService.doDefinition(json.id, params)
                    continue
                }

                if (json.method == "textDocument/references") {
                    val params: ReferenceParams =
                        objectMapper.convertValue(json.params, ReferenceParams::class.java)
                    referenceService.doReferences(json.id, params)
                    continue
                }
            }

            myConnection.close()
        }
    }
}