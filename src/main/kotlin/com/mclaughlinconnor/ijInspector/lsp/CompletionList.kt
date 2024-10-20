package com.mclaughlinconnor.ijInspector.lsp

import com.fasterxml.jackson.annotation.JsonProperty

class CompletionList(
    /**
     * This list is not complete. Further typing should result in recomputing
     * this list.
     *
     * Recomputed lists have all their items replaced (not appended) in the
     * incomplete completion sessions.
     */
    @JsonProperty
    val isIncomplete: Boolean,
    /**
     * The completion items.
     */
    @JsonProperty
    val items: MutableList<CompletionItem>
) {
    fun pushItem(item: CompletionItem) {
        items.add(item)
    }
}
