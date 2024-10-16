package com.mclaughlinconnor.ij_inspector.application.lsp

import com.fasterxml.jackson.annotation.JsonProperty

class Location(
    @JsonProperty
    val uri: String = "",

    @JsonProperty
    val range: Range = Range.EMPTY
)