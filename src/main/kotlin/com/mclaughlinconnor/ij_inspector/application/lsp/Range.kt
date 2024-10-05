package com.mclaughlinconnor.ij_inspector.application.lsp

import com.fasterxml.jackson.annotation.JsonProperty

class Range(@JsonProperty val start: Position, @JsonProperty var end: Position)
