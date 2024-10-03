package com.mclaughlinconnor.ij_inspector.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.mclaughlinconnor.ij_inspector.application.lsp.Response

class MessageFactory {
    private var objectMapper = ObjectMapper()

    fun newMessage(data: Response): String {
        val text = objectMapper.writeValueAsString(data)

        val s = StringBuilder()
        s.append("Content-Length: ")
        s.append(text.length)
        s.append("\r\n\r\n")
        s.append(text)

        return s.toString()
    }
}