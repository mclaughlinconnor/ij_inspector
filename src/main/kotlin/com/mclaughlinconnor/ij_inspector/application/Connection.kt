package com.mclaughlinconnor.ij_inspector.application

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.BufferedInputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue

class Connection {
    private lateinit var myInputStream: InputStream
    private lateinit var myOutputStream: OutputStream
    private lateinit var myServer: ServerSocket
    private lateinit var mySocket: Socket
    private val messageQueue: BlockingQueue<String> = LinkedBlockingQueue()
    private val readerScope = CoroutineScope(Dispatchers.IO)
    private var isRunning = false

    @Suppress("unused")
    fun close() {
        isRunning = false
        readerScope.cancel()
        mySocket.close()
        myServer.close()
    }

    fun init() {
        myServer = ServerSocket(2517)
        println("Waiting for connection...")
        mySocket = myServer.accept()
        myOutputStream = mySocket.getOutputStream()
        myInputStream = BufferedInputStream(mySocket.getInputStream())
        isRunning = true
        startMessageReader()
    }

    fun nextMessage(): String? {
        return if (isRunning) {
            messageQueue.take()
        } else {
            null
        }
    }

    fun write(message: String) {
        myOutputStream.write(message.toByteArray())
        myOutputStream.flush()
    }

    private fun startMessageReader() {
        readerScope.launch {
            while (isRunning) {
                val message = readMessage(myInputStream)
                messageQueue.put(message)
            }
        }
    }

    private fun readMessage(reader: InputStream): String {
        val header = ByteArray(8192)

        val r: Int = '\r'.code
        val n: Int = '\n'.code
        var lastDivider = n
        var dividerLength = 0
        var b: Int
        var index = 0
        while (dividerLength != 4) {
            b = reader.read()

            if (b == n || b == r) {
                if (b == lastDivider) {
                    dividerLength = 0
                    lastDivider = n
                    index = 0
                } else {
                    lastDivider = b
                    ++dividerLength
                }
            } else {
                header[index] = b.toByte()
                index++
            }
        }

        val contentLengthBytes = header.sliceArray("Content-Length: ".length..<index)
        val contentLength = String(contentLengthBytes).toInt()

        val body = ByteArray(contentLength)
        reader.read(body, 0, contentLength)
        reader.read() // read the trailing newline

        return String(body)
    }

    companion object {
        private val instance = Connection()

        fun getInstance() = instance
    }
}