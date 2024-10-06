package com.mclaughlinconnor.ij_inspector.application.lsp

import com.fasterxml.jackson.annotation.JsonProperty

class VersionedTextDocumentIdentifier(@JsonProperty uri: String = "", @JsonProperty val version: Int = 0) :
    TextDocumentIdentifier(uri)