package com.github.winteryuki.archtest.lib

import kotlinx.serialization.Serializable
import java.net.InetAddress
import java.net.Socket

@Serializable
@JvmInline
value class Port(val v: Int) {
    init {
        require(v > 0)
    }

    override fun toString(): String = v.toString()

    companion object {
        fun of(port: String): Port? =
            port.toIntOrNull()?.let { if (it > 0) Port(it) else null }
    }
}

@Serializable
@JvmInline
value class IpAddress(val v: String) {
    init {
        require(check(v)) { "Wrong com.github.winteryuki.archtest.lib.getIp string format" }
    }

    override fun toString(): String = v

    companion object {
        fun check(ip: String): Boolean {
            if (ip == "localhost") return true
            val nums = ip.split('.')
            return if (nums.size != 4) false else {
                nums.all {
                    val num = it.toIntOrNull()
                    num in 0..255
                }
            }
        }

        fun of(ip: String): IpAddress? =
            if (!check(ip)) null else {
                if (ip == "localhost") localhost else IpAddress(ip)
            }

        val localhost = IpAddress("127.0.0.1")
    }
}

val InetAddress.ip: IpAddress
    get() = IpAddress(hostAddress)

@Serializable
data class Endpoint(val ip: IpAddress, val port: Port) {
    override fun toString(): String = "$ip:$port"
}

fun Endpoint.toSocket(): Socket = Socket(ip.v, port.v)
