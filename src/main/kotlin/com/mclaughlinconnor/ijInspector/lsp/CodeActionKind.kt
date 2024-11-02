package com.mclaughlinconnor.ijInspector.lsp

@Suppress("ConstPropertyName", "unused")
class CodeActionKindEnum {
    companion object {
        const val Empty: CodeActionKind = ""

        const val QuickFix: CodeActionKind = "quickfix"

        const val Refactor: CodeActionKind = "refactor"

        const val RefactorExtract: CodeActionKind = "refactor.extract"

        const val RefactorInline: CodeActionKind = "refactor.inline"

        const val RefactorRewrite: CodeActionKind = "refactor.rewrite"

        const val Source: CodeActionKind = "source"

        const val SourceOrganizeImports: CodeActionKind = "source.organizeImports"

        const val SourceFixAll: CodeActionKind = "source.fixAll"
    }
}

typealias CodeActionKind = String