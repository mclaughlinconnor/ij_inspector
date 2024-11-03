package com.mclaughlinconnor.ijInspector.lsp

import com.fasterxml.jackson.annotation.JsonProperty

class FileCreate(
    @JsonProperty
    val uri: String = ""
)