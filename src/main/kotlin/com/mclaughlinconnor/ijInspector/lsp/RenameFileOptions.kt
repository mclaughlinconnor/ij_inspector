package com.mclaughlinconnor.ijInspector.lsp

class RenameFileOptions(
    overwrite: Boolean?,
    ignoreIfExists: Boolean?,
) : AbstractFileEditOptions(overwrite = overwrite, ignoreIfExists = ignoreIfExists)