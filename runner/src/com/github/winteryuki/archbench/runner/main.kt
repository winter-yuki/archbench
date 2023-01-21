package com.github.winteryuki.archbench.runner

import org.jetbrains.letsPlot.export.ggsave
import org.jetbrains.letsPlot.geom.geomDensity
import org.jetbrains.letsPlot.ggplot
import org.jetbrains.letsPlot.ggsize
import org.jetbrains.letsPlot.label.ggtitle
import kotlin.time.DurationUnit
import kotlin.time.toDuration

private const val FAST = true

private fun main() {
    val conf = ExperimentConf()
    val dashes = "------------------"
    println("$dashes n elements \t$dashes")
    runNElements(conf)
    println("$dashes n clients \t$dashes")
    runNClients(conf)
    println("$dashes client delay \t$dashes")
    runClientDelay(conf)
}

private fun runNElements(conf: ExperimentConf) {
    val elements = NElementsExperiment(
        nElementsValues = if (FAST) listOf(1000, 5000, 10000) else {
            listOf(1000, 10_000, 100_000, 200_000, 300_000, 1000_000)
        },
        nClients = conf.nServerWorkerThreads,
        clientResponseRequestDelay = 0.toDuration(DurationUnit.MILLISECONDS),
        nRequestsPerClient = if (FAST) 1 else 5,
        conf = conf,
    )
    elements.execute(
        xLabel = "number of elements",
        title = { tag -> "${tag.label} by elements" },
        filename = { tag -> "nElements$tag.png" },
    )
}

private fun runNClients(conf: ExperimentConf) {
    val clients = NClientsExperiment(
        nElements = 100_000,
        nClientsValues = if (FAST) listOf(1, 10, 20) else listOf(1, 10, 100, 500, 1000),
        clientResponseRequestDelay = 5.toDuration(DurationUnit.MILLISECONDS),
        nRequestsPerClient = if (FAST) 1 else 5,
        conf = conf
    )
    clients.execute(
        xLabel = "number of clients",
        title = { tag -> "${tag.label} by clients" },
        filename = { tag -> "nClients$tag.png" },
    )
}

private fun runClientDelay(conf: ExperimentConf) {
    val delays = ClientResponseRequestDelayExperiment(
        nElements = 100_000,
        nClients = 10,
        clientResponseRequestDelayValues = listOf(0, 10, 20, 30).map { it.toDuration(DurationUnit.MILLISECONDS) },
        nRequestsPerClient = if (FAST) 1 else 5,
        conf = conf
    )
    delays.execute(
        xLabel = "client request response delay",
        title = { tag -> "${tag.label} by delays" },
        filename = { tag -> "delays$tag.png" }
    )
}

private fun <Param> Collection<Pair<Param, (Arch) -> RunConf>>.execute(
    xLabel: String,
    width: Int = 500,
    height: Int = 250,
    title: (MetricTag) -> String,
    filename: (MetricTag) -> String,
) {
    val metrics = Arch.values().flatMap { arch ->
        println("Benchmarking $arch")
        this@execute.map { (x, c) -> println("Value: $x"); x to runBench(c(arch)) }
    }
    MetricTag.values().forEach { tag ->
        val data = mapOf(
            xLabel to metrics.map { it.first },
            tag.label to metrics.map { it.second },
            "arch" to Arch.values().flatMap { arch -> List(this@execute.size) { arch.name } }
        )
        val p = ggplot(data) { x = xLabel; y = tag.label; fill = "arch" } +
                geomDensity() +
                ggsize(width, height) +
                ggtitle(title(tag))
        ggsave(p, filename(tag))
    }
}
