package com.mclaughlinconnor.ijInspector.lsp

import com.fasterxml.jackson.annotation.JsonProperty

class CodeAction(
    @JsonProperty
    val title: String = "",

    @JsonProperty
    val kind: CodeActionKind? = null,

    @JsonProperty
    val diagnostics: List<Diagnostic>? = null,

    @JsonProperty
    val isPreferred: Boolean? = null,

    @JsonProperty
    val disabled: CodeActionDisabled? = null,

    @JsonProperty
    val edit: WorkspaceEdit? = null,

    @JsonProperty
    val command: Command? = null,

    @JsonProperty
    val data: Any? = null,
)