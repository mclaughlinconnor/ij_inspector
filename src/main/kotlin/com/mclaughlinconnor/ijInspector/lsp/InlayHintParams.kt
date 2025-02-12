package com.mclaughlinconnor.ijInspector.lsp

import com.fasterxml.jackson.annotation.JsonProperty

class InlayHintParams(
    @JsonProperty
    val textDocument: TextDocumentIdentifier = TextDocumentIdentifier(),

    @JsonProperty
    val range: Range = Range(),
)