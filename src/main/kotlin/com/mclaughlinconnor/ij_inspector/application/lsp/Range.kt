package com.mclaughlinconnor.ij_inspector.application.lsp

import com.fasterxml.jackson.annotation.JsonProperty

class Range(@JsonProperty val start: Position, @JsonProperty var end: Position) {
    companion object {
        val EMPTY = Range(Position(0, 0), Position(0, 0))
    }
}
