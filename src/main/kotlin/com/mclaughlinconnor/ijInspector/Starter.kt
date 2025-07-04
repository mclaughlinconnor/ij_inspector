package com.mclaughlinconnor.ijInspector

import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.exc.InvalidDefinitionException
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.ide.impl.OpenProjectTask
import com.intellij.ide.impl.ProjectUtil
import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ApplicationStarter
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.mclaughlinconnor.ijInspector.languageService.*
import com.mclaughlinconnor.ijInspector.lsp.*
import com.mclaughlinconnor.ijInspector.rpc.Connection
import com.mclaughlinconnor.ijInspector.rpc.ConnectionManager
import com.mclaughlinconnor.ijInspector.rpc.MessageFactory
import com.mclaughlinconnor.ijInspector.utils.Utils
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
        val completionType = CompletionType.SMART

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
        private lateinit var dumbService: DumbService
        private lateinit var diagnosticService: DiagnosticService
        private lateinit var documentService: DocumentService
        private lateinit var hoverService: HoverService
        private val initializeService = InitializeService(myConnection)
        private lateinit var inlayHintService: InlayHintService
        private var messageFactory: MessageFactory = MessageFactory()
        private val objectMapper = ObjectMapper()
        private lateinit var myProject: Project
        private lateinit var referenceService: ReferenceService
        private lateinit var renameService: RenameService
        private var ready: Boolean = false

        private fun initServices(project: Project) {
            myProject = project

            dumbService = DumbService.getInstance(project)
            dumbService.runWhenSmart {
                ready = true
                println("Indexing complete.")
                initializeService.finishInitialise()
            }

            inlayHintService = InlayHintService(project, myConnection)
            documentService = DocumentService(project, myConnection, inlayHintService)

            definitionService = DefinitionService(project, myConnection)
            hoverService = HoverService(project, myConnection)
            referenceService = ReferenceService(project, myConnection)

            renameService = RenameService(project, myConnection, documentService)
            completionsService = CompletionsService(project, myConnection, documentService)
            codeActionService = CodeActionService(project, myConnection, documentService, inlayHintService)
            commandService = CommandService(project, myConnection, documentService, inlayHintService)
            diagnosticService = DiagnosticService(project, myConnection, documentService, inlayHintService)
        }

        private fun handleBody(body: String) {
            if (tryHandleRequest(body)) {
                return
            }

            if (tryHandleResponse(body)) {
                return
            }

            if (tryHandleNotification(body)) {
                return
            }
        }

        private suspend fun mainLoop() {
            while (true) {
                val body = myConnection.nextMessage() ?: break

                withTimeout(10_000) {
                    launch {
                        handleBody(body)
                    }
                }


                if (this::dumbService.isInitialized && dumbService.isDumb) {
                    println("Currently indexing. Waiting...")
                    dumbService.waitForSmartMode()
                    println("Indexing complete.")
                }
            }
        }

        override fun run() {
            runBlocking {
                mainLoop()
            }
        }

        private fun tryHandleNotification(body: String): Boolean {
            val notification: Notification
            try {
                notification = objectMapper.readValue(body, Notification::class.java)
            } catch (e: JsonMappingException) {
                if (e is InvalidDefinitionException) {
                    throw e
                }

                return false
            }

            handleNotification(notification)

            return true
        }

        private fun tryHandleResponse(body: String): Boolean {
            try {
                objectMapper.readValue(body, Response::class.java)
            } catch (e: JsonMappingException) {
                if (e is InvalidDefinitionException) {
                    throw e
                }

                return false
            }

            handleResponse()

            return true
        }

        private fun tryHandleRequest(body: String): Boolean {
            val request: Request
            try {
                request = objectMapper.readValue(body, Request::class.java)
            } catch (e: JsonMappingException) {
                if (e is InvalidDefinitionException) {
                    throw e
                }

                return false
            }

            if (request.id == Int.MIN_VALUE) {
                return false
            }

            Utils.runCatching(request.id, myConnection) {
                handleRequest(request)
            }

            return true
        }

        private fun handleNotification(notification: Notification) {
            println(notification.method)

            if (notification.method == "$/cancelRequest") {
                val params: CancelParams = objectMapper.convertValue(notification.params, CancelParams::class.java)
                // Only completion service can handle cancellations for now
                completionsService.cancel(params.id)
                return
            }

            if (notification.method == "workspace/didCreateFiles") {
                val params: CreateFilesParams =
                    objectMapper.convertValue(notification.params, CreateFilesParams::class.java)
                documentService.didCreateFiles(params)
                return
            }

            if (notification.method == "workspace/didDeleteFiles") {
                val params: DeleteFilesParams =
                    objectMapper.convertValue(notification.params, DeleteFilesParams::class.java)
                documentService.didDeleteFiles(params)
                return
            }

            if (notification.method == "workspace/didRenameFiles") {
                val params: RenameFilesParams =
                    objectMapper.convertValue(notification.params, RenameFilesParams::class.java)
                documentService.didRenameFiles(params)
                return
            }

            if (notification.method == "textDocument/didChange") {
                val params: DidChangeTextDocumentParams =
                    objectMapper.convertValue(notification.params, DidChangeTextDocumentParams::class.java)
                documentService.handleChange(params, DiagnosticService::publishDiagnostics)
                return
            }

            if (notification.method == "textDocument/didOpen") {
                val params: DidOpenTextDocumentParams =
                    objectMapper.convertValue(notification.params, DidOpenTextDocumentParams::class.java)
                val filePath = params.textDocument.uri.substring("file://".length)
                documentService.doOpen(filePath, diagnosticService::triggerDiagnostics)
                return
            }
        }

        private fun handleResponse() {}

        private fun handleRequest(request: Request) {
            println(request.method)
            if (request.method == "initialize") {
                val params: InitializeParams =
                    objectMapper.convertValue(request.params, InitializeParams::class.java)

                val projectUri = initializeService.startInitialise(request.id, params) ?: return
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

                return
            }

            if (request.method == "shutdown") {
                println("Closing ${myProject.basePath}")
                if (myProject.isInitialized) {
                    diagnosticService.stopListening()
                    myApplication.invokeLaterOnWriteThread {
                        documentService.shutdown()
                        ProjectManager.getInstance().closeAndDispose(myProject)
                    }
                    println("Closed ${myProject.basePath}")
                }
                myConnection.write(messageFactory.newMessage(Response(request.id)))
                return
            }

            if (request.method == "exit") {
                println("Exiting")
                myConnection.close()
            }

            if (!ready) {
                return
            }

            if (request.method == "textDocument/completion") {
                val params: CompletionParams =
                    objectMapper.convertValue(request.params, CompletionParams::class.java)
                val fileUri = params.textDocument.uri.substring("file://".length)
                completionsService.autocomplete(
                    request.id,
                    params.position,
                    params.context,
                    fileUri,
                    myCompletionType
                )
                return
            }

            if (request.method == "completionItem/resolve") {
                val params: CompletionItem = objectMapper.convertValue(request.params, CompletionItem::class.java)
                completionsService.resolveCompletion(request.id, myCompletionType, params)
                return
            }

            if (request.method == "textDocument/hover") {
                val params: HoverParams =
                    objectMapper.convertValue(request.params, HoverParams::class.java)
                hoverService.doHover(request.id, params)
                return
            }

            if (request.method == "textDocument/inlayHint") {
                val params: InlayHintParams =
                    objectMapper.convertValue(request.params, InlayHintParams::class.java)
                inlayHintService.getInlayHints(request.id, params)
                return
            }

            if (request.method == "textDocument/definition") {
                val params: DefinitionParams =
                    objectMapper.convertValue(request.params, DefinitionParams::class.java)
                definitionService.doDefinition(request.id, params)
                return
            }

            if (request.method == "textDocument/references") {
                val params: ReferenceParams =
                    objectMapper.convertValue(request.params, ReferenceParams::class.java)
                referenceService.doReferences(request.id, params)
                return
            }

            if (request.method == "textDocument/codeAction") {
                val params: CodeActionParams =
                    objectMapper.convertValue(request.params, CodeActionParams::class.java)
                codeActionService.doCodeActions(request.id, params)
                return
            }

            if (request.method == "workspace/executeCommand") {
                val argsless: ArgslessExecuteCommandParams =
                    objectMapper.convertValue(request.params, ArgslessExecuteCommandParams::class.java)
                if (!argsless.command.startsWith(CODE_ACTION_COMMAND)) {
                    return
                }

                val params: ExecuteCommandParams =
                    objectMapper.convertValue(request.params, ExecuteCommandParams::class.java)
                commandService.executeCommand(request.id, params)
                return
            }

            if (request.method == "textDocument/rename") {
                val params: RenameParams =
                    objectMapper.convertValue(request.params, RenameParams::class.java)
                renameService.handleRename(request.id, params)
                return
            }
        }
    }
}