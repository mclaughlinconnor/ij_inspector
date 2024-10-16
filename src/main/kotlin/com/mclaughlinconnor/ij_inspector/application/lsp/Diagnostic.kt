package com.mclaughlinconnor.ij_inspector.application.lsp

import com.fasterxml.jackson.annotation.JsonProperty

class Diagnostic(
    @JsonProperty
    val range: Range = Range.EMPTY,

    @JsonProperty
    val severity: DiagnosticSeverity = DiagnosticSeverityEnum.Error,

    @JsonProperty
    val code: String? = "",

    @JsonProperty
    val codeDescription: CodeDescription? = null,

    @JsonProperty
    val message: String,

    @JsonProperty
    val tags: List<DiagnosticTag>? = null,

    @JsonProperty
    val relatedInformation: List<DiagnosticRelatedInformation>? = null,

    @JsonProperty
    val data: Any? = null,
) {
    @JsonProperty
    val source: String = "ij_inspector"
}