package com.mclaughlinconnor.ijInspector.lsp

class RenameFile(
    oldUri: DocumentUri,
    newUri: DocumentUri,
    options: RenameFileOptions? = null,
) : AbstractTextDocumentEdit(kind = "rename", oldUri = oldUri, newUri = newUri, options = options)