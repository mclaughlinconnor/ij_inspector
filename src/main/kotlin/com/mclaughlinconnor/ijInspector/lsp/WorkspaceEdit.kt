package com.mclaughlinconnor.ijInspector.lsp

import com.fasterxml.jackson.annotation.JsonProperty

class WorkspaceEdit(
    @JsonProperty
    val changes: Map<DocumentUri, List<TextEdit>>? = null,

    @JsonProperty
    val documentChanges: Array<AbstractTextDocumentEdit>? = null,

    @JsonProperty
    val changeAnnotations: Map<String, ChangeAnnotation>? = null
)