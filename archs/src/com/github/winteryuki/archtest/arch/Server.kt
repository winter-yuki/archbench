package com.github.winteryuki.archtest.arch

import com.github.winteryuki.archtest.lib.IpAddress
import java.io.Closeable

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
