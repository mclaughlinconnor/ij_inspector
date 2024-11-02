package com.mclaughlinconnor.ijInspector.lsp

import com.fasterxml.jackson.annotation.JsonProperty

class CodeActionParams(
    @JsonProperty
    val textDocument: TextDocumentIdentifier = TextDocumentIdentifier(),

    @JsonProperty
    val range: Range = Range.EMPTY,

    @JsonProperty
    val context: CodeActionContext = CodeActionContext(),
)