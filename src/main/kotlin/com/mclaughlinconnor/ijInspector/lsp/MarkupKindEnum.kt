package com.mclaughlinconnor.ijInspector.lsp

@Suppress("unused")
class MarkupKindEnum {
    companion object {
        const val MARKDOWN = "markup"
        const val PLAINTEXT = "plaintext"
    }
}

typealias MarkupKind = String