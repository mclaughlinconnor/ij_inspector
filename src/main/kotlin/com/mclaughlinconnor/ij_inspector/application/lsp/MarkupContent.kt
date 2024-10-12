package com.mclaughlinconnor.ij_inspector.application.lsp

import com.fasterxml.jackson.annotation.JsonProperty

class MarkupContent(@JsonProperty val kind: MarkupKind = "", @JsonProperty val value: String = "")