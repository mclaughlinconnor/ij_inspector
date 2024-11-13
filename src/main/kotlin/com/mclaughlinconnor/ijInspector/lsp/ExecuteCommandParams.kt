package com.mclaughlinconnor.ijInspector.lsp

import com.fasterxml.jackson.annotation.JsonProperty

class ExecuteCommandParams(
    @JsonProperty
    val command: String = "",

    @JsonProperty
    val arguments: Array<String>? = null
)