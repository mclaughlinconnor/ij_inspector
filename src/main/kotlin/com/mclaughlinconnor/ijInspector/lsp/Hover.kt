package com.mclaughlinconnor.ijInspector.lsp

import com.fasterxml.jackson.annotation.JsonProperty

class Hover(
    @JsonProperty
    val contents: MarkupContent = MarkupContent(MarkupKindEnum.PLAINTEXT, ""),

    @JsonProperty
    val range: Range? = null,
)