package com.github.winteryuki.archtest.runner

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toDuration

class MetricsStorage {
    private val map = ConcurrentHashMap<MetricTag, ConcurrentLinkedDeque<Duration>>()

    fun log(tag: MetricTag, duration: Duration) {
        val ds = map.getOrPut(tag) { ConcurrentLinkedDeque() }
        ds.add(duration)
    }

    fun average(tag: MetricTag): Duration {
        val ds = map.getValue(tag)
        return ds
            .sumOf { it.inWholeMilliseconds }
            .div(ds.size)
            .toDuration(DurationUnit.MILLISECONDS)
    }
}

enum class MetricTag {
    ServerComputingTime,
    ServerRequestProcessingTime,
    ClientSingleResponseTime,
}
