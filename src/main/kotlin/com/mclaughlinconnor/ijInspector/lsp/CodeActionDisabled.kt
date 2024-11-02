package com.mclaughlinconnor.ijInspector.lsp

import com.fasterxml.jackson.annotation.JsonProperty

class CodeActionDisabled(
    @JsonProperty
    val reason: String = ""
)