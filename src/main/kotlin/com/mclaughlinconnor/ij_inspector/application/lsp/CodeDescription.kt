package com.mclaughlinconnor.ij_inspector.application.lsp

import com.fasterxml.jackson.annotation.JsonProperty

class CodeDescription(
    @JsonProperty
    val href: String = ""
)