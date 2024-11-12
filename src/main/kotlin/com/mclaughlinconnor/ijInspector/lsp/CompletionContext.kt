package com.mclaughlinconnor.ijInspector.lsp

import com.fasterxml.jackson.annotation.JsonProperty

class CompletionContext {
    /**
     * How the completion was triggered.
     */
    @JsonProperty
    var triggerKind: CompletionTriggerKind = CompletionTriggerKindEnum.Invoked

    /**
     * The trigger character (a single character) that has trigger code
     * complete. Is undefined if
     * `triggerKind !== CompletionTriggerKind.TriggerCharacter`
     */
    @JsonProperty
    var triggerCharacter: String? = null
}