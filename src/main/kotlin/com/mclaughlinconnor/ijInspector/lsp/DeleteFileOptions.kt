package com.mclaughlinconnor.ijInspector.lsp

class DeleteFileOptions(
    recursive: Boolean?,
    ignoreIfExists: Boolean?,
) : AbstractFileEditOptions(overwrite = recursive, ignoreIfExists = ignoreIfExists)