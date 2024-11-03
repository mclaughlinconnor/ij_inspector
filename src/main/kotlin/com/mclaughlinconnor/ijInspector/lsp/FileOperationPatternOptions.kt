package com.mclaughlinconnor.ijInspector.lsp

import com.fasterxml.jackson.annotation.JsonProperty

class FileOperationPatternOptions(
    @JsonProperty
    val ignoreCase: Boolean?
)