package com.mclaughlinconnor.ijInspector.rpc

import com.fasterxml.jackson.databind.ObjectMapper

class MessageFactory {
    private var objectMapper = ObjectMapper()

    fun newMessage(data: Any): String {
        val text = objectMapper.writeValueAsString(data)
        val length = text.toByteArray(Charsets.UTF_8).size

        val s = StringBuilder()
        s.append("Content-Length: ")
        s.append(length)
        s.append("\r\n\r\n")
        s.append(text)

        return s.toString()
    }
}