package com.github.winteryuki.archbench.runner

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toDuration

enum class MetricTag(val label: String) {
    ServerComputingTime("server computing"),
    ServerRequestProcessingTime("server request"),
    ClientSingleResponseTime("client request"),
}

interface MetricsStorage {
    fun average(tag: MetricTag): Duration
}

interface MutableMetricsStorage : MetricsStorage {
    fun log(tag: MetricTag, duration: Duration)
}

class SimpleConcurrentMetricsStorage : MutableMetricsStorage {
    private val map = ConcurrentHashMap<MetricTag, ConcurrentLinkedDeque<Duration>>()

    override fun log(tag: MetricTag, duration: Duration) {
        val ds = map.getOrPut(tag) { ConcurrentLinkedDeque() }
        ds.add(duration)
    }

    override fun average(tag: MetricTag): Duration {
        val ds = map[tag] ?: error("No information on about $tag")
        return ds
            .sumOf { it.inWholeMilliseconds }
            .div(ds.size)
            .toDuration(DurationUnit.MILLISECONDS)
    }
}
