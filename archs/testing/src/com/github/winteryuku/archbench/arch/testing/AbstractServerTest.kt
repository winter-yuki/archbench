package com.github.winteryuku.archbench.arch.testing

import com.github.winteryuki.archbench.arch.Client
import com.github.winteryuki.archbench.arch.ClientResponseHandler
import com.github.winteryuki.archbench.arch.Server
import com.github.winteryuki.archbench.arch.ServerRequestHandler
import com.github.winteryuki.archbench.lib.Endpoint
import com.github.winteryuki.archbench.lib.IpAddress
import com.github.winteryuki.archbench.lib.Latch
import com.github.winteryuki.archbench.lib.Port
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.serialization.Serializable
import mu.KLoggable
import mu.KLogger
import java.util.concurrent.Executors
import kotlin.random.Random
import kotlin.test.assertEquals

abstract class AbstractServerTest(mutexFactory: () -> Mutex? = { null }) {
    @Serializable
    data class Request(val cmd: String, val id: Int, val data: Int)

    @Serializable
    data class Response(val id: Int, val data: Int)

    lateinit var server: Server
    lateinit var client1: Client<Request>
    lateinit var client2: Client<Request>
    lateinit var client3: Client<Request>
    private val mutex1 = mutexFactory()
    private val mutex2 = mutexFactory()
    private val mutex3 = mutexFactory()
    private lateinit var latch: Latch
    private val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()

    val handler1 = ClientResponseHandler<Response> {
        logger.info { "Client 1 response received" }
        responses1 += it
        mutex1?.unlock()
        latch.countDown()
    }

    val handler2 = ClientResponseHandler<Response> {
        logger.info { "Client 2 response received" }
        responses2 += it
        mutex2?.unlock()
        latch.countDown()
    }

    val handler3 = ClientResponseHandler<Response> {
        logger.info { "Client 3 response received" }
        responses3 += it
        mutex3?.unlock()
        latch.countDown()
    }

    val serverHandler = ServerRequestHandler<Request, Response> { (cmd, id, x), responseHandler ->
        logger.info { "Handle request $id" }
        when (cmd) {
            "p" -> responseHandler.handleResponse(Response(id, x + 1))
            "m" -> responseHandler.handleResponse(Response(id, x - 1))
        }
    }

    private val responses1 = mutableListOf<Response>()
    private val responses2 = mutableListOf<Response>()
    private val responses3 = mutableListOf<Response>()

    fun close() {
        client1.close()
        client2.close()
        client3.close()
        server.close()
        responses1.clear()
        responses2.clear()
        responses3.clear()
        dispatcher.close()
    }

    fun testBasic() = runBlocking {
        val rnd = Random(42)
        val responses1 = mutableListOf<Response>()
        val responses2 = mutableListOf<Response>()
        val responses3 = mutableListOf<Response>()
        val n = 30
        latch = Latch(n)
        repeat(n) { id ->
            val (cmd, f) = when (rnd.nextInt(2)) {
                0 -> "p" to fun(x: Int): Int = x + 1
                1 -> "m" to fun(x: Int): Int = x - 1
                else -> error("Unreachable")
            }
            val x = rnd.nextInt()
            when (rnd.nextInt(3)) {
                0 -> launch(dispatcher) {
                    mutex1?.lock()
                    client1.sendRequest(Request(cmd, id, x))
                    responses1 += Response(id, f(x))
                }

                1 -> launch(dispatcher) {
                    mutex2?.lock()
                    client2.sendRequest(Request(cmd, id, x))
                    responses2 += Response(id, f(x))
                }

                2 -> launch(dispatcher) {
                    mutex3?.lock()
                    client3.sendRequest(Request(cmd, id, x))
                    responses3 += Response(id, f(x))
                }
            }
        }
        logger.info { "Awaiting latch" }
        latch.await()
        assertEquals(responses1.sortedBy { it.id }, this@AbstractServerTest.responses1.sortedBy { it.id })
        assertEquals(responses2.sortedBy { it.id }, this@AbstractServerTest.responses2.sortedBy { it.id })
        assertEquals(responses3.sortedBy { it.id }, this@AbstractServerTest.responses3.sortedBy { it.id })
        logger.info { "Test finished!" }
    }

    companion object : KLoggable {
        override val logger: KLogger = logger()
        val serverPort = Port(8082)
        val endpoint = Endpoint(IpAddress.localhost, serverPort)
    }
}
