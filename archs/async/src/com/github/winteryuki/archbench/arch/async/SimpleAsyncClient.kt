package com.github.winteryuki.archbench.arch.async

import com.github.winteryuki.archbench.arch.Client
import com.github.winteryuki.archbench.arch.ClientResponseHandler
import com.github.winteryuki.archbench.lib.Endpoint
import com.github.winteryuki.archbench.lib.encodeDelimitedToBuffer
import com.github.winteryuki.archbench.lib.inetSocketAddress
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.serializer
import mu.KLoggable
import mu.KLogger
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousSocketChannel
import java.nio.channels.CompletionHandler
import java.util.concurrent.atomic.AtomicInteger

class SimpleAsyncClient<Request, Response>(
    private val endpoint: Endpoint,
    private val requestSerializer: SerializationStrategy<Request>,
    private val responseDeserializer: DeserializationStrategy<Response>,
    private val handler: ClientResponseHandler<Response>
) : Client<Request> {
    private val nResponsesInProgress = AtomicInteger(0)
    private val socketChannel = AsynchronousSocketChannel.open()
    private val connected by lazy { socketChannel.connect(endpoint.inetSocketAddress) }

    @Suppress("UnnecessaryOptInAnnotation")
    @OptIn(ExperimentalSerializationApi::class)
    override fun sendRequest(request: Request) {
        connected.get()
        val buffer = ProtoBuf.encodeDelimitedToBuffer(requestSerializer, request)
        logger.info { "Start sending request (totalSize=${buffer.capacity()},endpoint=$endpoint)" }
        val context = RequestContext(buffer)
        socketChannel.write(buffer, context, RequestHandler())
    }

    private class RequestContext(
        val requestBuffer: ByteBuffer
    )

    private inner class RequestHandler : CompletionHandler<Int, RequestContext> {
        override fun completed(result: Int, attachment: RequestContext) = with(attachment) {
            if (requestBuffer.hasRemaining()) {
                socketChannel.write(requestBuffer, attachment, this@RequestHandler)
                return@with
            }
            logger.info { "Request sent (totalSize=${requestBuffer.position()})" }
            val buffer = ByteBuffer.allocate(Int.SIZE_BYTES)
            val context = ResponseInitContext(buffer)
            val nInProgress = nResponsesInProgress.getAndIncrement()
            if (nInProgress == 0) {
                socketChannel.read(buffer, context, ResponseInitHandler())
            }
        }

        override fun failed(exc: Throwable, attachment: RequestContext) {
            logger.error(exc) { "Failed to write request to $endpoint" }
        }
    }

    private class ResponseInitContext(
        val responseSizeBuffer: ByteBuffer
    )

    private inner class ResponseInitHandler : CompletionHandler<Int, ResponseInitContext> {
        override fun completed(result: Int, attachment: ResponseInitContext) = with(attachment) {
            require(result != -1) { NOT_RECEIVED_ALL }
            if (responseSizeBuffer.hasRemaining()) {
                socketChannel.read(responseSizeBuffer, attachment, this@ResponseInitHandler)
                return@with
            }
            responseSizeBuffer.flip()
            val size = responseSizeBuffer.int
            logger.info { "Response size received (size=$size)" }
            responseSizeBuffer.clear()

            val buffer = ByteBuffer.allocate(size)
            val context = ResponseContext(attachment, this@ResponseInitHandler, buffer)
            socketChannel.read(buffer, context, ResponseHandler())
        }

        override fun failed(exc: Throwable, attachment: ResponseInitContext) {
            logger.error(exc) { "Failed to read response size" }
        }
    }

    private inner class ResponseContext(
        val initContext: ResponseInitContext,
        val initHandler: ResponseInitHandler,
        val responseBuffer: ByteBuffer,
    )

    private inner class ResponseHandler : CompletionHandler<Int, ResponseContext> {
        @Suppress("UnnecessaryOptInAnnotation")
        @OptIn(ExperimentalSerializationApi::class)
        override fun completed(result: Int, attachment: ResponseContext) = with(attachment) {
            require(result != -1) { NOT_RECEIVED_ALL }
            if (responseBuffer.hasRemaining()) {
                socketChannel.read(responseBuffer, attachment, this@ResponseHandler)
                return@with
            }
            responseBuffer.flip()
            val response = ProtoBuf.decodeFromByteArray(responseDeserializer, responseBuffer.array())
            val context = ClientResponseHandler.HandlingContext(endpoint)
            with(handler) { context.handleResponse(response) }

            val nRequestsRemaining = nResponsesInProgress.decrementAndGet()
            if (nRequestsRemaining != 0) {
                socketChannel.read(initContext.responseSizeBuffer, initContext, initHandler)
            }
        }

        override fun failed(exc: Throwable, attachment: ResponseContext) {
            logger.error(exc) { "Failed to read response from $endpoint" }
        }
    }

    override fun close() {
        socketChannel.close()
    }

    companion object : KLoggable {
        private const val NOT_RECEIVED_ALL = "All responses should be received before socket cancellation"

        override val logger: KLogger = logger()

        inline operator fun <reified Request, reified Response> invoke(
            endpoint: Endpoint,
            handler: ClientResponseHandler<Response>
        ): SimpleAsyncClient<Request, Response> =
            SimpleAsyncClient(endpoint, serializer(), serializer(), handler)
    }
}
