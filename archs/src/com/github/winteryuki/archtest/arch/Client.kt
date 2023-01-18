package com.github.winteryuki.archtest.arch

import com.github.winteryuki.archtest.lib.Endpoint
import java.io.Closeable

interface Client<in Request> : Closeable {
    fun sendRequest(request: Request)
}

fun interface ClientResponseHandler<in Response> {
    data class HandlingContext(val serverEndpoint: Endpoint)

    fun HandlingContext.handleResponse(response: Response)
}
