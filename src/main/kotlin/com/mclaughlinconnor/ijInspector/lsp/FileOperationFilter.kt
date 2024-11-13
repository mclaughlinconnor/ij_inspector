package com.mclaughlinconnor.ijInspector.lsp

import com.fasterxml.jackson.annotation.JsonProperty

class FileOperationFilter(
    @JsonProperty
    val scheme: String? = null,

    @JsonProperty
    val pattern: FileOperationPattern = FileOperationPattern(),
)