package com.mclaughlinconnor.ijInspector.lsp

import com.fasterxml.jackson.annotation.JsonProperty

class ServerCapabilities(
    @JsonProperty
    val positionEncoding: Any? = null,

    @JsonProperty
    val textDocumentSync: TextDocumentSyncKind? = TextDocumentSyncKindEnum.Full,

    @JsonProperty
    val notebookDocumentSync: Any? = null,

    @JsonProperty
    val completionProvider: CompletionOptions? = null,

    @JsonProperty
    val hoverProvider: Boolean? = null,

    @JsonProperty
    val signatureHelpProvider: Any? = null,

    @JsonProperty
    val declarationProvider: Boolean? = null,

    @JsonProperty
    val definitionProvider: Boolean? = null,

    @JsonProperty
    val typeDefinitionProvider: Boolean? = null,

    @JsonProperty
    val implementationProvider: Boolean? = null,

    @JsonProperty
    val referencesProvider: Boolean? = null,

    @JsonProperty
    val documentHighlightProvider: Boolean? = null,

    @JsonProperty
    val documentSymbolProvider: Boolean? = null,

    @JsonProperty
    val codeActionProvider: Boolean? = null,

    @JsonProperty
    val codeLensProvider: Any? = null,

    @JsonProperty
    val documentLinkProvider: Any? = null,

    @JsonProperty
    val colorProvider: Boolean? = null,

    @JsonProperty
    val documentFormattingProvider: Boolean? = null,

    @JsonProperty
    val documentRangeFormattingProvider: Boolean? = null,

    @JsonProperty
    val documentOnTypeFormattingProvider: Any? = null,

    @JsonProperty
    val renameProvider: Boolean? = null,

    @JsonProperty
    val foldingRangeProvider: Boolean? = null,

    @JsonProperty
    val executeCommandProvider: ExecuteCommandOptions? = null,

    @JsonProperty
    val selectionRangeProvider: Boolean? = null,

    @JsonProperty
    val linkedEditingRangeProvider: Boolean? = null,

    @JsonProperty
    val callHierarchyProvider: Boolean? = null,

    @JsonProperty
    val semanticTokensProvider: Any? = null,

    @JsonProperty
    val monikerProvider: Boolean? = null,

    @JsonProperty
    val typeHierarchyProvider: Boolean? = null,

    @JsonProperty
    val inlineValueProvider: Boolean? = null,

    @JsonProperty
    val inlayHintProvider: Boolean? = null,

    @JsonProperty
    val diagnosticProvider: DiagnosticOptions? = null,

    @JsonProperty
    val workspaceSymbolProvider: Boolean? = null,

    @JsonProperty
    val workspace: WorkspaceCapabilities? = null,

    @JsonProperty
    val experimental: Any? = null,
) {
    companion object {
        val EMPTY = ServerCapabilities()
    }
}

