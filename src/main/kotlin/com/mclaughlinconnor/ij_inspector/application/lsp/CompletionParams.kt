package com.mclaughlinconnor.ij_inspector.application.lsp

import com.fasterxml.jackson.annotation.JsonProperty

class CompletionParams(
    /**
     * The text document.
     */
    @JsonProperty
    val textDocument: TextDocumentIdentifier = TextDocumentIdentifier(""),

    /**
     * The position inside the text document.
     */
    @JsonProperty
    val position: Position = Position(0, 0),

    @JsonProperty
    val context: CompletionContext? = null,
)
