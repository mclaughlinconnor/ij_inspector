package com.mclaughlinconnor.ijInspector.lsp

import com.fasterxml.jackson.annotation.JsonProperty

class ExecuteCommandOptions(
    @JsonProperty
    val commands: Array<String> = arrayOf()
)