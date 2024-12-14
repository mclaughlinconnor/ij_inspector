package com.mclaughlinconnor.ijInspector.lsp

@Suppress("unused", "ConstPropertyName")
class DiagnosticTagEnum {
    companion object {
        /**
         * Unused or unnecessary code.
         *
         * Clients are allowed to render diagnostics with this tag faded out
         * instead of having an error squiggle.
         */
        const val Unnecessary: DiagnosticTag = 1

        /**
         * Deprecated or obsolete code.
         *
         * Clients are allowed to rendered diagnostics with this tag strike through.
         */
        const val Deprecated: DiagnosticTag = 2
    }
}

typealias DiagnosticTag = Int