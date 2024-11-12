package com.mclaughlinconnor.ijInspector.lsp

@Suppress("unused", "ConstPropertyName")
class InsertTextFormatEnum {
    companion object {
        const val PlainText = 1

        const val Snippet = 2
    }
}

typealias InsertTextFormat = Int