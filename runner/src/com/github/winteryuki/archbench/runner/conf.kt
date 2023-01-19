package com.github.winteryuki.archbench.runner

import com.github.winteryuki.archbench.arch.Client
import com.github.winteryuki.archbench.arch.ClientResponseHandler
import com.github.winteryuki.archbench.arch.Server
import com.github.winteryuki.archbench.arch.ServerRequestHandler
import com.github.winteryuki.archbench.arch.ServerTimeLogger
import com.github.winteryuki.archbench.arch.blocking.SimpleBlockingClient
import com.github.winteryuki.archbench.arch.blocking.SimpleBlockingServer
import com.github.winteryuki.archbench.lib.Endpoint
import com.github.winteryuki.archbench.lib.IpAddress
import com.github.winteryuki.archbench.lib.Port
import kotlin.time.Duration

data class Conf(
    val nElementsValues: List<Int>,
    val nClientsValues: List<Int>,
    val clientResponseRequestDelayValues: List<Duration>,
    val nRequestsPerClientValues: List<Int>,
    val port: Port = Port(8082),
    val endpoint: Endpoint = Endpoint(IpAddress.localhost, port),
    val nServerWorkerThreads: Int = Runtime.getRuntime().availableProcessors() - 1,
) {
    init {
        require(nServerWorkerThreads > 0)
    }
}

enum class Arch(val serverFactory: ServerFactory, val clientFactory: ClientFactory) {
    Blocking(
        { port, serverTimeLogger, serverRequestHandler ->
            SimpleBlockingServer(port, serverTimeLogger, serverRequestHandler)
        },
        { endpoint, clientResponseHandler ->
            SimpleBlockingClient(endpoint, clientResponseHandler)
        }
    )
}

fun interface ServerFactory {
    fun create(
        port: Port,
        timeLogger: ServerTimeLogger,
        requestHandler: ServerRequestHandler<BusinessRequest, BusinessResponse>
    ): Server
}

fun interface ClientFactory {
    fun create(endpoint: Endpoint, responseHandler: ClientResponseHandler<BusinessResponse>): Client<BusinessRequest>
}

/**
 * @param nElements N
 * @param nClients M
 * @param clientResponseRequestDelay \Delta
 * @param nRequestsPerClient X
 */
data class RunConf(
    val nElements: Int,
    val nClients: Int,
    val clientResponseRequestDelay: Duration,
    val nRequestsPerClient: Int,
    val arch: Arch,
) {
    init {
        require(nElements >= 0)
        require(nClients >= 0)
        require(nRequestsPerClient >= 0)
    }
}
