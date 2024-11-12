package com.mclaughlinconnor.ijInspector.lsp

@Suppress("ConstPropertyName", "unused")
class CompletionItemKindEnum {
    companion object {
        const val Text = 1
        const val Method = 2
        const val Function = 3
        const val Constructor = 4
        const val Field = 5
        const val Variable = 6
        const val Class = 7
        const val Interface = 8
        const val Module = 9
        const val Property = 10
        const val Unit = 11
        const val Value = 12
        const val Enum = 13
        const val Keyword = 14
        const val Snippet = 15
        const val Color = 16
        const val File = 17
        const val Reference = 18
        const val Folder = 19
        const val EnumMember = 20
        const val Constant = 21
        const val Struct = 22
        const val Event = 23
        const val Operator = 24
        const val TypeParameter = 25
    }
}

typealias CompletionItemKind = Int