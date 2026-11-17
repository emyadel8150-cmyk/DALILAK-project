package com.casper.dalilak.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

const val DEFAULT_MODEL = "gemini-3.7-flash"
const val DEFAULT_STALE_DAYS = 30

/** تشفير مفتاح الـ API بمفتاح AES جوه Android Keystore. المفتاح ما بيطلعش من الجهاز. */
private object KeyCrypto {
    private const val ALIAS = "dalilak_api_key"
    private const val PROVIDER = "AndroidKeyStore"

    private fun secretKey(): SecretKey {
        val ks = KeyStore.getInstance(PROVIDER).apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
        gen.init(
            KeyGenParameterSpec.Builder(
                ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    fun encrypt(plain: String): Pair<ByteArray, ByteArray> {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, secretKey())
        return Pair(c.iv, c.doFinal(plain.toByteArray(Charsets.UTF_8)))
    }

    fun decrypt(iv: ByteArray, data: ByteArray): String {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, iv))
        return String(c.doFinal(data), Charsets.UTF_8)
    }
}

class AppSettings(ctx: Context) {
    private val sp = ctx.getSharedPreferences("dalilak_settings", Context.MODE_PRIVATE)

    var model: String
        get() = sp.getString("model", DEFAULT_MODEL) ?: DEFAULT_MODEL
        set(v) { sp.edit().putString("model", v).apply() }

    /** فترة اعتبار المعلومة قديمة (بالأيام) */
    var staleDays: Int
        get() = sp.getInt("stale_days", DEFAULT_STALE_DAYS)
        set(v) { sp.edit().putInt("stale_days", v).apply() }

    var lang: String
        get() = sp.getString("lang", "ar") ?: "ar"
        set(v) { sp.edit().putString("lang", v).apply() }

    fun hasKey(): Boolean = sp.contains("api_key_enc") && sp.contains("api_key_iv")

    fun saveKey(plain: String) {
        val (iv, ct) = KeyCrypto.encrypt(plain.filter { !it.isWhitespace() })
        sp.edit()
            .putString("api_key_enc", Base64.encodeToString(ct, Base64.NO_WRAP))
            .putString("api_key_iv", Base64.encodeToString(iv, Base64.NO_WRAP))
            .apply()
    }

    fun loadKey(): String? {
        val enc = sp.getString("api_key_enc", null) ?: return null
        val iv = sp.getString("api_key_iv", null) ?: return null
        return try {
            KeyCrypto.decrypt(Base64.decode(iv, Base64.NO_WRAP), Base64.decode(enc, Base64.NO_WRAP))
        } catch (e: Exception) {
            null
        }
    }

    fun clearKey() {
        sp.edit().remove("api_key_enc").remove("api_key_iv").apply()
    }
}
