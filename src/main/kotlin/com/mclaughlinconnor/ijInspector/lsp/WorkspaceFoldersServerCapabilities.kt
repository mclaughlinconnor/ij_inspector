package com.mclaughlinconnor.ijInspector.lsp

import com.fasterxml.jackson.annotation.JsonProperty

class WorkspaceFoldersServerCapabilities(
    @JsonProperty
    val supported: Boolean?,

    @JsonProperty
    val changeNotifications: Boolean?,
)