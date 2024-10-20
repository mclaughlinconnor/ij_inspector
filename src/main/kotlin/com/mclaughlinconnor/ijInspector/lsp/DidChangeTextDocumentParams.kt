package com.mclaughlinconnor.ijInspector.lsp

import com.fasterxml.jackson.annotation.JsonProperty

class DidChangeTextDocumentParams(
    @JsonProperty
    val textDocument: VersionedTextDocumentIdentifier = VersionedTextDocumentIdentifier("", 0),

    @JsonProperty
    val contentChanges: List<TextDocumentContentChangeEvent> = ArrayList()
)