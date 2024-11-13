package com.mclaughlinconnor.ijInspector.lsp

import com.fasterxml.jackson.annotation.JsonProperty

class Response (
    @JsonProperty
    val id: Int = 0,

    @JsonProperty
    val result: Any? = null,

    @JsonProperty
    val error: ResponseError? = null,
) {
    @JsonProperty
    val jsonrpc: String = "2.0"
}