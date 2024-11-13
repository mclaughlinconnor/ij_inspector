package com.mclaughlinconnor.ijInspector.lsp

import com.fasterxml.jackson.annotation.JsonProperty

class RenameParams(
    @JsonProperty
    val textDocument: TextDocumentIdentifier = TextDocumentIdentifier(),

    @JsonProperty
    val position: Position = Position(),

    @JsonProperty
    val newName: String = "",
)