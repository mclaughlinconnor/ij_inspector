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

                if (tryHandleRequest(body)) {
                    continue
                }

                if (tryHandleResponse(body)) {
                    continue
                }

                if (tryHandleNotification(body)) {
                    continue
                }
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

            handleRequest(request)

            return true
        }

        private fun handleNotification(notification: Notification) {
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
                val filePath = params.textDocument.uri.substring("file://".length)
                documentService.handleChange(filePath, params)
                return
            }

            if (notification.method == "textDocument/didOpen") {
                val params: DidOpenTextDocumentParams =
                    objectMapper.convertValue(notification.params, DidOpenTextDocumentParams::class.java)
                val filePath = params.textDocument.uri.substring("file://".length)
                documentService.doOpen(filePath)
                return
            }
        }

        private fun handleResponse() {}

        private fun handleRequest(request: Request) {
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
                myConnection.write(messageFactory.newMessage(Response(request.id)))
                return
            }

            if (request.method == "exit") {
                myConnection.close()
            }

            if (!ready) {
                return
            }

            if (request.method == "textDocument/completion") {
                val params: CompletionParams =
                    objectMapper.convertValue(request.params, CompletionParams::class.java)
                val fileUri = params.textDocument.uri.substring("file://".length)
                completionsService.doAutocomplete(
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
                val params: ExecuteCommandParams =
                    objectMapper.convertValue(request.params, ExecuteCommandParams::class.java)
                commandService.executeCommand(request.id, params)
                return
            }
        }
    }
}