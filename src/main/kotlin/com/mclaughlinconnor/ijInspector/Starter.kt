package com.mclaughlinconnor.ijInspector

import com.fasterxml.jackson.databind.ObjectMapper
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.ide.impl.OpenProjectTask
import com.intellij.ide.impl.ProjectUtil
import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ApplicationStarter
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.mclaughlinconnor.ijInspector.languageService.*
import com.mclaughlinconnor.ijInspector.lsp.*
import com.mclaughlinconnor.ijInspector.rpc.Connection
import com.mclaughlinconnor.ijInspector.rpc.ConnectionManager
import com.mclaughlinconnor.ijInspector.rpc.MessageFactory
import kotlinx.coroutines.*
import java.nio.file.Path

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

    override fun main(args: List<String>) {
        val completionType = CompletionType.BASIC

        myConnectionManager.start(2517)

        scope.launch {
            while (myConnectionManager.running) {
                val connection = myConnectionManager.nextConnection() ?: break
                val server = Server(connection, completionType)
                myApplication.executeOnPooledThread(server)
            }
        }
    }

    inner class Server(private val myConnection: Connection, private val myCompletionType: CompletionType) : Runnable {
        private lateinit var codeActionService: CodeActionService
        private lateinit var commandService: CommandService
        private lateinit var completionsService: CompletionsService
        private lateinit var definitionService: DefinitionService
        private lateinit var diagnosticService: DiagnosticService
        private lateinit var documentService: DocumentService
        private lateinit var hoverService: HoverService
        private val initializeService = InitializeService(myConnection)
        private var messageFactory: MessageFactory = MessageFactory()
        private val objectMapper = ObjectMapper()
        private lateinit var referenceService: ReferenceService
        private var ready: Boolean = false

        private fun initServices(project: Project) {
            DumbService.getInstance(project).runWhenSmart {
                ready = true
                initializeService.finishInitialise()
            }

            codeActionService = CodeActionService(project, myConnection)
            commandService = CommandService(project, myConnection)
            completionsService = CompletionsService(project, myConnection)
            definitionService = DefinitionService(project, myConnection)
            diagnosticService = DiagnosticService(project, myConnection)
            documentService = DocumentService(project)
            hoverService = HoverService(project, myConnection)
            referenceService = ReferenceService(project, myConnection)
        }

        override fun run() {
            while (true) {
                val body = myConnection.nextMessage() ?: break

                val json: Request
                try {
                    json = objectMapper.readValue(body, Request::class.java)
                } catch (e: Exception) {
                    // Added to allow receiving `Response`s back from the client again
                    println(e)
                    continue
                }

                if (json.method == "initialize") {
                    val params: InitializeParams =
                        objectMapper.convertValue(json.params, InitializeParams::class.java)

                    val projectUri = initializeService.startInitialise(json.id, params) ?: continue
                    val openProjectTask = OpenProjectTask {
                        forceOpenInNewFrame = true
                        isNewProject = false
                        preventIprLookup = true
                    }

                    var project: Project?
                    runBlocking {
                        project = ProjectUtil.openOrImportAsync(Path.of(projectUri), openProjectTask)
                    }

                    if (project == null) {
                        return
                    }

                    initServices(project!!)

                    continue
                }

                if (json.method == "shutdown") {
                    myConnection.write(messageFactory.newMessage(Response(json.id)))
                    continue
                }

                if (json.method == "exit") {
                    break
                }

                if (!ready) {
                    continue
                }

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

                if (json.method == "textDocument/codeAction") {
                    val params: CodeActionParams =
                        objectMapper.convertValue(json.params, CodeActionParams::class.java)
                    codeActionService.doCodeActions(json.id, params)
                    continue
                }

                if (json.method == "workspace/executeCommand") {
                    val params: ExecuteCommandParams =
                        objectMapper.convertValue(json.params, ExecuteCommandParams::class.java)
                    commandService.executeCommand(json.id, params)
                    continue
                }
            }

            myConnection.close()
        }
    }
}