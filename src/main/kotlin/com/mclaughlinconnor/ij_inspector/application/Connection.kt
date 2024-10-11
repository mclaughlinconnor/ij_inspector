package com.mclaughlinconnor.ij_inspector.application

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.*
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue

class Connection {
    private lateinit var myInputStream: InputStream
    private lateinit var myOutputStream: OutputStream
    private var myReader: BufferedReader? = null
    private lateinit var myServer: ServerSocket
    private lateinit var mySocket: Socket
    private val messageQueue: BlockingQueue<String> = LinkedBlockingQueue()
    private val readerScope = CoroutineScope(Dispatchers.IO)
    private var isRunning = false

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

    private fun getReader(): BufferedReader {
        return myReader ?: BufferedReader(InputStreamReader(myInputStream), 8192000)
    }

    private fun startMessageReader() {
        readerScope.launch {
            val reader = getReader()
            while (isRunning) {
                val message = readMessage(reader)
                messageQueue.put(message)
            }
        }
    }

    private fun readMessage(reader: BufferedReader): String {
        // readLine removes the first \n\r pair. Remove the second pair manually.
        val header = reader.readLine()
        reader.read()
        reader.read()

        val contentLengthBytes = header.substring("Content-Length: ".length)
        val contentLength = contentLengthBytes.toInt()

        val body = CharArray(81920)
        reader.read(body, 0, contentLength)
        reader.read() // read the trailing newline

        return String(body.sliceArray(IntRange(0, contentLength)))
    }

    companion object {
        private val instance = Connection()

        fun getInstance() = instance
    }
}