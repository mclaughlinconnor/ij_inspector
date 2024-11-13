package com.mclaughlinconnor.ijInspector.lsp

import com.fasterxml.jackson.annotation.JsonProperty

class ResponseError(
    @JsonProperty
    val code: Int = 0,

    @JsonProperty
    val message: String = "",

    @JsonProperty
    val data: ResponseErrorData? = null,
)