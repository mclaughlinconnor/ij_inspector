package com.mclaughlinconnor.ijInspector.lsp

import com.fasterxml.jackson.annotation.JsonProperty

class ClientCapabilities(
    @JsonProperty
    val workspace: Any? = null,

    @JsonProperty
    val textDocument: Any? = null,

    @JsonProperty
    val notebookDocument: Any? = null,

    @JsonProperty
    val window: Any? = null,

    @JsonProperty
    val general: Any? = null,

    @JsonProperty
    val experimental: Any? = null,

    @JsonProperty
    val inlayHint: Any? = null,
) {
    companion object {
        val EMPTY = ClientCapabilities()
    }
}
