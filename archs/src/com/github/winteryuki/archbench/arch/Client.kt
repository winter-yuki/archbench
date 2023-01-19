package com.github.winteryuki.archbench.arch

import com.github.winteryuki.archbench.lib.Endpoint
import java.io.Closeable

interface Client<in Request> : Closeable {
    fun sendRequest(request: Request)
}

fun interface ClientResponseHandler<in Response> {
    data class HandlingContext(val serverEndpoint: Endpoint)

    fun HandlingContext.handleResponse(response: Response)
}
