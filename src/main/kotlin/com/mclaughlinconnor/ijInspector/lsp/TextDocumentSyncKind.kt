package com.mclaughlinconnor.ijInspector.lsp

class TextDocumentSyncKindEnum {
    @Suppress("unused", "ConstPropertyName")
    companion object {
        const val None = 0
        const val Full = 1
        const val Incremental = 2
    }
}

typealias TextDocumentSyncKind = Int