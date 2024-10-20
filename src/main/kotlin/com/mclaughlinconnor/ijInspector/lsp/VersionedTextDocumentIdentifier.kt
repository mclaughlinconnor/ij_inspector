package com.mclaughlinconnor.ijInspector.lsp

import com.fasterxml.jackson.annotation.JsonProperty

class VersionedTextDocumentIdentifier(@JsonProperty uri: String = "", @JsonProperty val version: Int = 0) :
    TextDocumentIdentifier(uri)