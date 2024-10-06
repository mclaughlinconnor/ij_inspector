package com.mclaughlinconnor.ij_inspector.application.lsp

import com.fasterxml.jackson.annotation.JsonProperty

class TextDocumentItem(
    @JsonProperty
    val uri: String = "",

    @JsonProperty
    val languageId: String = "",

    @JsonProperty
    val version: Int = 0,

    @JsonProperty
    val text: String = "",
)