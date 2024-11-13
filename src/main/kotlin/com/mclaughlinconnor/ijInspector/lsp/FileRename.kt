package com.mclaughlinconnor.ijInspector.lsp

import com.fasterxml.jackson.annotation.JsonProperty

class FileRename(
    @JsonProperty
    val oldUri: String = "",

    @JsonProperty
    val newUri: String = "",
)