package com.mclaughlinconnor.ijInspector.lsp

import com.fasterxml.jackson.annotation.JsonProperty

class FileOperationRegistrationOptions(
    @JsonProperty
    val filters: Array<FileOperationFilter> = arrayOf(),
)