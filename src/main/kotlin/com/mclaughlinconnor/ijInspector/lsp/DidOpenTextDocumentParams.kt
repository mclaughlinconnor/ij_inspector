package com.mclaughlinconnor.ijInspector.lsp

import com.fasterxml.jackson.annotation.JsonProperty

class DidOpenTextDocumentParams(
    @JsonProperty
    val textDocument: TextDocumentItem = TextDocumentItem("", "", 0, "")
)