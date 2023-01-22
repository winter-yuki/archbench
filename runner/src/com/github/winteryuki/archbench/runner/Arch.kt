package com.github.winteryuki.archbench.runner

import com.github.winteryuki.archbench.arch.Client
import com.github.winteryuki.archbench.arch.ClientResponseHandler
import com.github.winteryuki.archbench.arch.Server
import com.github.winteryuki.archbench.arch.ServerRequestHandler
import com.github.winteryuki.archbench.arch.ServerTimeLogger
import com.github.winteryuki.archbench.arch.async.SimpleAsyncClient
import com.github.winteryuki.archbench.arch.async.SimpleAsyncServer
import com.github.winteryuki.archbench.arch.blocking.SimpleBlockingClient
import com.github.winteryuki.archbench.arch.blocking.SimpleBlockingServer
import com.github.winteryuki.archbench.lib.Endpoint
import com.github.winteryuki.archbench.lib.Port

fun interface ServerFactory {
    fun create(
        port: Port,
        timeLogger: ServerTimeLogger,
        requestHandler: ServerRequestHandler<BusinessRequest, BusinessResponse>
    ): Server
}

fun interface ClientFactory {
    fun create(endpoint: Endpoint, responseHandler: ClientResponseHandler<BusinessResponse>): Client<BusinessRequest>
}

enum class Arch(val serverFactory: ServerFactory, val clientFactory: ClientFactory) {
    Blocking(
        SimpleBlockingServer.Companion::invoke,
        SimpleBlockingClient.Companion::invoke,
    ),
    Async(
        SimpleAsyncServer.Companion::invoke,
        SimpleAsyncClient.Companion::invoke,
    ),
}
