package com.mclaughlinconnor.ijInspector.lsp

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