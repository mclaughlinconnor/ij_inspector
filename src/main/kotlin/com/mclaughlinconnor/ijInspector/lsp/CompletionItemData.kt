package com.mclaughlinconnor.ijInspector.lsp

import com.fasterxml.jackson.annotation.JsonProperty

class CompletionItemData(
    @JsonProperty val filePath: String = "",
    @JsonProperty val position: Position = Position(0, 0),
    @JsonProperty val triggerCharacter: Char = '\u0000'
)
