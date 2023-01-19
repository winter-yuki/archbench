package com.github.winteryuki.archbench.runner

import com.github.winteryuki.archbench.arch.Client
import com.github.winteryuki.archbench.arch.ServerTimeLogger
import com.github.winteryuki.archbench.lib.Latch
import com.github.winteryuki.archbench.lib.sortSlowly
import com.github.winteryuki.archbench.lib.submitCatching
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime
import kotlin.time.toDuration

private val logger = KotlinLogging.logger {}

private data class ClientRequestInfo(
    val id: Id,
    val client: Client<BusinessRequest>,
    val iRequest: Int,
)

@OptIn(ExperimentalTime::class)
fun runBench(
    conf: Conf,
    runConf: RunConf,
): MetricsStorage = runBlocking {
    val rnd = Random(42)
    val array = IntArray(runConf.nElements) { rnd.nextInt() }
    val metrics = MetricsStorage()
    val timeLogger = object : ServerTimeLogger {
        override fun logRequestProcessingDuration(duration: Duration) {
            metrics.log(MetricTag.ServerRequestProcessingTime, duration)
        }
    }
    val pool = Executors.newFixedThreadPool(conf.nServerWorkerThreads)
    val server = runConf.arch.serverFactory.create(conf.port, timeLogger) { request, responseHandler ->
        pool.submitCatching({ e -> logger.error(e) {} }) {
            val time = measureTime {
                request.array.sortSlowly()
                val response = BusinessResponse(request.id, request.array)
                responseHandler.handleResponse(response)
            }
            metrics.log(MetricTag.ServerComputingTime, time)
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

    val latch = Latch(runConf.nClients)
    val clients = List(runConf.nClients) {
        runConf.arch.clientFactory.create(conf.endpoint) { response ->
            val info = requestInfos.getValue(response.id)
            if (info.iRequest < runConf.nRequestsPerClient) {
                launch {
                    delay(runConf.clientResponseRequestDelay)
                    send(info.client, info.iRequest + 1)
                }
            } else {
                val start = clientsFirstRequestTimeMs.getValue(info.client)
                val elapsed = (System.currentTimeMillis() - start).toDuration(DurationUnit.MILLISECONDS)
                val corrected = elapsed - runConf.clientResponseRequestDelay * (runConf.nRequestsPerClient - 1)
                metrics.log(MetricTag.ClientSingleResponseTime, corrected / runConf.nRequestsPerClient)
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

    metrics
}
