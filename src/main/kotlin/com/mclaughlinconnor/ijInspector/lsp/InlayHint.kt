package com.mclaughlinconnor.ijInspector.lsp

import com.fasterxml.jackson.annotation.JsonProperty

class InlayHint(
    @JsonProperty
    val position: Position = Position(),

    @JsonProperty
    val label: Array<InlayHintLabelPart> = arrayOf(),

    @JsonProperty
    val kind: InlayHintKind? = null,

    @JsonProperty
    val textEdits: Array<TextEdit>? = null,

    @JsonProperty
    val tooltip: MarkupContent? = null,

    @JsonProperty
    val paddingLeft: Boolean? = null,

    @JsonProperty
    val paddingRight: Boolean? = null,

    @JsonProperty
    val data: Any? = null,
)