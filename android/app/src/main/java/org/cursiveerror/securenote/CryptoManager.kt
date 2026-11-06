package org.cursiveerror.securenote

import android.content.Context
import android.util.Base64
import org.json.JSONObject
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object CryptoManager {
    private const val ALGORITHM = "AES"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH = 128
    private const val IV_LENGTH = 12
    private const val PREFS_NAME = "secure_note_crypto"
    private const val KEY_AES = "pairing_aes_key"
    private const val KEY_SERVER_IP = "pairing_server_ip"

    fun getSavedKey(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_AES, null)
    }

    fun getSavedServerIp(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_SERVER_IP, null)
    }

    fun saveServerIp(context: Context, ip: String) {
        val cleanIp = ip.trim()
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_SERVER_IP, cleanIp).apply()
    }

    fun saveKey(context: Context, rawData: String): Pair<String, String?> {
        val trimmed = rawData.trim()
        var extractedKey = trimmed
        var extractedIp: String? = null

        if (trimmed.startsWith("SECURENOTE_PAIR:")) {
            try {
                val jsonStr = trimmed.removePrefix("SECURENOTE_PAIR:").trim()
                val json = JSONObject(jsonStr)
                extractedKey = json.optString("key", "").trim()
                val ip = json.optString("ip", "").trim()
                if (ip.isNotBlank()) {
                    extractedIp = ip
                    saveServerIp(context, ip)
                }
            } catch (_: Exception) {
                extractedKey = trimmed.removePrefix("SECURENOTE_PAIR:").trim()
            }
        } else if (trimmed.startsWith("SECURENOTE_KEY:")) {
            extractedKey = trimmed.removePrefix("SECURENOTE_KEY:").trim()
        }

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_AES, extractedKey).apply()
        return Pair(extractedKey, extractedIp)
    }

    fun clearKey(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_AES).remove(KEY_SERVER_IP).apply()
    }

    fun isPaired(context: Context): Boolean {
        return !getSavedKey(context).isNullOrBlank()
    }

    fun encrypt(plainText: String, keyBase64: String): Pair<String, String> {
        val keyBytes = Base64.decode(keyBase64.trim(), Base64.DEFAULT)
        val secretKey = SecretKeySpec(keyBytes, ALGORITHM)

        val iv = ByteArray(IV_LENGTH)
        SecureRandom().nextBytes(iv)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec)
        val cipherBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))

        val ivBase64 = Base64.encodeToString(iv, Base64.NO_WRAP)
        val dataBase64 = Base64.encodeToString(cipherBytes, Base64.NO_WRAP)
        return Pair(ivBase64, dataBase64)
    }

    fun decrypt(cipherTextBase64: String, ivBase64: String, keyBase64: String): String {
        val keyBytes = Base64.decode(keyBase64.trim(), Base64.DEFAULT)
        val secretKey = SecretKeySpec(keyBytes, ALGORITHM)

        val iv = Base64.decode(ivBase64.trim(), Base64.DEFAULT)
        val cipherBytes = Base64.decode(cipherTextBase64.trim(), Base64.DEFAULT)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
        val plainBytes = cipher.doFinal(cipherBytes)
        return String(plainBytes, Charsets.UTF_8)
    }
}
