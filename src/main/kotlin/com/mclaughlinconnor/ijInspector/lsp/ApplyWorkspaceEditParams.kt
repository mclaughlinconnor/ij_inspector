package com.mclaughlinconnor.ijInspector.lsp

import com.fasterxml.jackson.annotation.JsonProperty

class ApplyWorkspaceEditParams(
    @JsonProperty
    val label: String? = null,

    @JsonProperty
    val edit: WorkspaceEdit? = null,
)