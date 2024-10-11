package com.mclaughlinconnor.ij_inspector.application.lsp

import com.fasterxml.jackson.annotation.JsonProperty

@Suppress("unused")
class MarkupKind {
    companion object {
        const val MARKDOWN = "markup"
        const val PLAINTEXT = "plaintext"
    }
}

class MarkupContent(@JsonProperty val kind: String = "", @JsonProperty val value: String = "")