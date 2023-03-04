package com.github.winteryuki.archbench.runner

import com.github.winteryuki.archbench.lib.Endpoint
import com.github.winteryuki.archbench.lib.IpAddress
import com.github.winteryuki.archbench.lib.Port
import kotlin.time.Duration

interface Experiment<Param> : Collection<Pair<Param, (Arch) -> RunConf>>

abstract class AbstractExperiment<Param> : AbstractCollection<Pair<Param, (Arch) -> RunConf>>(), Experiment<Param>

data class ExperimentConf(
    val endpoint: Endpoint = Endpoint(IpAddress.localhost, Port(8082)),
    val nServerWorkerThreads: Int = Runtime.getRuntime().availableProcessors() - 1,
) {
    init {
        require(nServerWorkerThreads > 0)
    }
}

data class NElementsExperiment(
    val nElementsValues: List<Int>,
    val nClients: Int,
    val clientResponseRequestDelay: Duration,
    val nRequestsPerClient: Int,
    val conf: ExperimentConf,
) : AbstractExperiment<Int>() {
    override val size: Int
        get() = nElementsValues.size

    override fun iterator(): Iterator<Pair<Int, (Arch) -> RunConf>> =
        nElementsValues.asSequence().map {
            it to { arch: Arch ->
                RunConf(
                    nElements = it,
                    nClients = nClients,
                    clientResponseRequestDelay = clientResponseRequestDelay,
                    nRequestsPerClient = nRequestsPerClient,
                    arch = arch,
                    endpoint = conf.endpoint,
                    nServerWorkerThreads = conf.nServerWorkerThreads,
                )
            }
        }.iterator()
}

data class NClientsExperiment(
    val nElements: Int,
    val nClientsValues: List<Int>,
    val clientResponseRequestDelay: Duration,
    val nRequestsPerClient: Int,
    val conf: ExperimentConf,
) : AbstractExperiment<Int>() {
    override val size: Int
        get() = nClientsValues.size

    override fun iterator(): Iterator<Pair<Int, (Arch) -> RunConf>> =
        nClientsValues.asSequence().map {
            it to { arch: Arch ->
                RunConf(
                    nElements = nElements,
                    nClients = it,
                    clientResponseRequestDelay = clientResponseRequestDelay,
                    nRequestsPerClient = nRequestsPerClient,
                    arch = arch,
                    endpoint = conf.endpoint,
                    nServerWorkerThreads = conf.nServerWorkerThreads,
                )
            }
        }.iterator()
}

data class ClientResponseRequestDelayExperiment(
    val nElements: Int,
    val nClients: Int,
    val clientResponseRequestDelayValues: List<Duration>,
    val nRequestsPerClient: Int,
    val conf: ExperimentConf,
) : AbstractExperiment<Duration>() {
    override val size: Int
        get() = clientResponseRequestDelayValues.size

    override fun iterator(): Iterator<Pair<Duration, (Arch) -> RunConf>> =
        clientResponseRequestDelayValues.asSequence().map {
            it to { arch: Arch ->
                RunConf(
                    nElements = nElements,
                    nClients = nClients,
                    clientResponseRequestDelay = it,
                    nRequestsPerClient = nRequestsPerClient,
                    arch = arch,
                    endpoint = conf.endpoint,
                    nServerWorkerThreads = conf.nServerWorkerThreads,
                )
            }
        }.iterator()
}
