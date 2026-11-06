package org.cursiveerror.securenote

import com.google.zxing.BarcodeFormat
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import java.io.File
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object CryptoManager {
    private const val ALGORITHM = "AES"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH = 128
    private const val IV_LENGTH = 12

    private val storageDir = File(System.getProperty("user.home"), ".securenote").apply {
        if (!exists()) mkdirs()
    }
    private val keyFile = File(storageDir, "device_key.secret")

    @Volatile
    private var currentKey: SecretKey? = null

    fun getOrGenerateKey(): SecretKey {
        currentKey?.let { return it }

        synchronized(this) {
            currentKey?.let { return it }

            if (keyFile.exists()) {
                try {
                    val base64 = keyFile.readText(Charsets.UTF_8).trim()
                    if (base64.isNotEmpty()) {
                        val key = keyFromBase64(base64)
                        currentKey = key
                        return key
                    }
                } catch (e: Exception) {
                    println("Could not read existing key, generating new one: ${e.message}")
                }
            }

            val newKey = generateAesKey()
            try {
                keyFile.writeText(keyToBase64(newKey), Charsets.UTF_8)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            currentKey = newKey
            return newKey
        }
    }

    fun getKeyBase64(): String {
        return keyToBase64(getOrGenerateKey())
    }

    private fun generateAesKey(): SecretKey {
        val keyGen = KeyGenerator.getInstance(ALGORITHM)
        keyGen.init(256, SecureRandom())
        return keyGen.generateKey()
    }

    fun keyToBase64(key: SecretKey): String {
        return Base64.getEncoder().encodeToString(key.encoded)
    }

    fun keyFromBase64(base64: String): SecretKey {
        val decoded = Base64.getDecoder().decode(base64.trim())
        return SecretKeySpec(decoded, ALGORITHM)
    }

    fun encrypt(plainText: String, secretKey: SecretKey = getOrGenerateKey()): Pair<String, String> {
        val iv = ByteArray(IV_LENGTH)
        SecureRandom().nextBytes(iv)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec)
        val cipherBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))

        val ivBase64 = Base64.getEncoder().encodeToString(iv)
        val dataBase64 = Base64.getEncoder().encodeToString(cipherBytes)
        return Pair(ivBase64, dataBase64)
    }

    fun decrypt(cipherTextBase64: String, ivBase64: String, secretKey: SecretKey = getOrGenerateKey()): String {
        val iv = Base64.getDecoder().decode(ivBase64)
        val cipherBytes = Base64.getDecoder().decode(cipherTextBase64)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
        val plainBytes = cipher.doFinal(cipherBytes)
        return String(plainBytes, Charsets.UTF_8)
    }

    fun generateQrMatrix(content: String, size: Int = 260): BitMatrix {
        val writer = QRCodeWriter()
        return writer.encode(content, BarcodeFormat.QR_CODE, size, size)
    }
}
