package com.mclaughlinconnor.ijInspector.lsp

import com.fasterxml.jackson.annotation.JsonProperty

class DidSaveTextDocumentParams(
    @JsonProperty
    val textDocument: TextDocumentItem = TextDocumentItem("", "", 0, ""),

    @JsonProperty
    val text: String = "",
)