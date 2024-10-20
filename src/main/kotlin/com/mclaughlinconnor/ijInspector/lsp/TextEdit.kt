package com.mclaughlinconnor.ijInspector.lsp

import com.fasterxml.jackson.annotation.JsonProperty

class TextEdit(@JsonProperty val range: Range, @JsonProperty var newText: String)
