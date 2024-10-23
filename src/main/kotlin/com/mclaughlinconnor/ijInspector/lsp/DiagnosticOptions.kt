package com.mclaughlinconnor.ijInspector.lsp

import com.fasterxml.jackson.annotation.JsonProperty

class DiagnosticOptions(
    @JsonProperty
    val identifier: String? = null,

    @JsonProperty
    val interFileDependencies: Boolean = false,

    @JsonProperty
    val workspaceDiagnostics: Boolean = false
)