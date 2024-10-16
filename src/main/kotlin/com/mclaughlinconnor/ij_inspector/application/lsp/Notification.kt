package com.mclaughlinconnor.ij_inspector.application.lsp

import com.fasterxml.jackson.annotation.JsonProperty

class Notification(
    @JsonProperty
    val method: String = "",

    @JsonProperty
    val params: Any? = null
) {
    @JsonProperty
    val jsonrpc: String = "2.0"
}