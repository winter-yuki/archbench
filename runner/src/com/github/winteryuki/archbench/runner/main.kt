package com.github.winteryuki.archbench.runner

import org.jetbrains.letsPlot.export.ggsave
import org.jetbrains.letsPlot.geom.geomLine
import org.jetbrains.letsPlot.ggsize
import org.jetbrains.letsPlot.label.ggtitle
import org.jetbrains.letsPlot.letsPlot
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toDuration

private const val FAST = false
private const val PREFIX = "<><><><><><><><><><> "

private fun main() {
    val conf = ExperimentConf()
    val dashes = "------------------"
//    println("$dashes n elements $dashes")
//    runNElements(conf)
    println("$dashes n clients $dashes")
    runNClients(conf)
//    println("$dashes client delay $dashes")
//    runClientDelay(conf)
}

private fun runNElements(conf: ExperimentConf) {
    val elements = NElementsExperiment(
        nElementsValues = if (FAST) listOf(1000, 5000, 10000) else {
            listOf(1000, 2000, 5000, 7000, 10000, 15000)
        },
        nClients = 300,
        clientResponseRequestDelay = Duration.ZERO,
        nRequestsPerClient = if (FAST) 1 else 10,
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
        nElements = 10000,
        nClientsValues = if (FAST) listOf(1, 10, 20) else listOf(1, 10, 50, 100, 150, 200, 250, 300),
        clientResponseRequestDelay = Duration.ZERO,
        nRequestsPerClient = if (FAST) 1 else 10,
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
        nElements = if (FAST) 10000 else 100_000,
        nClients = 2 * conf.nServerWorkerThreads,
        clientResponseRequestDelayValues = listOf(0, 10, 20, 30).map { it.toDuration(DurationUnit.MILLISECONDS) },
        nRequestsPerClient = if (FAST) 1 else 3,
        conf = conf
    )
    delays.execute(
        xLabel = "client request response delay",
        title = { tag -> "${tag.label} by delays" },
        filename = { tag -> "delays$tag.png" }
    )
}

private fun <Param> Experiment<Param>.execute(
    xLabel: String,
    width: Int = 500,
    height: Int = 250,
    title: (MetricTag) -> String,
    filename: (MetricTag) -> String,
) {
    val metrics = Arch.values().flatMap { arch ->
        println("${PREFIX}Benchmarking $xLabel.$arch....")
        this@execute.map { (x, c) ->
            println("${PREFIX}Benchmarking $xLabel.$arch.$x....")
            x to runBench(c(arch)).also { metrics ->
                MetricTag.values().forEach { tag ->
                    println("${PREFIX}Result: $xLabel.$arch.$x: $tag = ${metrics.average(tag)}")
                }
            }
        }
    }
    MetricTag.values().forEach { tag ->
        val yLabel = "${tag.label} (ms)"
        val data = mapOf(
            xLabel to metrics.map { it.first },
            yLabel to metrics.map { it.second.average(tag).inWholeMilliseconds },
            "arch" to Arch.values().flatMap { arch -> List(this@execute.size) { arch.name } }
        )
        val p = letsPlot(data) { x = xLabel; y = yLabel; color = "arch" } +
                geomLine() +
                ggsize(width, height) +
                ggtitle(title(tag))
        ggsave(p, filename(tag))
    }
}
