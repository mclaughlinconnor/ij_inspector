package com.mclaughlinconnor.ijInspector.lsp

import com.fasterxml.jackson.annotation.JsonProperty

class Command(
    @JsonProperty
    val title: String = "",

    @JsonProperty
    val command: String = "",

    @JsonProperty
    val arguments: List<String>? = null, // actually List<Any>?
)