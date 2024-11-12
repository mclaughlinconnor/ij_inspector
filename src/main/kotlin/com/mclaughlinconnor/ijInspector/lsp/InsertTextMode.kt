package com.mclaughlinconnor.ijInspector.lsp

@Suppress("ConstPropertyName", "unused")
class InsertTextModeEnum {
    companion object {
        const val asIs = 1

        const val adjustIndentation = 2
    }
}

typealias InsertTextMode = Int