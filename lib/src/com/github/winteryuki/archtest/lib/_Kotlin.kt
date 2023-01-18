package com.github.winteryuki.archtest.lib

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
