package com.mclaughlinconnor.ij_inspector.application.lsp

import com.fasterxml.jackson.annotation.JsonProperty

class ReferenceContext(
    @JsonProperty
    val includeDeclaration: Boolean = false
)