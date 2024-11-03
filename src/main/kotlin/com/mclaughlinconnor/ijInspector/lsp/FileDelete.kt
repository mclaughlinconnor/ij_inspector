package com.mclaughlinconnor.ijInspector.lsp

import com.fasterxml.jackson.annotation.JsonProperty

class FileDelete(
    @JsonProperty
    val uri: String,
)