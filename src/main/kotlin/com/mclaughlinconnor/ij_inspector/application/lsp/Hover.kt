package com.mclaughlinconnor.ij_inspector.application.lsp

import com.fasterxml.jackson.annotation.JsonProperty

class Hover(
    @JsonProperty
    val contents: MarkupContent = MarkupContent(MarkupKindEnum.PLAINTEXT, ""),

    @JsonProperty
    val range: Range? = null,
)