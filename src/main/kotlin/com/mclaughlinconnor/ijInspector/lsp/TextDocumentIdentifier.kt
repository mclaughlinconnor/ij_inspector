package com.mclaughlinconnor.ijInspector.lsp

import com.fasterxml.jackson.annotation.JsonProperty

open class TextDocumentIdentifier(
    /**
     * The text document's URI.
     */
    @JsonProperty
    val uri: String = ""
)
