package com.mclaughlinconnor.ijInspector.lsp

import com.fasterxml.jackson.annotation.JsonProperty

class ApplyWorkspaceEditParams(
    @JsonProperty
    val label: String?,

    @JsonProperty
    val edit: WorkspaceEdit?
)