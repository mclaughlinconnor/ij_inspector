package com.mclaughlinconnor.ijInspector.lsp

import com.fasterxml.jackson.annotation.JsonProperty

class PublishDiagnosticsParams(
    @JsonProperty
    val uri: String = "",

    @JsonProperty
    val version: Int? = null,

    @JsonProperty
    val diagnostics: List<Diagnostic> = ArrayList(),
)