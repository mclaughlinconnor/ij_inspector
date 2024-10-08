package com.mclaughlinconnor.ij_inspector.application

import com.fasterxml.jackson.annotation.JsonProperty
import com.mclaughlinconnor.ij_inspector.application.lsp.CompletionTriggerKind
import com.mclaughlinconnor.ij_inspector.application.lsp.CompletionTriggerKindEnum

class CompletionContext {
    /**
     * How the completion was triggered.
     */
    @JsonProperty
    val triggerKind: CompletionTriggerKind = CompletionTriggerKindEnum.Invoked

    /**
     * The trigger character (a single character) that has trigger code
     * complete. Is undefined if
     * `triggerKind !== CompletionTriggerKind.TriggerCharacter`
     */
    @JsonProperty
    val triggerCharacter: String? = null
}