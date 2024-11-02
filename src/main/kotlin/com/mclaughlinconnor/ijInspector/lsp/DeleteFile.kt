package com.mclaughlinconnor.ijInspector.lsp

class DeleteFile(
    uri: DocumentUri,
    options: DeleteFileOptions?,
) : AbstractTextDocumentEdit(kind = "delete", uri = uri, options = options)