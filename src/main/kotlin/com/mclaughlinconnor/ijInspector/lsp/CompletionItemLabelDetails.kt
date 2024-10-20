package com.mclaughlinconnor.ijInspector.lsp

import com.fasterxml.jackson.annotation.JsonProperty

class CompletionItemLabelDetails(
    @JsonProperty val detail: String? = null,
    @JsonProperty val description: String? = null
)
