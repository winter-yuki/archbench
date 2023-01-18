package com.github.winteryuki.archtest.lib

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.serializer
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer

@OptIn(ExperimentalSerializationApi::class)
fun <T> ProtoBuf.Default.encodeToStream(
    serializer: SerializationStrategy<T>,
    value: T,
    stream: OutputStream,
) {
    val bytes = encodeToByteArray(serializer, value)
    stream.write(bytes)
    stream.flush()
}

@OptIn(ExperimentalSerializationApi::class)
inline fun <reified T> ProtoBuf.Default.encodeToStream(value: T, stream: OutputStream) {
    encodeToStream(serializer(), value, stream)
}

@OptIn(ExperimentalSerializationApi::class)
fun <T> ProtoBuf.Default.encodeDelimitedToStream(
    serializer: SerializationStrategy<T>,
    value: T,
    stream: OutputStream,
) {
    val bytes = encodeToByteArray(serializer, value)
    val size = ByteBuffer.allocate(Int.SIZE_BYTES).putInt(bytes.size).array()
    stream.write(size)
    stream.write(bytes)
    stream.flush()
}

@OptIn(ExperimentalSerializationApi::class)
inline fun <reified T> ProtoBuf.Default.encodeDelimitedToStream(value: T, stream: OutputStream) {
    encodeDelimitedToStream(serializer(), value, stream)
}

@OptIn(ExperimentalSerializationApi::class)
fun <T> ProtoBuf.Default.decodeFromStream(deserializer: DeserializationStrategy<T>, stream: InputStream): T =
    decodeFromByteArray(deserializer, stream.readAllBytes())

@OptIn(ExperimentalSerializationApi::class)
inline fun <reified T> ProtoBuf.Default.decodeFromStream(stream: InputStream): T =
    decodeFromByteArray(serializer(), stream.readAllBytes())

@OptIn(ExperimentalSerializationApi::class)
fun <T> ProtoBuf.Default.decodeDelimitedFromStream(deserializer: DeserializationStrategy<T>, stream: InputStream): T? {
    val bytes = stream.readNBytes(Int.SIZE_BYTES)
    if (bytes.size != Int.SIZE_BYTES) return null
    val size = ByteBuffer.wrap(bytes).int
    return decodeFromByteArray(deserializer, stream.readNBytes(size))
}

@OptIn(ExperimentalSerializationApi::class)
inline fun <reified T> ProtoBuf.Default.decodeDelimitedFromStream(stream: InputStream): T? =
    decodeDelimitedFromStream(serializer(), stream)
