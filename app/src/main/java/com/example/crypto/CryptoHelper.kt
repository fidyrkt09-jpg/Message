package com.example.crypto

import android.util.Base64
import java.security.*
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object CryptoHelper {

    private const val RSA_ALGORITHM = "RSA"
    private const val RSA_TRANSFORMATION = "RSA/ECB/PKCS1Padding"
    private const val SIGNATURE_ALGORITHM = "SHA256withRSA"

    private const val AES_ALGORITHM = "AES"
    private const val AES_TRANSFORMATION = "AES/CBC/PKCS5Padding"

    // Generate a fresh 2048-bit RSA Key Pair
    fun generateRsaKeyPair(): KeyPair {
        val keyPairGenerator = KeyPairGenerator.getInstance(RSA_ALGORITHM)
        keyPairGenerator.initialize(2048)
        return keyPairGenerator.generateKeyPair()
    }

    // Convert RSA Public Key to Base64 String
    fun encodePublicKeyToBase64(publicKey: PublicKey): String {
        val encodedBytes = publicKey.encoded
        return Base64.encodeToString(encodedBytes, Base64.NO_WRAP)
    }

    // Convert Base64 String back to RSA Public Key
    fun decodeBase64ToPublicKey(base64PublicKey: String): PublicKey {
        val cleanKey = base64PublicKey.trim()
            .replace("\n", "")
            .replace("\r", "")
        val keyBytes = Base64.decode(cleanKey, Base64.NO_WRAP)
        val keyFactory = KeyFactory.getInstance(RSA_ALGORITHM)
        return keyFactory.generatePublic(X509EncodedKeySpec(keyBytes))
    }

    // Generate a secure, pseudo-random 256-bit AES Key
    fun generateAesKey(): SecretKey {
        val keyGenerator = KeyGenerator.getInstance(AES_ALGORITHM)
        keyGenerator.init(256)
        return keyGenerator.generateKey()
    }

    // Encrypt AES SecretKey with a recipient's RSA Public Key
    fun encryptAesKeyWithRsa(aesKey: SecretKey, recipientPublicKey: PublicKey): String {
        val cipher = Cipher.getInstance(RSA_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, recipientPublicKey)
        val encryptedBytes = cipher.doFinal(aesKey.encoded)
        return Base64.encodeToString(encryptedBytes, Base64.NO_WRAP)
    }

    // Decrypt AES SecretKey with own RSA Private Key
    fun decryptAesKeyWithRsa(encryptedAesKeyBase64: String, privateKey: PrivateKey): SecretKey {
        val encryptedBytes = Base64.decode(encryptedAesKeyBase64, Base64.NO_WRAP)
        val cipher = Cipher.getInstance(RSA_TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, privateKey)
        val decryptedBytes = cipher.doFinal(encryptedBytes)
        return SecretKeySpec(decryptedBytes, AES_ALGORITHM)
    }

    // Encrypt plaintext message with AES Key, returning pair of (Ciphertext, IV)
    fun encryptWithAes(plaintext: String, aesKey: SecretKey): Pair<String, String> {
        val cipher = Cipher.getInstance(AES_TRANSFORMATION)
        
        // Generate secure 16-byte IV
        val secureRandom = SecureRandom()
        val iv = ByteArray(16)
        secureRandom.nextBytes(iv)
        val ivSpec = IvParameterSpec(iv)

        cipher.init(Cipher.ENCRYPT_MODE, aesKey, ivSpec)
        val ciphertextBytes = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))

        val ciphertextBase64 = Base64.encodeToString(ciphertextBytes, Base64.NO_WRAP)
        val ivBase64 = Base64.encodeToString(iv, Base64.NO_WRAP)

        return Pair(ciphertextBase64, ivBase64)
    }

    // Decrypt AES ciphertext with AES Key and IV
    fun decryptWithAes(ciphertextBase64: String, aesKey: SecretKey, ivBase64: String): String {
        val ciphertextBytes = Base64.decode(ciphertextBase64, Base64.NO_WRAP)
        val iv = Base64.decode(ivBase64, Base64.NO_WRAP)
        val ivSpec = IvParameterSpec(iv)

        val cipher = Cipher.getInstance(AES_TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, aesKey, ivSpec)
        val plaintextBytes = cipher.doFinal(ciphertextBytes)

        return String(plaintextBytes, Charsets.UTF_8)
    }

    // Sign message payload bytes with own Private Key
    fun signPayload(payloadBytes: ByteArray, privateKey: PrivateKey): String {
        val privateSignature = Signature.getInstance(SIGNATURE_ALGORITHM)
        privateSignature.initSign(privateKey)
        privateSignature.update(payloadBytes)
        val signatureBytes = privateSignature.sign()
        return Base64.encodeToString(signatureBytes, Base64.NO_WRAP)
    }

    // Verify message payload bytes using sender's Public Key
    fun verifyPayload(payloadBytes: ByteArray, signatureBase64: String, senderPublicKey: PublicKey): Boolean {
        return try {
            val signatureBytes = Base64.decode(signatureBase64, Base64.NO_WRAP)
            val publicSignature = Signature.getInstance(SIGNATURE_ALGORITHM)
            publicSignature.initVerify(senderPublicKey)
            publicSignature.update(payloadBytes)
            publicSignature.verify(signatureBytes)
        } catch (e: Exception) {
            false
        }
    }
}
