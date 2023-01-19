package com.github.winteryuki.archbench.arch

import com.github.winteryuki.archbench.lib.IpAddress
import java.io.Closeable
import kotlin.time.Duration

interface Server : Closeable {
    fun start()
    fun awaitTermination()
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
