package com.mclaughlinconnor.ijInspector.lsp

import com.fasterxml.jackson.annotation.JsonProperty

class TextDocumentContentChangeEvent(@JsonProperty val text: String = "")