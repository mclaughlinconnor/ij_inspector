package com.mclaughlinconnor.ij_inspector.application.lsp

@Suppress("unused", "ConstPropertyName")
class DiagnosticTagEnum {
    companion object {
        /**
         * Unused or unnecessary code.
         *
         * Clients are allowed to render diagnostics with this tag faded out
         * instead of having an error squiggle.
         */
        const val Unnecessary: Int = 1

        /**
         * Deprecated or obsolete code.
         *
         * Clients are allowed to rendered diagnostics with this tag strike through.
         */
        const val Deprecated: Int = 2
    }
}

typealias DiagnosticTag = Int