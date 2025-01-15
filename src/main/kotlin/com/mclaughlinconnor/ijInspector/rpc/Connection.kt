package com.mclaughlinconnor.ijInspector.rpc

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.BufferedInputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue

const val MAX_HEADER_LENGTH = 32

class Connection(private val mySocket: Socket) {
    private lateinit var myInputStream: InputStream
    private lateinit var myOutputStream: OutputStream
    private val messageQueue: BlockingQueue<String> = LinkedBlockingQueue()
    private val readerScope = CoroutineScope(Dispatchers.IO)
    private var isRunning = false

    @Suppress("unused")
    fun close() {
        isRunning = false
        myInputStream.close()
        myOutputStream.close()
        mySocket.close()
        readerScope.cancel()
    }

    fun start() {
        myOutputStream = mySocket.getOutputStream()
        myInputStream = BufferedInputStream(mySocket.getInputStream())
        isRunning = true
        startMessageReader()
    }

    fun nextMessage(): String? {
        return if (isRunning && !mySocket.isClosed) {
            messageQueue.take()
        } else {
            if (mySocket.isClosed) {
                this.close()
            }
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
        val header = ArrayDeque<Byte>(MAX_HEADER_LENGTH)

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
                    header.clear()
                } else {
                    lastDivider = b
                    ++dividerLength
                }
            } else {
                header.add(b.toByte())

                if (header.size >= MAX_HEADER_LENGTH) {
                    header.removeFirst()
                } else {
                    index++
                }
            }

        }

        val contentLengthBytes: ByteArray = header
            .toArray(arrayOf<Byte>())
            .sliceArray("Content-Length: ".length..<index)
            .toByteArray()
        val contentLength = String(contentLengthBytes).toInt()

        val body = ByteArray(contentLength)
        var bytesRead = reader.read(body, 0, contentLength)
        var leftToRead = contentLength - bytesRead
        while (bytesRead < leftToRead) {
            bytesRead += reader.read(body, bytesRead, leftToRead)
            leftToRead -= bytesRead
            leftToRead = contentLength - bytesRead
        }

        return String(body)
    }
}