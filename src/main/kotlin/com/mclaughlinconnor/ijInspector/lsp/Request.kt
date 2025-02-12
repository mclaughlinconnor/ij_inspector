package com.mclaughlinconnor.ijInspector.lsp

import com.fasterxml.jackson.annotation.JsonProperty

class Request(
    @JsonProperty(required = true) val id: Int = Int.MIN_VALUE,
    @JsonProperty val params: Any? = null,
    @JsonProperty val method: String = ""
) {
    @JsonProperty
    val jsonrpc: String = "2.0"
}
