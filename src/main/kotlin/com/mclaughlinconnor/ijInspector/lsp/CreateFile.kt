package com.mclaughlinconnor.ijInspector.lsp

class CreateFile(
    uri: DocumentUri, options: CreateFileOptions?,
) : AbstractTextDocumentEdit(kind = "create", uri = uri, options = options)