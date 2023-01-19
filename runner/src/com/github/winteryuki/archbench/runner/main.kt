package com.github.winteryuki.archbench.runner

import kotlin.time.DurationUnit
import kotlin.time.toDuration

fun main() {
    val conf = Conf(
        nElementsValues = listOf(10, 100, 1000),
        nClientsValues = listOf(1, 10, 100),
        clientResponseRequestDelayValues = listOf(0).map { it.toDuration(DurationUnit.MILLISECONDS) },
        nRequestsPerClientValues = listOf(1, 10)
    )
    val metrics = runGrid(conf)
}

fun runGrid(conf: Conf): Map<RunConf, MetricsStorage> = buildMap {
    conf.nElementsValues.forEach { nElements ->
        conf.nClientsValues.forEach { nClients ->
            conf.clientResponseRequestDelayValues.forEach { clientDelay ->
                conf.nRequestsPerClientValues.forEach { nRequests ->
                    Arch.values().forEach { arch ->
                        val runConf = RunConf(
                            nElements = nElements,
                            nClients = nClients,
                            clientResponseRequestDelay = clientDelay,
                            nRequestsPerClient = nRequests,
                            arch = arch,
                        )
                        val metrics = runBench(conf, runConf)
                        put(runConf, metrics)
                        println(runConf)
                        println("Average server computing time: ${metrics.average(MetricTag.ServerComputingTime)}")
                        println("Average server request processing time: ${metrics.average(MetricTag.ServerRequestProcessingTime)}")
                        println("Average client response awaiting time: ${metrics.average(MetricTag.ClientSingleResponseTime)}")
                    }
                }
            }
        }
    }
}
