package com.mclaughlinconnor.ij_inspector.application.lsp

import com.fasterxml.jackson.annotation.JsonProperty

class TextDocumentContentChangeEvent(@JsonProperty val text: String = "")