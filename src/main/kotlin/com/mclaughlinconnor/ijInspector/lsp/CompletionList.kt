package com.mclaughlinconnor.ijInspector.lsp

import com.fasterxml.jackson.annotation.JsonProperty

class CompletionList(
    @get:JsonProperty("isIncomplete")
    val isIncomplete: Boolean,

    @JsonProperty
    val items: MutableList<CompletionItem>,
) {
    fun pushItem(item: CompletionItem) {
        items.add(item)
    }
}
