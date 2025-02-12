package com.mclaughlinconnor.ijInspector.lsp

import com.fasterxml.jackson.annotation.JsonProperty

class InlayHintOptions(
    @JsonProperty
    val resolveProvider: Boolean? = null
)