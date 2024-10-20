package com.mclaughlinconnor.ijInspector.lsp

import com.fasterxml.jackson.annotation.JsonProperty

class ReferenceParams(
    @JsonProperty
    val textDocument: TextDocumentIdentifier = TextDocumentIdentifier(""),

    @JsonProperty
    val position: Position = Position(0, 0),

    @JsonProperty
    val context: ReferenceContext = ReferenceContext(
        false
    )
)