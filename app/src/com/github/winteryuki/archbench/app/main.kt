package com.github.winteryuki.archbench.app

import com.github.winteryuki.archbench.runner.MetricTag
import com.github.winteryuki.archbench.runner.MetricsStorage
import kotlinx.serialization.Serializable
import kotlin.time.Duration

@Serializable
data class MetricsSnapshot(val times: Map<MetricTag, Duration>) {
    constructor(metricsStorage: MetricsStorage) : this(
        MetricTag.values().associateWith { tag -> metricsStorage.average(tag) })
}
