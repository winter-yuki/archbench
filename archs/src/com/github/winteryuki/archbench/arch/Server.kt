package com.github.winteryuki.archbench.arch

import com.github.winteryuki.archbench.lib.IpAddress
import mu.KLoggable
import java.io.Closeable
import java.util.concurrent.atomic.AtomicInteger
import kotlin.reflect.KClass
import kotlin.time.Duration

interface Server : Closeable {
    fun start()
    fun awaitTermination()
}

abstract class AbstractServer(private val cls: KClass<*>) : Server {
    private var serverThread: Thread? = null
    protected val id = nRunners.getAndIncrement()

    override fun start() {
        serverThread?.let { error("Unable to start server $id twice") }
        logger.info { "Starting server $id" }
        serverThread = Thread(::run, "${cls.simpleName}-$id")
        serverThread?.start()
    }

    protected abstract fun run()

    override fun awaitTermination() {
        logger.info { "Awaiting termination of server $id" }
        serverThread?.join()
    }

    override fun close() {
        logger.info { "Closing server $id" }
        serverThread?.interrupt()
    }

    companion object : KLoggable {
        private val nRunners = AtomicInteger(0)
        override val logger = logger()
    }
}

fun interface ServerRequestHandler<in Request, out Response> {
    data class HandlingContext(val clientIp: IpAddress)

    fun interface ResponseHandler<in Response> {
        fun handleResponse(response: Response)
    }

    // Unable to use receiver contexts due to the compiler issue
    fun HandlingContext.handleRequest(
        request: Request,
        responseHandler: ResponseHandler<Response>
    )
}

interface ServerTimeLogger {
    fun logRequestProcessingDuration(duration: Duration)

    companion object {
        private val empty = object : ServerTimeLogger {
            override fun logRequestProcessingDuration(duration: Duration) = Unit
        }

        operator fun invoke(): ServerTimeLogger = empty
    }
}
