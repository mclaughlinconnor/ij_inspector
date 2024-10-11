package com.mclaughlinconnor.ij_inspector.application.lsp

@Suppress("unused", "ConstPropertyName")
class CompletionTriggerKindEnum {
    companion object {
        /**
         * Completion was triggered by typing an identifier (24x7 code
         * complete), manual invocation (e.g Ctrl+Space) or via API.
         */
        const val Invoked = 1

        /**
         * Completion was triggered by a trigger character specified by
         * the `triggerCharacters` properties of the
         * `CompletionRegistrationOptions`.
         */
        const val TriggerCharacter = 2

        /**
         * Completion was re-triggered as the current completion list is incomplete.
         */
        const val TriggerForIncompleteCompletions = 3
    }
}

typealias CompletionTriggerKind = Int