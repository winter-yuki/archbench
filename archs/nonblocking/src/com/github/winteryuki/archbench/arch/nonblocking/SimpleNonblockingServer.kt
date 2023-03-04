package com.github.winteryuki.archbench.arch.nonblocking

import com.github.winteryuki.archbench.arch.AbstractServer
import com.github.winteryuki.archbench.arch.ServerRequestHandler
import com.github.winteryuki.archbench.arch.ServerTimeLogger
import com.github.winteryuki.archbench.lib.IpAddress
import com.github.winteryuki.archbench.lib.Port
import com.github.winteryuki.archbench.lib.closeMany
import com.github.winteryuki.archbench.lib.encodeDelimitedToBuffer
import com.github.winteryuki.archbench.lib.inetSocketAddress
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.serializer
import mu.KLoggable
import mu.KLogger
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.SelectionKey
import java.nio.channels.Selector
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.time.DurationUnit
import kotlin.time.toDuration

class SimpleNonblockingServer<Request, Response>(
    port: Port,
    private val requestDeserializer: DeserializationStrategy<Request>,
    private val responseSerializer: SerializationStrategy<Response>,
    private val timeLogger: ServerTimeLogger = ServerTimeLogger(),
    private val requestHandler: ServerRequestHandler<Request, Response>
) : AbstractServer(SimpleNonblockingServer::class) {
    private val serverChannel = ServerSocketChannel.open().bind(port.inetSocketAddress)

    private val readChannelsToRegister = ConcurrentLinkedQueue<SocketChannel>()
    private val readSelector = Selector.open()
    private val readSelectionThread = Thread(::runReadSelection)

    private val writeChannelsToRegister = ConcurrentLinkedQueue<SelectionKey>()
    private val writeSelector = Selector.open()
    private val writeSelectionThread = Thread(::runWriteSelection)

    private class ClientContext(
        @Volatile
        var request: RequestContext,
        val response: ResponseContext,
        @Volatile
        var writeKey: SelectionKey? = null,
    )

    private sealed interface RequestContext {
        class Init(val sizeBuffer: ByteBuffer) : RequestContext
        class Message(val initContext: Init, val messageBuffer: ByteBuffer) : RequestContext
    }

    private class ResponseContext(val queue: Queue<ByteBuffer> = ConcurrentLinkedQueue())

    override fun run() {
        readSelectionThread.start()
        writeSelectionThread.start()
        while (serverChannel.isOpen && !Thread.currentThread().isInterrupted) {
            val channel = serverChannel.accept().apply { configureBlocking(false) }
            readChannelsToRegister.add(channel)
            readSelector.wakeup()
        }
    }

    private fun runReadSelection() {
        while (!Thread.currentThread().isInterrupted && readSelector.isOpen) {
            readSelector.select().let { nNewReadyChannels ->
                logger.info { "Selected new ready to read channels: $nNewReadyChannels" }
            }
            val keyIter = readSelector.selectedKeys().iterator()
            while (keyIter.hasNext()) {
                val key = keyIter.next()
                if (key.isReadable) {
                    when (val context = key.clientContext.request) {
                        is RequestContext.Init -> readInit(context, key)
                        is RequestContext.Message -> readMessage(context, key)
                    }
                } else {
                    logger.warn { "Reading key is not ready to read" }
                }
                keyIter.remove()
            }
            while (readChannelsToRegister.isNotEmpty()) {
                val channel = readChannelsToRegister.remove()
                requireNotNull(channel)
                val context = ClientContext(
                    request = RequestContext.Init(sizeBuffer = ByteBuffer.allocate(Int.SIZE_BYTES)),
                    response = ResponseContext(),
                )
                channel.register(readSelector, SelectionKey.OP_READ, context)
            }
        }
    }

    private fun readInit(
        context: RequestContext.Init,
        key: SelectionKey
    ) = with(context) {
        key.socketChannel.read(sizeBuffer).let {
            if (it == -1) {
                logger.info { "Client disconnected" }
                key.clientContext.writeKey
                    ?.cancel()
                    ?: logger.error { "Failed to cancel client key: ${key.socketChannel.remoteAddress}" }
                return@with
            }
        }
        if (sizeBuffer.hasRemaining()) return@with
        sizeBuffer.flip()
        val size = sizeBuffer.int
        sizeBuffer.clear()
        key.clientContext.request = RequestContext.Message(
            initContext = context,
            messageBuffer = ByteBuffer.allocate(size)
        )
    }

    @Suppress("UnnecessaryOptInAnnotation")
    @OptIn(ExperimentalSerializationApi::class)
    private fun readMessage(
        context: RequestContext.Message,
        key: SelectionKey
    ) = with(context) {
        val result = key.socketChannel.read(messageBuffer)
        require(result != -1) { "Unable to read whole message" }
        if (messageBuffer.hasRemaining()) return@with
        messageBuffer.flip()
        val request = ProtoBuf.decodeFromByteArray(requestDeserializer, messageBuffer.array())
        val ip = IpAddress(key.socketChannel.remoteAddress as InetSocketAddress)
        val handlingContext = ServerRequestHandler.HandlingContext(ip)
        val startTimeMs = System.currentTimeMillis()
        with(requestHandler) {
            handlingContext.handleRequest(request) { response ->
                val elapsed = System.currentTimeMillis() - startTimeMs
                timeLogger.logRequestProcessingDuration(elapsed.toDuration(DurationUnit.MILLISECONDS))
                val buffer = ProtoBuf.encodeDelimitedToBuffer(responseSerializer, response)
                key.clientContext.response.queue.add(buffer)
                writeChannelsToRegister.add(key)
                writeSelector.wakeup()
            }
        }
        key.clientContext.request = context.initContext
    }

    private fun runWriteSelection() {
        while (!Thread.currentThread().isInterrupted && writeSelector.isOpen) {
            writeSelector.select().let { nNewReadyChannels ->
                logger.info { "Selected new ready to write channels: $nNewReadyChannels" }
            }
            val keyIter = writeSelector.selectedKeys().iterator()
            while (keyIter.hasNext()) {
                val key = keyIter.next()
                if (key.isWritable) {
                    val queue = key.clientContext.response.queue
                    while (queue.isNotEmpty()) {
                        val buffer = queue.element()
                        key.socketChannel.write(buffer)
                        if (!buffer.hasRemaining()) {
                            queue.remove()
                        }
                        if (queue.isEmpty()) {
                            // Optimization: unregister write channel if no data is available to write
                            key.socketChannel.register(writeSelector, 0, key.clientContext)
                        }
                    }
                } else {
                    logger.warn { "Selected key is not writable" }
                }
                keyIter.remove()
            }
            while (writeChannelsToRegister.isNotEmpty()) {
                val readKey = writeChannelsToRegister.remove()
                val key = readKey.socketChannel.register(writeSelector, SelectionKey.OP_WRITE, readKey.clientContext)
                readKey.clientContext.writeKey = key
            }
        }
    }

    override fun close() {
        super.close()
        closeMany(serverChannel, readSelector, writeSelector)
        readSelectionThread.run {
            interrupt()
            join()
        }
        writeSelectionThread.run {
            interrupt()
            join()
        }
    }

    private val SelectionKey.clientContext: ClientContext
        get() = attachment() as ClientContext

    private val SelectionKey.socketChannel: SocketChannel
        get() = channel() as SocketChannel

    companion object : KLoggable {
        override val logger: KLogger = logger()

        inline operator fun <reified Request, reified Response> invoke(
            port: Port,
            timeLogger: ServerTimeLogger = ServerTimeLogger(),
            requestHandler: ServerRequestHandler<Request, Response>,
        ) = SimpleNonblockingServer(port, serializer(), serializer(), timeLogger, requestHandler)
    }
}
