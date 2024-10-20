package com.mclaughlinconnor.ijInspector.lsp

import com.fasterxml.jackson.annotation.JsonProperty

class Request(
    @JsonProperty val id: Int = 0,
    @JsonProperty val params: Any = 0,
    @JsonProperty val method: String = ""
) {
    @JsonProperty
    val jsonrpc: String = "2.0"
}
