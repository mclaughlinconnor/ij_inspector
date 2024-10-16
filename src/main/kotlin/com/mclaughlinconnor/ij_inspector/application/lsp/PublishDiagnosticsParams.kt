package com.mclaughlinconnor.ij_inspector.application.lsp

import com.fasterxml.jackson.annotation.JsonProperty

class PublishDiagnosticsParams(
    @JsonProperty
    val uri: String = "",

    @JsonProperty
    val version: Int? = null,

    @JsonProperty
    val diagnostics: List<Diagnostic> = ArrayList(),
)