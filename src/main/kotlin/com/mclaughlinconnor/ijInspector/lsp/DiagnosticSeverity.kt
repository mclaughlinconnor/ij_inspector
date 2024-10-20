package com.mclaughlinconnor.ijInspector.lsp

@Suppress("ConstPropertyName")
class DiagnosticSeverityEnum {
    companion object {
        const val Error: Int = 1
        const val Warning: Int = 2
        const val Information: Int = 3
        const val Hint: Int = 4
    }
}

typealias DiagnosticSeverity = Int