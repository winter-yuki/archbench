package com.github.winteryuki.archbench.runner

import com.github.winteryuki.archbench.arch.Client
import com.github.winteryuki.archbench.arch.ServerTimeLogger
import com.github.winteryuki.archbench.lib.Endpoint
import com.github.winteryuki.archbench.lib.Latch
import com.github.winteryuki.archbench.lib.sortSlowly
import com.github.winteryuki.archbench.lib.submitCatching
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import mu.KotlinLogging
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime
import kotlin.time.toDuration

/**
 * @param nElements N
 * @param nClients M
 * @param clientResponseRequestDelay \Delta
 * @param nRequestsPerClient X
 */
@Serializable
data class RunConf(
    val nElements: Int,
    val nClients: Int,
    val clientResponseRequestDelay: Duration,
    val nRequestsPerClient: Int,
    val arch: Arch,
    val endpoint: Endpoint,
    val nServerWorkerThreads: Int,
) {
    init {
        require(nElements >= 0)
        require(nClients >= 0)
        require(nRequestsPerClient >= 0)
    }
}

private data class ClientRequestInfo(
    val id: Id,
    val client: Client<BusinessRequest>,
    val iRequest: Int,
)

@OptIn(ExperimentalTime::class)
fun runBench(conf: RunConf): MetricsStorage = runBlocking {
    val rnd = Random(42)
    val array = IntArray(conf.nElements) { rnd.nextInt() }
    val metricsStorage = SimpleConcurrentMetricsStorage()
    val timeLogger = object : ServerTimeLogger {
        override fun logRequestProcessingDuration(duration: Duration) {
            metricsStorage.log(MetricTag.ServerRequestProcessingTime, duration)
        }
    }
    val pool = Executors.newFixedThreadPool(conf.nServerWorkerThreads)
    val server = conf.arch.serverFactory.create(conf.endpoint.port, timeLogger) { request, responseHandler ->
        pool.submitCatching({ e -> logger.error(e) {} }) {
            logger.info { "Processing request (size=${request.array.size})" }
            val time = measureTime {
                request.array.sortSlowly()
                val response = BusinessResponse(request.id, request.array)
                responseHandler.handleResponse(response)
            }
            metricsStorage.log(MetricTag.ServerComputingTime, time)
        }
    }
    val requestInfos = ConcurrentHashMap<Id, ClientRequestInfo>()
    val clientsFirstRequestTimeMs = mutableMapOf<Client<*>, Long>()

    fun send(client: Client<BusinessRequest>, iRequest: Int = 0) {
        val id = Id.random()
        val request = BusinessRequest(id, array)
        requestInfos[id] = ClientRequestInfo(id, client, iRequest)
        client.sendRequest(request)
    }

    val latch = Latch(conf.nClients)
    val clients = List(conf.nClients) {
        conf.arch.clientFactory.create(conf.endpoint) { response ->
            logger.info { "Response received (size=${response.array.size})" }
            val info = requestInfos.getValue(response.id)
            if (info.iRequest < conf.nRequestsPerClient) {
                launch {
                    delay(conf.clientResponseRequestDelay)
                    send(info.client, info.iRequest + 1)
                }
            } else {
                val start = clientsFirstRequestTimeMs.getValue(info.client)
                val elapsed = (System.currentTimeMillis() - start).toDuration(DurationUnit.MILLISECONDS)
                val corrected = elapsed - conf.clientResponseRequestDelay * (conf.nRequestsPerClient - 1)
                metricsStorage.log(MetricTag.ClientSingleResponseTime, corrected / conf.nRequestsPerClient)
                latch.countDown()
            }
        }
    }
    server.start()
    clients.forEach { client ->
        clientsFirstRequestTimeMs[client] = System.currentTimeMillis()
        send(client)
    }

    latch.await()
    withContext(Dispatchers.IO) { server.close() }
    pool.shutdown()
    clients.forEach { it.close() }

    metricsStorage
}

private val logger = KotlinLogging.logger {}
