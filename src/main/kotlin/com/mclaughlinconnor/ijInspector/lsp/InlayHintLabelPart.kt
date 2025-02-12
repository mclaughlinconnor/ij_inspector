package com.mclaughlinconnor.ijInspector.lsp

import com.fasterxml.jackson.annotation.JsonProperty

class InlayHintLabelPart(
    @JsonProperty
    val value: String = "",

    @JsonProperty
    val tooltip: MarkupContent? = null,

    @JsonProperty
    val location: Location? = null,

    @JsonProperty
    val command: Command? = null,
)