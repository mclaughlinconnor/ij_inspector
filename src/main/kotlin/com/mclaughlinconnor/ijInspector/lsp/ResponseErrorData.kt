package com.mclaughlinconnor.ijInspector.lsp

import com.fasterxml.jackson.annotation.JsonProperty

class ResponseErrorData(
    @JsonProperty
    val stackTrace: String = "",
)