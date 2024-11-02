package com.mclaughlinconnor.ijInspector.lsp

import com.fasterxml.jackson.annotation.JsonProperty

abstract class AbstractFileEditOptions(
    @JsonProperty
    val overwrite: Boolean? = null,

    @JsonProperty
    val ignoreIfExists: Boolean? = null,

    @JsonProperty
    val recursive: Boolean? = null,
)