package com.mclaughlinconnor.ij_inspector.application.lsp

class DidChangeTextDocumentParams(
    val textDocument: VersionedTextDocumentIdentifier = VersionedTextDocumentIdentifier("", 0),
    val contentChanges: List<TextDocumentContentChangeEvent> = ArrayList()
)