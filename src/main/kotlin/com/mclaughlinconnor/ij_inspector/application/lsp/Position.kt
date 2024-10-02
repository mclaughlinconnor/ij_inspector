package com.mclaughlinconnor.ij_inspector.application.lsp

import com.fasterxml.jackson.annotation.JsonProperty

class Position (
    /**
     * Line position in a document (zero-based).
     */
    @JsonProperty
    val line: Int = 0,

    /**
     * Character offset on a line in a document (zero-based). The meaning of this
     * offset is determined by the negotiated `PositionEncodingKind`.
     *
     * If the character value is greater than the line length it defaults back
     * to the line length.
     */
    @JsonProperty
    val character: Int = 0,
)
