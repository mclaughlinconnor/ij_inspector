package com.mclaughlinconnor.ijInspector.lsp

class CreateFileOptions(
    overwrite: Boolean?,
    ignoreIfExists: Boolean?,
) : AbstractFileEditOptions(overwrite = overwrite, ignoreIfExists = ignoreIfExists)