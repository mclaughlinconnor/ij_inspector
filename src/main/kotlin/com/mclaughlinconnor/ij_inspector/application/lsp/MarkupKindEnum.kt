package com.mclaughlinconnor.ij_inspector.application.lsp

@Suppress("unused")
class MarkupKindEnum {
    companion object {
        const val MARKDOWN = "markup"
        const val PLAINTEXT = "plaintext"
    }
}

typealias MarkupKind = String