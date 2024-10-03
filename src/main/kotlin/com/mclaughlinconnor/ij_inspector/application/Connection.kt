package com.mclaughlinconnor.ij_inspector.application

import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket

class Connection {
    private lateinit var myInputStream: InputStream
    private lateinit var myOutputStream: OutputStream
    private var myReader: BufferedReader? = null
    private lateinit var myServer: ServerSocket
    private lateinit var mySocket: Socket

    private fun getReader(): BufferedReader {
        return myReader ?: BufferedReader(InputStreamReader(myInputStream))
    }

    fun write(message: String) {
        myOutputStream.write(message.toByteArray())
    }

    fun init() {
        myServer = ServerSocket(2517)
        println("Waiting for connection...")
        mySocket = myServer.accept()
        myOutputStream = mySocket.getOutputStream()
        myInputStream = mySocket.getInputStream()
    }

    fun nextMessage(): String {
        val reader = getReader()

        val header = StringBuilder()
        val body = StringBuilder()
        var dividerLen = 0
        var prevDivider = '\u0000'

        while (dividerLen < 4) {
            val c = reader.read()
            val char = c.toChar()
            if ((char == '\n' || char == '\r') && char != prevDivider) {
                dividerLen++
                prevDivider = char
            } else {
                header.append(char)
                dividerLen = 0
                prevDivider = '\u0000'
            }
        }

        val contentLengthBytes = header.substring("Content-Length: ".length)
        val contentLength = contentLengthBytes.toInt()

        for (i in 0..contentLength) {
            val c = reader.read()
            body.append(c.toChar())
        }

        return body.toString()
    }

    companion object {
        private val instance = Connection()

        fun getInstance() = instance
    }
}