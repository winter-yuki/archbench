package com.github.winteryuki.archbench.lib

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.io.Closeable

inline fun <T : Closeable, R> T.use(onFinally: () -> Unit, block: (T) -> R): R =
    try {
        use(block)
    } finally {
        onFinally()
    }

fun IntArray.sortSlowly() {
    for (i in 0 until size - 1) {
        for (j in 0 until size - i - 1) {
            if (get(j) > get(j + 1)) {
                val tmp = get(j)
                set(j, get(j + 1))
                set(j + 1, tmp)
            }
        }
    }
}

class Latch(n: Int) {
    private val channel = Channel<Unit>(capacity = n)
    private val job = CoroutineScope(Dispatchers.Default).launch {
        repeat(n) { channel.receive() }
    }

    suspend fun await() {
        job.join()
    }

    fun countDown() {
        channel.trySend(Unit)
    }
}
