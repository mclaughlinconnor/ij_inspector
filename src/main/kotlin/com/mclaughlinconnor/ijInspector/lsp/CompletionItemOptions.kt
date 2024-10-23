package com.mclaughlinconnor.ijInspector.lsp

import com.fasterxml.jackson.annotation.JsonProperty

class CompletionItemOptions(
    @JsonProperty
    val labelDetailsSupport: Boolean? = null
)
