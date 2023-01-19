@file:Suppress("ArrayInDataClass")

package com.github.winteryuki.archtest.runner

import com.github.winteryuki.archtest.lib.UUIDSerializer
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.util.*

@JvmInline
@Serializable(with = IdSerializer::class)
value class Id(val v: UUID) {
    override fun toString(): String = v.toString()

    companion object {
        fun random(): Id = Id(UUID.randomUUID())
    }
}

object IdSerializer : KSerializer<Id> {
    override val descriptor: SerialDescriptor
        get() = PrimitiveSerialDescriptor(Id::class.qualifiedName!!, PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): Id =
        Id(UUIDSerializer.deserialize(decoder))

    override fun serialize(encoder: Encoder, value: Id) {
        UUIDSerializer.serialize(encoder, value.v)
    }
}


@Serializable
data class BusinessRequest(val id: Id, val array: IntArray)

@Serializable
data class BusinessResponse(val id: Id, val array: IntArray)
