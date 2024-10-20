package com.mclaughlinconnor.ijInspector.rpc

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.net.ServerSocket
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue

class ConnectionManager {
    private val connections = mutableListOf<Connection>()
    private lateinit var serverSocket: ServerSocket
    private var isRunning = false
    private var serverJob: Job? = null
    private val connectionQueue: BlockingQueue<Connection> = LinkedBlockingQueue()

    var running: Boolean = false
        get() = isRunning

    fun start(port: Int) {
        serverSocket = ServerSocket(port)
        isRunning = true
        println("Server started on port $port")
        serverJob = CoroutineScope(Dispatchers.IO).launch {
            while (isRunning) {
                val socket = serverSocket.accept()
                println("New connection accepted")
                val connection = Connection(socket)
                connections.add(connection)
                connection.start()
                connectionQueue.put(connection)
            }
        }
    }

    fun nextConnection(): Connection? {
        return if (isRunning) {
            connectionQueue.take()
        } else {
            null
        }
    }

    fun stop() {
        isRunning = false
        connections.forEach { it.close() }
        serverSocket.close()
        serverJob?.cancel()
    }

    fun broadcastMessage(message: String) {
        connections.forEach { it.write(message) }
    }

    companion object {
        private val instance = ConnectionManager()

        fun getInstance() = instance
    }
}

