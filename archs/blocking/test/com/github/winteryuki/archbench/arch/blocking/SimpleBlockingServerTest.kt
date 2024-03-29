package com.github.winteryuki.archbench.arch.blocking

import com.github.winteryuku.archbench.arch.testing.AbstractServerTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SimpleBlockingServerTest : AbstractServerTest() {
    @BeforeEach
    fun beforeEach() {
        server = SimpleBlockingServer(serverPort, requestHandler = serverHandler)
        server.start()

        client1 = SimpleBlockingClient(endpoint, handler1)
        client2 = SimpleBlockingClient(endpoint, handler2)
        client3 = SimpleBlockingClient(endpoint, handler3)
    }

    @AfterEach
    fun afterEach() = close()

    @Test
    fun `test basic`() = super.testBasic()
}
