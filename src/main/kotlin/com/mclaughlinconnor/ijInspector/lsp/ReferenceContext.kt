package com.mclaughlinconnor.ijInspector.lsp

import com.fasterxml.jackson.annotation.JsonProperty

class ReferenceContext(
    @JsonProperty
    val includeDeclaration: Boolean = false
)