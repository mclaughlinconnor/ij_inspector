package com.mclaughlinconnor.ijInspector.lsp

import com.fasterxml.jackson.annotation.JsonProperty

class InitializeResult(
    @JsonProperty
    val capabilities: ServerCapabilities = ServerCapabilities.EMPTY,

    @JsonProperty
    val serverInfo: Any? = null,
)