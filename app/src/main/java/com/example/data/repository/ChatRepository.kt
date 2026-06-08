package com.example.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import android.util.Log
import com.example.crypto.CryptoHelper
import com.example.data.local.ChatDao
import com.example.data.local.MessageEntity
import com.example.data.local.UserEntity
import com.example.data.remote.EncryptedMessagePacket
import com.example.data.remote.NtfyService
import com.example.data.remote.PacketParser
import com.example.data.remote.PresencePacket
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import java.security.KeyFactory
import java.security.KeyPair
import java.security.PrivateKey
import java.security.PublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.UUID

class ChatRepository(
    private val context: Context,
    private val chatDao: ChatDao,
    private val externalScope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
) {
    companion object {
        private const val TAG = "ChatRepository"
        private const val PREFS_NAME = "secure_messenger_prefs"
        private const val KEY_MY_ID = "my_id"
        private const val KEY_MY_USERNAME = "my_username"
        private const val KEY_PRIVATE_KEY = "my_private_key"
        private const val KEY_PUBLIC_KEY = "my_public_key"

        // Unique discovery channels for AI Studio real-time playground
        private const val CLAN_TOPIC_PREFIX = "aistudio_secchat"
        const val DISCOVERY_TOPIC = "${CLAN_TOPIC_PREFIX}_discovery_v1"
        const val MSG_TOPIC_PREFIX = "${CLAN_TOPIC_PREFIX}_msg_"
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var myId: String = ""
        private set

    var myUsername: String = ""
        private set

    private var myPrivateKey: PrivateKey? = null
    private var myPublicKey: PublicKey? = null
    var myPublicKeyBase64: String = ""
        private set

    init {
        loadOrCreateIdentity()
        startPeriodicPresenceAndCleanup()
        listenToDiscovery()
        listenToInbox()
    }

    // Load existing identity or generate a new RSA cryptographic footprint
    private fun loadOrCreateIdentity() {
        val savedId = prefs.getString(KEY_MY_ID, null)
        val savedUsername = prefs.getString(KEY_MY_USERNAME, null)
        val savedPrivKeyBase64 = prefs.getString(KEY_PRIVATE_KEY, null)
        val savedPubKeyBase64 = prefs.getString(KEY_PUBLIC_KEY, null)

        if (savedId != null && savedUsername != null && savedPrivKeyBase64 != null && savedPubKeyBase64 != null) {
            try {
                myId = savedId
                myUsername = savedUsername
                myPublicKeyBase64 = savedPubKeyBase64

                val keyFactory = KeyFactory.getInstance("RSA")
                
                // Reconstruct private key (PKCS8)
                val privBytes = Base64.decode(savedPrivKeyBase64, Base64.NO_WRAP)
                myPrivateKey = keyFactory.generatePrivate(PKCS8EncodedKeySpec(privBytes))

                // Reconstruct public key (X509)
                val pubBytes = Base64.decode(savedPubKeyBase64, Base64.NO_WRAP)
                myPublicKey = keyFactory.generatePublic(X509EncodedKeySpec(pubBytes))

                Log.d(TAG, "Successfully loaded existing cryptographic profile: $myUsername ($myId)")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to reconstruct cryptographic keys, regenerating...", e)
                generateAndSaveIdentity()
            }
        } else {
            generateAndSaveIdentity()
        }
    }

    private fun generateAndSaveIdentity() {
        val randomSuffix = UUID.randomUUID().toString().substring(0, 5)
        myId = "usr_$randomSuffix"
        myUsername = "User_$randomSuffix"

        Log.i(TAG, "Generating custom RSA-2048 E2EE Cryptographic Keypair...")
        val keyPair: KeyPair = CryptoHelper.generateRsaKeyPair()
        myPrivateKey = keyPair.private
        myPublicKey = keyPair.public

        val privBase64 = Base64.encodeToString(myPrivateKey!!.encoded, Base64.NO_WRAP)
        myPublicKeyBase64 = Base64.encodeToString(myPublicKey!!.encoded, Base64.NO_WRAP)

        prefs.edit()
            .putString(KEY_MY_ID, myId)
            .putString(KEY_MY_USERNAME, myUsername)
            .putString(KEY_PRIVATE_KEY, privBase64)
            .putString(KEY_PUBLIC_KEY, myPublicKeyBase64)
            .apply()

        Log.i(TAG, "New cryptographic identity created for user $myUsername ($myId)")
    }

    // Expose local identity settings update
    fun updateUsername(newUsername: String) {
        myUsername = newUsername.trim().ifEmpty { myUsername }
        prefs.edit().putString(KEY_MY_USERNAME, myUsername).apply()
        // Broadcast presence instantly to let others know
        broadcastPresence()
    }

    // Observables from Room database
    val activeUsersFlow: Flow<List<UserEntity>> = chatDao.getAllUsers()

    fun getConversationFlow(otherUserId: String): Flow<List<MessageEntity>> {
        return chatDao.getMessagesWithUser(myId, otherUserId)
    }

    // Clean up local conversation
    suspend fun clearChatHistory(otherUserId: String) {
        chatDao.deleteMessagesWithUser(myId, otherUserId)
    }

    // Broadcast our own presence to the global discovery lobby
    fun broadcastPresence() {
        externalScope.launch {
            val packet = PresencePacket(
                userId = myId,
                username = myUsername,
                publicKey = myPublicKeyBase64,
                timestamp = System.currentTimeMillis()
            )
            val json = PacketParser.serializePresence(packet)
            NtfyService.publish(DISCOVERY_TOPIC, json)
        }
    }

    // Periodic Heartbeats (Every 8 seconds) and Stale User Offline sweeping (Every 10 seconds)
    private fun startPeriodicPresenceAndCleanup() {
        externalScope.launch {
            while (isActive) {
                broadcastPresence()
                delay(8000)
            }
        }

        externalScope.launch {
            while (isActive) {
                val now = System.currentTimeMillis()
                chatDao.markStaleUsersOffline(now)
                delay(10000)
            }
        }
    }

    // Listen in real-time to discovery heartbeats of online users
    private fun listenToDiscovery() {
        externalScope.launch {
            NtfyService.subscribe(DISCOVERY_TOPIC).collect { json ->
                val packet = PacketParser.deserializePresence(json) ?: return@collect
                // Ignore our own presence heartbeats
                if (packet.userId == myId) return@collect

                val user = UserEntity(
                    id = packet.userId,
                    username = packet.username,
                    publicKey = packet.publicKey,
                    lastSeen = System.currentTimeMillis(),
                    isOnline = true
                )
                // Save user directly to RoomDB
                chatDao.insertUser(user)
            }
        }
    }

    // Listen in real-time to encrypted messages targeting our user ID
    private fun listenToInbox() {
        externalScope.launch {
            val inboxTopic = "$MSG_TOPIC_PREFIX$myId"
            NtfyService.subscribe(inboxTopic).collect { json ->
                val packet = PacketParser.deserializeEncryptedMessage(json) ?: return@collect
                if (packet.receiverId != myId) return@collect // Safety check

                // Process the secure message
                decryptAndSaveIncomingMessage(packet)
            }
        }
    }

    // Master E2EE decryption algorithm
    private suspend fun decryptAndSaveIncomingMessage(packet: EncryptedMessagePacket) {
        var decryptedBody = ""
        var decryptionSuccess = false

        try {
            val privateKey = myPrivateKey
            if (privateKey == null) {
                throw IllegalStateException("Private key is missing. Cannot decrypt incoming E2EE messages.")
            }

            // 1. Decrypt AES Key using own RSA private key
            val aesKey = CryptoHelper.decryptAesKeyWithRsa(packet.encryptedAesKey, privateKey)

            // 2. Decrypt message content using AES Key + IV
            decryptedBody = CryptoHelper.decryptWithAes(packet.encryptedBody, aesKey, packet.iv)

            // 3. Verify authenticity: signature using sender's RSA public key
            val sender = chatDao.getUserById(packet.senderId)
            if (sender == null) {
                // If sender isn't cached yet, we reconstruct public key directly from their discovery signature string
                Log.w(TAG, "No cached user for sender ID ${packet.senderId}. Cannot verify authenticity yet.")
                decryptedBody = "[Échec vérification - Expéditeur inconnu] $decryptedBody"
                decryptionSuccess = false
            } else {
                val senderPublicKey = CryptoHelper.decodeBase64ToPublicKey(sender.publicKey)
                val signatureValid = CryptoHelper.verifyPayload(
                    payloadBytes = decryptedBody.toByteArray(Charsets.UTF_8),
                    signatureBase64 = packet.signature,
                    senderPublicKey = senderPublicKey
                )
                if (signatureValid) {
                    decryptionSuccess = true
                } else {
                    Log.e(TAG, "Cryptographic signature verify failed!")
                    decryptedBody = "[Alerte sécurité - Signature de message non authentifiée!] $decryptedBody"
                    decryptionSuccess = false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed E2EE decryption chain: ${e.message}", e)
            decryptedBody = "[⚠️ Déchiffrement Impossible - Données chiffrées corrompues ou Clé non valide]"
            decryptionSuccess = false
        }

        // 4. Save to client local Room database
        val message = MessageEntity(
            id = packet.id,
            senderId = packet.senderId,
            receiverId = myId,
            senderUsername = packet.senderUsername,
            body = decryptedBody,
            timestamp = packet.timestamp,
            isSentByMe = false,
            isSecure = true,
            decryptionSuccess = decryptionSuccess
        )
        chatDao.insertMessage(message)
    }

    // Master E2EE encryption and delivery algorithm
    suspend fun sendSecureMessage(
        recipientId: String,
        recipientUsername: String,
        recipientPublicKeyBase64: String,
        textMessage: String
    ): Boolean {
        if (textMessage.trim().isEmpty()) return false

        try {
            // 1. Decode recipient's RSA Public Key
            val recipientPublicKey = CryptoHelper.decodeBase64ToPublicKey(recipientPublicKeyBase64)

            // 2. Generate unique ephemeral AES Key
            val aesKey = CryptoHelper.generateAesKey()

            // 3. Encrypt AES Key with recipient's RSA Public Key
            val encryptedAesKey = CryptoHelper.encryptAesKeyWithRsa(aesKey, recipientPublicKey)

            // 4. Encrypt raw text block with AES
            val (encryptedBody, iv) = CryptoHelper.encryptWithAes(textMessage, aesKey)

            // 5. Generate message digital signature with own Private Key
            val myPrivKey = myPrivateKey ?: throw IllegalStateException("Local private key missing.")
            val signature = CryptoHelper.signPayload(textMessage.toByteArray(Charsets.UTF_8), myPrivKey)

            // 6. Build final network packet
            val messageId = UUID.randomUUID().toString()
            val timestamp = System.currentTimeMillis()
            val packet = EncryptedMessagePacket(
                id = messageId,
                senderId = myId,
                senderUsername = myUsername,
                receiverId = recipientId,
                encryptedAesKey = encryptedAesKey,
                encryptedBody = encryptedBody,
                iv = iv,
                signature = signature,
                timestamp = timestamp
            )

            // 7. Publish to recipient's private inbox topic on ntfy
            val payloadJson = PacketParser.serializeEncryptedMessage(packet)
            val destinationTopic = "$MSG_TOPIC_PREFIX$recipientId"
            val sentSuccessfully = NtfyService.publish(destinationTopic, payloadJson)

            if (sentSuccessfully) {
                // Save complete readable sent message to local DB history
                val message = MessageEntity(
                    id = messageId,
                    senderId = myId,
                    receiverId = recipientId,
                    senderUsername = myUsername,
                    body = textMessage,
                    timestamp = timestamp,
                    isSentByMe = true,
                    isSecure = true,
                    decryptionSuccess = true
                )
                chatDao.insertMessage(message)
                return true
            }
            return false
        } catch (e: Exception) {
            Log.e(TAG, "Failed E2EE secure packaging and submission: ${e.message}", e)
            return false
        }
    }
}
