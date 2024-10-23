package com.mclaughlinconnor.ijInspector.lsp

import com.fasterxml.jackson.annotation.JsonProperty

class CompletionOptions(
    @JsonProperty
    val triggerCharacters: Array<String>? = null,

    @JsonProperty
    val allCommitCharacters: Array<String>? = null,

    @JsonProperty
    val resolveProvider: Boolean? = null,

    @JsonProperty
    val completionItem: CompletionItemOptions? = null
)