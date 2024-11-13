package com.mclaughlinconnor.ijInspector.lsp

import com.fasterxml.jackson.annotation.JsonProperty

class RenameFilesParams(
    @JsonProperty
    val files: Array<FileRename> = arrayOf(),
)