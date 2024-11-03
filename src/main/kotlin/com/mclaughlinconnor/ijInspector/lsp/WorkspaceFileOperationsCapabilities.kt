package com.mclaughlinconnor.ijInspector.lsp

import com.fasterxml.jackson.annotation.JsonProperty

class WorkspaceFileOperationsCapabilities(
    @JsonProperty
    val didCreate: FileOperationRegistrationOptions? = null,

    @JsonProperty
    val willCreate: FileOperationRegistrationOptions? = null,

    @JsonProperty
    val didRename: FileOperationRegistrationOptions? = null,

    @JsonProperty
    val willRename: FileOperationRegistrationOptions? = null,

    @JsonProperty
    val didDelete: FileOperationRegistrationOptions? = null,

    @JsonProperty
    val willDelete: FileOperationRegistrationOptions? = null,
)