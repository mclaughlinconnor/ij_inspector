package com.mclaughlinconnor.ijInspector.lsp

import com.fasterxml.jackson.annotation.JsonProperty

abstract class AbstractTextDocumentEdit(
    @JsonProperty
    val edits: Array<TextEdit>? = null,

    @JsonProperty
    val kind: String? = null,

    @JsonProperty
    val newUri: DocumentUri? = null,

    @JsonProperty
    val oldUri: DocumentUri? = null,

    @JsonProperty
    val options: AbstractFileEditOptions? = null,

    @JsonProperty
    val textDocument: TextDocumentIdentifier? = null,

    @JsonProperty
    val uri: DocumentUri? = null,
)