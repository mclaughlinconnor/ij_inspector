package com.mclaughlinconnor.ijInspector.lsp

class InlayHintKindEnum {
    companion object {
        const val Type: InlayHintKind = 1

        const val Parameter: InlayHintKind = 2
    }
}

typealias InlayHintKind = Int
