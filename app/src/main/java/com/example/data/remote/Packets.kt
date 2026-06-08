package com.example.data.remote

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

// Packet circulated to discover peers
data class PresencePacket(
    val userId: String,
    val username: String,
    val publicKey: String,
    val timestamp: Long
)

// Packet circulated to send a bulletproof E2EE message
data class EncryptedMessagePacket(
    val id: String,
    val senderId: String,
    val senderUsername: String,
    val receiverId: String,
    val encryptedAesKey: String,
    val encryptedBody: String,
    val iv: String,
    val signature: String,
    val timestamp: Long
)

object PacketParser {
    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val presenceAdapter = moshi.adapter(PresencePacket::class.java)
    private val encryptedMessageAdapter = moshi.adapter(EncryptedMessagePacket::class.java)

    fun serializePresence(presence: PresencePacket): String {
        return presenceAdapter.toJson(presence)
    }

    fun deserializePresence(json: String): PresencePacket? {
        return try {
            presenceAdapter.fromJson(json)
        } catch (e: Exception) {
            null
        }
    }

    fun serializeEncryptedMessage(msg: EncryptedMessagePacket): String {
        return encryptedMessageAdapter.toJson(msg)
    }

    fun deserializeEncryptedMessage(json: String): EncryptedMessagePacket? {
        return try {
            encryptedMessageAdapter.fromJson(json)
        } catch (e: Exception) {
            null
        }
    }
}
