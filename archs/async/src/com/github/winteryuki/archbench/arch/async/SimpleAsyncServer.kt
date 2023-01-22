package com.github.winteryuki.archbench.arch.async

import com.github.winteryuki.archbench.arch.AbstractServer
import com.github.winteryuki.archbench.arch.ServerRequestHandler
import com.github.winteryuki.archbench.arch.ServerTimeLogger
import com.github.winteryuki.archbench.lib.IpAddress
import com.github.winteryuki.archbench.lib.Port
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
import java.nio.channels.AsynchronousCloseException
import java.nio.channels.AsynchronousServerSocketChannel
import java.nio.channels.AsynchronousSocketChannel
import java.nio.channels.CompletionHandler
import kotlin.time.DurationUnit
import kotlin.time.toDuration

class SimpleAsyncServer<Request, Response>(
    port: Port,
    private val requestDeserializer: DeserializationStrategy<Request>,
    private val responseSerializer: SerializationStrategy<Response>,
    private val timeLogger: ServerTimeLogger = ServerTimeLogger(),
    private val requestHandler: ServerRequestHandler<Request, Response>
) : AbstractServer(SimpleAsyncServer::class) {
    private val serverChannel = AsynchronousServerSocketChannel.open().bind(port.inetSocketAddress)

    override fun run() {
        serverChannel.accept(Unit, ConnectionHandler())
    }

    override fun close() {
        super.close()
        serverChannel.close()
    }

    private inner class ConnectionHandler : CompletionHandler<AsynchronousSocketChannel, Unit> {
        override fun completed(result: AsynchronousSocketChannel, attachment: Unit) {
            serverChannel.accept(attachment, this)
            val buffer = ByteBuffer.allocate(Int.SIZE_BYTES)
            val context = RequestInitContext(channel = result, messageSizeBuffer = buffer)
            result.read(buffer, context, RequestInitHandler())
        }

        override fun failed(exc: Throwable, attachment: Unit) {
            if (exc is AsynchronousCloseException) {
                logger.info { "Connection closed $exc" }
            } else {
                logger.error(exc) { "Accept failed" }
            }
        }
    }

    private class RequestInitContext(
        val channel: AsynchronousSocketChannel,
        val messageSizeBuffer: ByteBuffer,
    )

    private inner class RequestInitHandler : CompletionHandler<Int, RequestInitContext> {
        override fun completed(result: Int, attachment: RequestInitContext) = with(attachment) {
            if (result == -1) {
                logger.info { "Connection closed with ${channel.remoteAddress}" }
                return@with
            }
            if (messageSizeBuffer.hasRemaining()) {
                channel.read(messageSizeBuffer, attachment, this@RequestInitHandler)
                return@with
            }

            messageSizeBuffer.flip()
            val size = messageSizeBuffer.int
            logger.info { "Request size received (size=$size)" }
            messageSizeBuffer.clear()

            val buffer = ByteBuffer.allocate(size)
            val context = RequestContext(attachment, this@RequestInitHandler, channel, messageBuffer = buffer)
            channel.read(buffer, context, RequestHandler())
        }

        override fun failed(exc: Throwable, attachment: RequestInitContext) {
            logger.error(exc) { "Message size read failed for ${attachment.channel.remoteAddress}" }
        }
    }

    private inner class RequestContext(
        val initContext: RequestInitContext,
        val initHandler: RequestInitHandler,
        val channel: AsynchronousSocketChannel,
        val messageBuffer: ByteBuffer,
    )

    private inner class RequestHandler : CompletionHandler<Int, RequestContext> {
        @Suppress("UnnecessaryOptInAnnotation")
        @OptIn(ExperimentalSerializationApi::class)
        override fun completed(result: Int, attachment: RequestContext) = with(attachment) {
            require(result != -1) { "Channel should not be closed before complete message received" }
            if (messageBuffer.hasRemaining()) {
                channel.read(messageBuffer, attachment, this@RequestHandler)
                return@with
            }
            messageBuffer.flip()
            val request = ProtoBuf.decodeFromByteArray(requestDeserializer, messageBuffer.array())
            val ip = IpAddress(channel.remoteAddress as InetSocketAddress)
            val context = ServerRequestHandler.HandlingContext(ip)
            val startTimeMs = System.currentTimeMillis()
            with(requestHandler) {
                context.handleRequest(request) { response ->
                    val elapsed = (System.currentTimeMillis() - startTimeMs).toDuration(DurationUnit.MILLISECONDS)
                    timeLogger.logRequestProcessingDuration(elapsed)

                    val buffer = ProtoBuf.encodeDelimitedToBuffer(responseSerializer, response)
                    logger.info { "Sending response (totalSize=${buffer.limit()})" }
                    val responseContext = ResponseContext(responseBuffer = buffer, channel = channel)
                    // TODO request multiplication (send responses sequentially)
                    channel.write(buffer, responseContext, ResponseHandler())
                }
            }
            channel.read(initContext.messageSizeBuffer, initContext, initHandler)
        }

        override fun failed(exc: Throwable, attachment: RequestContext) {
            logger.error(exc) { "Message read failed for ${attachment.channel.remoteAddress}" }
        }
    }

    private class ResponseContext(
        val channel: AsynchronousSocketChannel,
        val responseBuffer: ByteBuffer,
    )

    private inner class ResponseHandler : CompletionHandler<Int, ResponseContext> {
        override fun completed(result: Int, attachment: ResponseContext) = with(attachment) {
            if (responseBuffer.hasRemaining()) {
                channel.write(responseBuffer, attachment, this@ResponseHandler)
            } else {
                logger.info { "Response sent (totalSize=${responseBuffer.position()})" }
            }
        }

        override fun failed(exc: Throwable, attachment: ResponseContext) {
            logger.error(exc) { "Message write failed for ${attachment.channel.remoteAddress}" }
        }
    }

    companion object : KLoggable {
        override val logger: KLogger = logger()

        inline operator fun <reified Request, reified Response> invoke(
            port: Port,
            timeLogger: ServerTimeLogger = ServerTimeLogger(),
            requestHandler: ServerRequestHandler<Request, Response>
        ): SimpleAsyncServer<Request, Response> =
            SimpleAsyncServer(port, serializer(), serializer(), timeLogger, requestHandler)
    }
}
