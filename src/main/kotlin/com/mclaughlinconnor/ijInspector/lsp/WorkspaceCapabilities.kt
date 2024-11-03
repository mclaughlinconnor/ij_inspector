package com.mclaughlinconnor.ijInspector.lsp

import com.fasterxml.jackson.annotation.JsonProperty

class WorkspaceCapabilities(
    @JsonProperty
    val workspaceFolders: WorkspaceFoldersServerCapabilities?,

    @JsonProperty
    val fileOperations: WorkspaceFileOperationsCapabilities?
)