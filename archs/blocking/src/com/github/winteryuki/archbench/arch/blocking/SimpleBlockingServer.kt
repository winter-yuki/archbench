package com.github.winteryuki.archbench.arch.blocking

import com.github.winteryuki.archbench.arch.Server
import com.github.winteryuki.archbench.arch.ServerRequestHandler
import com.github.winteryuki.archbench.arch.ServerTimeLogger
import com.github.winteryuki.archbench.lib.Port
import com.github.winteryuki.archbench.lib.decodeDelimitedFromStream
import com.github.winteryuki.archbench.lib.encodeDelimitedToStream
import com.github.winteryuki.archbench.lib.ip
import com.github.winteryuki.archbench.lib.submitCatching
import com.github.winteryuki.archbench.lib.use
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.serializer
import mu.KLoggable
import java.io.IOException
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.DurationUnit
import kotlin.time.toDuration

class SimpleBlockingServer<Request, Response>(
    port: Port,
    private val requestDeserializer: DeserializationStrategy<Request>,
    private val responseSerializer: SerializationStrategy<Response>,
    private val timeLogger: ServerTimeLogger = ServerTimeLogger(),
    private val requestHandler: ServerRequestHandler<Request, Response>
) : Server {
    private val connectionsPool = Executors.newCachedThreadPool()
    private val serverSocket = ServerSocket(port.v)
    private var serverThread: Thread? = null
    private val id = nRunners.getAndIncrement()

    override fun start() {
        serverThread?.let { error("Unable to start server $id twice") }
        logger.info { "Starting server $id" }
        serverThread = Thread(::run, "${SimpleBlockingServer::class.simpleName}-$id")
        serverThread?.start()
    }

    private fun run() = serverSocket.use { serverSocket ->
        while (!serverSocket.isClosed && !Thread.currentThread().isInterrupted) {
            try {
                logger.info { "Accepting clients" }
                val socket = serverSocket.accept()
                logger.info { "Client accepted" }
                val worker =
                    Worker(socket, requestDeserializer, responseSerializer, timeLogger, requestHandler)
                connectionsPool.submit(worker)
            } catch (e: SocketException) {
                if (serverSocket.isClosed) break
                logger.error(e) { "Server exception" }
            } catch (e: Exception) {
                logger.error(e) { "Something unexpected happened" }
            }
        }
    }

    override fun awaitTermination() {
        logger.info { "Awaiting termination of server $id" }
        serverThread?.join()
    }

    override fun close() {
        logger.info { "Closing server $id" }
        serverThread?.interrupt()
        connectionsPool.shutdown()
        serverSocket.close()
    }

    companion object : KLoggable {
        private val nRunners = AtomicInteger(0)
        override val logger = logger()

        inline operator fun <reified Request, reified Response> invoke(
            port: Port,
            timeLogger: ServerTimeLogger = ServerTimeLogger(),
            requestHandler: ServerRequestHandler<Request, Response>
        ) = SimpleBlockingServer(port, serializer(), serializer(), timeLogger, requestHandler)
    }
}

private class Worker<Request, Response>(
    private val socket: Socket,
    private val requestDeserializer: DeserializationStrategy<Request>,
    private val responseSerializer: SerializationStrategy<Response>,
    private val timeLogger: ServerTimeLogger,
    private val requestHandler: ServerRequestHandler<Request, Response>
) : Runnable {
    private val writePool = Executors.newSingleThreadExecutor()

    @Suppress("UnnecessaryOptInAnnotation")
    @OptIn(ExperimentalSerializationApi::class)
    override fun run(): Unit = socket.use(onFinally = { writePool.shutdown() }) { socket ->
        logger.info { "Client connected" }
        while (!socket.isClosed && !Thread.currentThread().isInterrupted) {
            try {
                val request = ProtoBuf.decodeDelimitedFromStream(requestDeserializer, socket.getInputStream()) ?: break
                logger.info { "Request received" }
                val ctx = ServerRequestHandler.HandlingContext(clientIp = socket.inetAddress.ip)
                with(requestHandler) {
                    val handlingTimeStart = System.currentTimeMillis()
                    ctx.handleRequest(request) { response ->
                        val handlingTimeElapsed = System.currentTimeMillis() - handlingTimeStart
                        timeLogger.logRequestProcessingDuration(handlingTimeElapsed.toDuration(DurationUnit.MILLISECONDS))
                        writePool.submitCatching(onException = { logger.error(it) { "Failed sending response" } }) {
                            ProtoBuf.encodeDelimitedToStream(responseSerializer, response, socket.getOutputStream())
                        }
                    }
                }
            } catch (e: SocketException) {
                if (socket.isClosed) break
                logger.error(e) {}
            } catch (e: IOException) {
                logger.error(e) {}
            } catch (e: Exception) {
                logger.error(e) {}
                break
            }
        }
    }

    companion object : KLoggable {
        override val logger = logger()
    }
}
