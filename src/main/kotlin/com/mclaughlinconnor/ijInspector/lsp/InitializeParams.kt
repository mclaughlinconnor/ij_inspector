package com.mclaughlinconnor.ijInspector.lsp

import com.fasterxml.jackson.annotation.JsonProperty

class InitializeParams(
    @JsonProperty
    val processId: Int? = null,

    @JsonProperty
    val clientInfo: Any? = null,

    @JsonProperty
    val locale: String? = null,

    @JsonProperty
    val rootPath: String? = null,

    @JsonProperty
    val rootUri: String? = null,

    @JsonProperty
    val initializationOptions: Any? = null,

    @JsonProperty
    val capabilities: ClientCapabilities = ClientCapabilities.EMPTY,

    @JsonProperty
    val trace: Any? = null,

    @JsonProperty
    val workspaceFolders: Any? = null,

    @JsonProperty
    val workDoneToken: Any? = null,
)