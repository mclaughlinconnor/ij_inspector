package com.mclaughlinconnor.ij_inspector.application.lsp

import com.fasterxml.jackson.annotation.JsonProperty

class DiagnosticRelatedInformation(
    @JsonProperty
    val location: Location,

    @JsonProperty
    val message: String = "",
)