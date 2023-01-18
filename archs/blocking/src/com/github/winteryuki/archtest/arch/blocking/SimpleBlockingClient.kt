package com.github.winteryuki.archtest.arch.blocking

import com.github.winteryuki.archtest.arch.Client
import com.github.winteryuki.archtest.arch.ClientResponseHandler
import com.github.winteryuki.archtest.lib.Endpoint
import com.github.winteryuki.archtest.lib.decodeDelimitedFromStream
import com.github.winteryuki.archtest.lib.encodeDelimitedToStream
import com.github.winteryuki.archtest.lib.submitCatching
import com.github.winteryuki.archtest.lib.toSocket
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
    private val socket = endpoint.toSocket()
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
        socket.close()
    }

    companion object : KLoggable {
        override val logger: KLogger = logger()

        inline operator fun <reified Request, reified Response> invoke(
            endpoint: Endpoint,
            handler: ClientResponseHandler<Response>
        ) = SimpleBlockingClient<Request, _>(endpoint, serializer(), serializer(), handler)
    }
}
