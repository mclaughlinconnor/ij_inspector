package com.mclaughlinconnor.ij_inspector.application.lsp

import com.fasterxml.jackson.annotation.JsonProperty

class TextDocumentIdentifier(
    /**
     * The text document's URI.
     */
    @JsonProperty
    val uri: String = ""
)
