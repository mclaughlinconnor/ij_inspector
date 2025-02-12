package com.mclaughlinconnor.ijInspector.languageService

import com.intellij.util.containers.toArray
import com.mclaughlinconnor.ijInspector.lsp.*
import com.mclaughlinconnor.ijInspector.rpc.Connection
import com.mclaughlinconnor.ijInspector.rpc.MessageFactory

class InitializeService(
    private val myConnection: Connection
) {
    private val messageFactory: MessageFactory = MessageFactory()
    private var pendingResponse: Response? = null

    fun startInitialise(requestId: Int, params: InitializeParams): String? {
        val triggerCharacters = ('A'..'z').map { it.toString() }.toMutableList()
        triggerCharacters.addAll(listOf(".", "\"", "'", "`", "/", "@", "<", "#", " ", "*"))

        val serverCapabilities = ServerCapabilities(
            completionProvider = CompletionOptions(
                resolveProvider = true,
                completionItem = CompletionItemOptions(labelDetailsSupport = true),
                triggerCharacters = triggerCharacters.toArray(arrayOf()),
                allCommitCharacters = arrayOf(".", ",", ";"),
            ),
            hoverProvider = true,
            definitionProvider = true,
            inlayHintProvider = true,
            referencesProvider = true,
            codeActionProvider = true,
            executeCommandProvider = ExecuteCommandOptions(arrayOf(CODE_ACTION_COMMAND)),
            renameProvider = true,
            workspace = WorkspaceCapabilities(
                workspaceFolders = WorkspaceFoldersServerCapabilities(
                    supported = true,
                    changeNotifications = true,
                ),
                fileOperations = WorkspaceFileOperationsCapabilities(
                    didCreate = FileOperationRegistrationOptions(
                        filters = arrayOf(
                            FileOperationFilter(
                                pattern = FileOperationPattern(glob = "**/*")
                            )
                        )
                    ),
                    didDelete = FileOperationRegistrationOptions(
                        filters = arrayOf(
                            FileOperationFilter(
                                pattern = FileOperationPattern(glob = "**/*")
                            )
                        )
                    ),
                    didRename = FileOperationRegistrationOptions(
                        filters = arrayOf(
                            FileOperationFilter(
                                pattern = FileOperationPattern(glob = "**/*")
                            )
                        )
                    )
                )
            )
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