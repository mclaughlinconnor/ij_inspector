package com.mclaughlinconnor.ijInspector.lsp

import com.fasterxml.jackson.annotation.JsonProperty

class CompletionItem(
    @JsonProperty
    val label: String = "",

    @JsonProperty
    val labelDetails: CompletionItemLabelDetails? = null,

    @JsonProperty
    val kind: CompletionItemKind? = null,

    @JsonProperty
    val tags: Array<CompletionItemTag>? = null,

    @JsonProperty
    val detail: String? = null,

    @JsonProperty
    var documentation: MarkupContent? = null,

    @JsonProperty
    val deprecated: Boolean? = null,

    @JsonProperty
    val preselect: Boolean? = null,

    @JsonProperty
    var sortText: String? = null,

    @JsonProperty
    val filterText: String? = null,

    @JsonProperty
    var insertText: String? = null,

    @JsonProperty
    var insertTextFormat: InsertTextFormat? = null,

    @JsonProperty
    val insertTextMode: InsertTextMode? = null,

    @JsonProperty
    var textEdit: (TextEdit /* | InsertReplaceEdit?*/)? = null,

    @JsonProperty
    val textEditText: String? = null,

    @JsonProperty
    var additionalTextEdits: Array<TextEdit>? = null,

    @JsonProperty
    val commitCharacters: Array<String>? = null,

    @JsonProperty
    val command: Command? = null,

    @JsonProperty
    val data: CompletionItemData = CompletionItemData(),
)