package com.mclaughlinconnor.ij_inspector.application.lsp

import com.fasterxml.jackson.annotation.JsonProperty

class Response (
    @JsonProperty
    val id: Int = 0,

    @JsonProperty
    val result: Any = 0,
)