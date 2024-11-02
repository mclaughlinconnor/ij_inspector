package com.mclaughlinconnor.ijInspector.lsp

import com.fasterxml.jackson.annotation.JsonProperty

class ChangeAnnotation(
    @JsonProperty
    val label: String = "",

    @JsonProperty
    val needsConfirmation: Boolean? = null,

    @JsonProperty
    val description: String? = null
)