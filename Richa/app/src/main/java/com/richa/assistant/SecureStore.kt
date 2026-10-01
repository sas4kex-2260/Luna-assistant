package com.richa.assistant

import android.content.Context
import android.util.Base64
import java.nio.charset.StandardCharsets
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object SecureStore {
    private const val PREFS = "richa_secure"
    private const val KEY_NAME = "richa_gemini_key"
    private const val DATA = "gemini_api_key"
    private fun key(): SecretKey {
        val ks = java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val existing = ks.getKey(KEY_NAME, null) as? SecretKey
        if (existing != null) return existing
        val generator = KeyGenerator.getInstance("AES", "AndroidKeyStore")
        generator.init(android.security.keystore.KeyGenParameterSpec.Builder(KEY_NAME, android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or android.security.keystore.KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE).build())
        return generator.generateKey()
    }
    fun saveGeminiKey(context: Context, value: String) {
        val clean = value.trim()
        if (clean.isEmpty()) { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(DATA).apply(); return }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encoded = Base64.encodeToString(cipher.iv + cipher.doFinal(clean.toByteArray(StandardCharsets.UTF_8)), Base64.NO_WRAP)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(DATA, encoded).apply()
    }
    fun getGeminiKey(context: Context): String? {
        val stored = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(DATA, null) ?: return null
        return try {
            val raw = Base64.decode(stored, Base64.NO_WRAP)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, raw.copyOfRange(0, 12)))
            String(cipher.doFinal(raw.copyOfRange(12, raw.size)), StandardCharsets.UTF_8)
        } catch (_: Throwable) { null }
    }
    fun hasGeminiKey(context: Context): Boolean = !getGeminiKey(context).isNullOrBlank()
}