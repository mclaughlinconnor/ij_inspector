package com.mclaughlinconnor.ijInspector.lsp

import com.fasterxml.jackson.annotation.JsonProperty

class CodeActionContext(
    @JsonProperty
    val diagnostics: Array<Diagnostic> = arrayOf(),

    @JsonProperty
    val only: Array<CodeActionKind>? = null,

    @JsonProperty
    val triggerKind: CodeActionTriggerKind? = null,
)