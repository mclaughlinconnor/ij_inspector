package com.mclaughlinconnor.ijInspector.lsp

class TextDocumentEdit(
    textDocument: TextDocumentIdentifier,
    edits: Array<TextEdit>,
) : AbstractTextDocumentEdit(textDocument = textDocument, edits = edits)