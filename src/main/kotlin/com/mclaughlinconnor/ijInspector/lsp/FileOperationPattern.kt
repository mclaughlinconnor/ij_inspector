package com.mclaughlinconnor.ijInspector.lsp

import com.fasterxml.jackson.annotation.JsonProperty

class FileOperationPattern(
    @JsonProperty
    val glob: String,

    @JsonProperty
    val matches: FileOperationPatternKind? = null,

    @JsonProperty
    val options: FileOperationPatternOptions? = null,
)