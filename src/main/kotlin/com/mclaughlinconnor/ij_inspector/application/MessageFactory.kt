package com.mclaughlinconnor.ij_inspector.application

import com.fasterxml.jackson.databind.ObjectMapper

class MessageFactory {
    private var objectMapper = ObjectMapper()

    fun newMessage(data: Any): String {
        val text = objectMapper.writeValueAsString(data)

        val s = StringBuilder()
        s.append("Content-Length: ")
        s.append(text.length)
        s.append("\r\n\r\n")
        s.append(text)

        return s.toString()
    }
}