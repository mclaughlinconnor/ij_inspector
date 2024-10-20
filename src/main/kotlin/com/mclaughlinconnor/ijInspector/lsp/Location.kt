package com.mclaughlinconnor.ijInspector.lsp

import com.fasterxml.jackson.annotation.JsonProperty

class Location(
    @JsonProperty
    val uri: String = "",

    @JsonProperty
    val range: Range = Range.EMPTY
)