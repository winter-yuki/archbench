package com.github.winteryuki.archbench.lib

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import kotlin.random.Random

@Suppress("ClassName")
class _KotlinKtTest {
    @Test
    fun sortSlowly() {
        val rnd = Random(42)
        val xs = IntArray(7654) { rnd.nextInt() }
        val ys = xs.copyOf()
        xs.sortSlowly()
        assertArrayEquals(ys.sortedArray(), xs)
    }
}
