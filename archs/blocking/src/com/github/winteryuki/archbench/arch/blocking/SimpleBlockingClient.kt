package com.github.winteryuki.archbench.arch.blocking

import com.github.winteryuki.archbench.arch.Client
import com.github.winteryuki.archbench.arch.ClientResponseHandler
import com.github.winteryuki.archbench.lib.Endpoint
import com.github.winteryuki.archbench.lib.decodeDelimitedFromStream
import com.github.winteryuki.archbench.lib.encodeDelimitedToStream
import com.github.winteryuki.archbench.lib.submitCatching
import com.github.winteryuki.archbench.lib.toSocket
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.serializer
import mu.KLoggable
import mu.KLogger
import java.util.concurrent.Executors

class SimpleBlockingClient<Request, Response>(
    private val endpoint: Endpoint,
    private val requestSerializer: SerializationStrategy<Request>,
    private val responseDeserializer: DeserializationStrategy<Response>,
    private val handler: ClientResponseHandler<Response>
) : Client<Request> {
    private val lazySocket = lazy { endpoint.toSocket() } // Postpone connection establishment until first request
    private val socket by lazySocket
    private val readPool = Executors.newSingleThreadExecutor()
    private val writePool = Executors.newSingleThreadExecutor()

    @Suppress("UnnecessaryOptInAnnotation")
    @OptIn(ExperimentalSerializationApi::class)
    override fun sendRequest(request: Request) {
        writePool.submitCatching(onException = { if (!socket.isClosed) logger.error(it) { "Failed to send request" } }) {
            ProtoBuf.encodeDelimitedToStream(requestSerializer, request, socket.getOutputStream())
            logger.info { "Request sent" }
            readPool.submitCatching(onException = { if (!socket.isClosed) logger.error(it) { "Failed to receive response" } }) reading@{
                val response =
                    ProtoBuf.decodeDelimitedFromStream(responseDeserializer, socket.getInputStream()) ?: return@reading
                logger.info { "Response received" }
                with(handler) {
                    ClientResponseHandler.HandlingContext(endpoint).handleResponse(response)
                }
            }
        }
    }

    override fun close() {
        readPool.shutdown()
        writePool.shutdown()
        if (lazySocket.isInitialized()) {
            socket.close()
        }
    }

    companion object : KLoggable {
        override val logger: KLogger = logger()

        inline operator fun <reified Request, reified Response> invoke(
            endpoint: Endpoint,
            handler: ClientResponseHandler<Response>
        ) = SimpleBlockingClient<Request, _>(endpoint, serializer(), serializer(), handler)
    }
}
