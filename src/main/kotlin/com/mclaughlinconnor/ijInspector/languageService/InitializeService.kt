package com.mclaughlinconnor.ijInspector.languageService

import com.mclaughlinconnor.ijInspector.lsp.*
import com.mclaughlinconnor.ijInspector.rpc.Connection
import com.mclaughlinconnor.ijInspector.rpc.MessageFactory

class InitializeService(
    private val myConnection: Connection
) {
    private val messageFactory: MessageFactory = MessageFactory()
    private var pendingResponse: Response? = null

    fun startInitialise(requestId: Int, params: InitializeParams): String? {
        val serverCapabilities = ServerCapabilities(
            completionProvider = CompletionOptions(
                resolveProvider = true,
                completionItem = CompletionItemOptions(labelDetailsSupport = true)
            ),
            hoverProvider = true,
            definitionProvider = true,
            referencesProvider = true,
            diagnosticProvider = DiagnosticOptions(
                interFileDependencies = false,
                workspaceDiagnostics = false
            ),
            codeActionProvider = true,
            executeCommandProvider = ExecuteCommandOptions(arrayOf(CODE_ACTION_COMMAND))
        )
        val result = InitializeResult(serverCapabilities)
        pendingResponse = Response(requestId, result)

        if (params.rootUri != null) {
            if (params.rootUri.startsWith("file://")) {
                return params.rootUri.substring("file://".length)
            }

            return params.rootUri
        }

        return params.rootPath
    }

    fun finishInitialise() {
        if (pendingResponse != null) {
            myConnection.write(messageFactory.newMessage(pendingResponse!!))
            pendingResponse = null
        }
    }
}