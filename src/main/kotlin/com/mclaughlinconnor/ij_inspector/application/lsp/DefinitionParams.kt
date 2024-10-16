package com.mclaughlinconnor.ij_inspector.application.lsp

import com.fasterxml.jackson.annotation.JsonProperty

class DefinitionParams(
    @JsonProperty
    val textDocument: TextDocumentIdentifier = TextDocumentIdentifier(""),

    @JsonProperty
    val position: Position = Position(0, 0)
)