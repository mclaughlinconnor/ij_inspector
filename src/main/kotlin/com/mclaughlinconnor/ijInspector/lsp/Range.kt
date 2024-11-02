package com.mclaughlinconnor.ijInspector.lsp

import com.fasterxml.jackson.annotation.JsonProperty

class Range(
    @JsonProperty val start: Position = Position(0, 0),

    @JsonProperty var end: Position = Position(0, 0)
) {
    companion object {
        val EMPTY = Range(Position(0, 0), Position(0, 0))
    }
}
