package com.mclaughlinconnor.ij_inspector.application.lsp

import com.fasterxml.jackson.annotation.JsonProperty

class Request(
    @JsonProperty val jsonrpc: String = "2.0",
    @JsonProperty val id: Int = 0,
    @JsonProperty val params: Any = 0,
    @JsonProperty val method: String = ""
)
