package com.mclaughlinconnor.ijInspector.languageService

import com.mclaughlinconnor.ijInspector.lsp.*
import com.mclaughlinconnor.ijInspector.rpc.Connection
import com.mclaughlinconnor.ijInspector.rpc.MessageFactory

class InitializeService(
    private val myConnection: Connection
) {
    private val messageFactory: MessageFactory = MessageFactory()

    fun doInitialize(requestId: Int, params: InitializeParams): String? {
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
            )
        )
        val result = InitializeResult(serverCapabilities)
        val response = Response(requestId, result)

        myConnection.write(messageFactory.newMessage(response))

        return params.rootUri ?: params.rootPath
    }
}