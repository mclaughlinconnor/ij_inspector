package com.mclaughlinconnor.ijInspector.lsp

import com.fasterxml.jackson.annotation.JsonProperty

class DeleteFilesParams(
    @JsonProperty
    val files: Array<FileDelete> = arrayOf(),
)