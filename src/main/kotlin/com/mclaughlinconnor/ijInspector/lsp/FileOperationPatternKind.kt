package com.mclaughlinconnor.ijInspector.lsp

@Suppress("ConstPropertyName", "unused")
class FileOperationPatternKindEnum {
    companion object {
        const val file = "file"

        const val folder = "folder"
    }
}

typealias FileOperationPatternKind = String
