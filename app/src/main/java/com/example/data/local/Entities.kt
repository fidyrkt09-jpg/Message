package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val id: String,
    val username: String,
    val publicKey: String,
    val lastSeen: Long,
    val isOnline: Boolean = true
) {
    val publicKeyFingerprint: String
        get() = if (publicKey.length > 16) {
            "SHA256:" + publicKey.hashCode().toString(16).uppercase()
        } else {
            "N/A"
        }
}

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey val id: String,
    val senderId: String,
    val receiverId: String,
    val senderUsername: String,
    val body: String,
    val timestamp: Long,
    val isSentByMe: Boolean,
    val isSecure: Boolean = true,
    val decryptionSuccess: Boolean = true
)
