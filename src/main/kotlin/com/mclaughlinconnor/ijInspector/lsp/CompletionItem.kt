package com.mclaughlinconnor.ijInspector.lsp

import com.fasterxml.jackson.annotation.JsonProperty

class CompletionItem(
    label: String = "",
    detail: String? = null,
    documentation: MarkupContent? = null,
    insertText: String = "",
    labelDetails: CompletionItemLabelDetails? = null,
    additionalTextEdits: MutableList<TextEdit>? = null,
    textEdit: TextEdit? = null,
    data: CompletionItemData = CompletionItemData("", Position(0, 0), '\u0000')
) {
    @JsonProperty
    val labelDetails: CompletionItemLabelDetails? = labelDetails

    /**
     * The label of this completion item.
     *
     * The label property is also by default the text that
     * is inserted when selecting this completion.
     *
     * If label details are provided the label itself should
     * be an unqualified name of the completion item.
     */
    @JsonProperty
    val label: String = label

    /**
     * The kind of this completion item. Based of the kind
     * an icon is chosen by the editor. The standardized set
     * of available values is defined in `CompletionItemKind`.
     */
    // val kind?: CompletionItemKind;

    /**
     * Tags for this completion item.
     *
     * @since 3.15.0
     */
    // val tags?: CompletionItemTag[];

    /**
     * A human-readable string with additional information
     * about this item, like type or symbol information.
     */
    @JsonProperty
    val detail: String? = detail

    /**
     * A human-readable string that represents a doc-comment.
     */
    @JsonProperty
    var documentation: MarkupContent? = documentation

    /**
     * A string that should be used when comparing this item
     * with other items. When omitted the label is used
     * as the sort text for this item.
     */
    // val sortText?: string; // Are the results I get sorted?

    /**
     * A string that should be inserted into a document when selecting
     * this completion. When omitted the label is used as the insert text
     * for this item.
     *
     * The `insertText` is subject to interpretation by the client side.
     * Some tools might not take the string literally. For example
     * VS Code when code complete is requested in this example
     * `con<cursor position>` and a completion item with an `insertText` of
     * `console` is provided it will only insert `sole`. Therefore it is
     * recommended to use `textEdit` instead since it avoids additional client
     * side interpretation.
     */
    @JsonProperty
    val insertText: String = insertText

    /**
     * An edit which is applied to a document when selecting this completion.
     * When an edit is provided the value of `insertText` is ignored.
     *
     * *Note:* The range of the edit must be a single line range and it must
     * contain the position at which completion has been requested.
     *
     * Most editors support two different operations when accepting a completion
     * item. One is to insert a completion text and the other is to replace an
     * existing text with a completion text. Since this can usually not be
     * predetermined by a server it can report both ranges. Clients need to
     * signal support for `InsertReplaceEdit`s via the
     * `textDocument.completion.completionItem.insertReplaceSupport` client
     * capability property.
     *
     * *Note 1:* The text edit's range as well as both ranges from an insert
     * replace edit must be a [single line] and they must contain the position
     * at which completion has been requested.
     * *Note 2:* If an `InsertReplaceEdit` is returned the edit's insert range
     * must be a prefix of the edit's replace range, that means it must be
     * contained and starting at the same position.
     *
     * @since 3.16.0 additional type `InsertReplaceEdit`
     */
    var textEdit: TextEdit? = textEdit

    /**
     * An optional array of additional text edits that are applied when
     * selecting this completion. Edits must not overlap (including the same
     * insert position) with the main edit nor with themselves.
     *
     * Additional text edits should be used to change text unrelated to the
     * current cursor position (for example adding an import statement at the
     * top of the file if the completion item will insert an unqualified type).
     */
    var additionalTextEdits: MutableList<TextEdit>? = additionalTextEdits

    /**
     * An optional set of characters that when pressed while this completion is
     * active will accept it first and then type that character. *Note* that all
     * commit characters should have `length=1` and that superfluous characters
     * will be ignored.
     */
    // val commitCharacters?: string[];

    /**
     * An optional command that is executed *after* inserting this completion.
     * *Note* that additional modifications to the current document should be
     * described with the additionalTextEdits-property.
     */
    // val command?: Command;

    /**
     * A data entry field that is preserved on a completion item between
     * a completion and a completion resolve request.
     */
    @JsonProperty
    val data: CompletionItemData = data
}