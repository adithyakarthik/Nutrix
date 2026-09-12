package com.nutrix.app.data.prefs

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.secretsDataStore: DataStore<Preferences> by preferencesDataStore(name = "nutrix_secrets")

/**
 * API keys, encrypted with an AES key that lives in the Android Keystore and never leaves it.
 *
 * The threat this addresses is a rooted device or a careless backup: what lands on disk is
 * ciphertext, and the key that opens it is bound to this app on this device. It does not make
 * shipping a key inside an APK safe — nothing does. See the README on running Nutrix against
 * a backend proxy instead, which is the right answer for a published build.
 */
class SecretStore(context: Context) {

    private val dataStore = context.secretsDataStore

    val anthropicApiKey: Flow<String?> = dataStore.data.map { it[KEY_ANTHROPIC]?.let(::decrypt) }
    val usdaApiKey: Flow<String?> = dataStore.data.map { it[KEY_USDA]?.let(::decrypt) }
    val proxyBaseUrl: Flow<String?> = dataStore.data.map { it[KEY_PROXY]?.let(::decrypt) }

    suspend fun setAnthropicApiKey(value: String?) = put(KEY_ANTHROPIC, value)

    suspend fun setUsdaApiKey(value: String?) = put(KEY_USDA, value)

    suspend fun setProxyBaseUrl(value: String?) = put(KEY_PROXY, value)

    private suspend fun put(key: Preferences.Key<String>, value: String?) {
        dataStore.edit { prefs ->
            val trimmed = value?.trim().orEmpty()
            if (trimmed.isEmpty()) prefs.remove(key) else prefs[key] = encrypt(trimmed)
        }
    }

    private fun encrypt(plaintext: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val iv = cipher.iv
        val bytes = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        // IV is not a secret, but it must travel with the ciphertext: [iv length][iv][ciphertext]
        val packed = ByteArray(1 + iv.size + bytes.size)
        packed[0] = iv.size.toByte()
        iv.copyInto(packed, 1)
        bytes.copyInto(packed, 1 + iv.size)
        return Base64.encodeToString(packed, Base64.NO_WRAP)
    }

    private fun decrypt(encoded: String): String? = runCatching {
        val packed = Base64.decode(encoded, Base64.NO_WRAP)
        val ivSize = packed[0].toInt()
        val iv = packed.copyOfRange(1, 1 + ivSize)
        val bytes = packed.copyOfRange(1 + ivSize, packed.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        String(cipher.doFinal(bytes), Charsets.UTF_8)
    }.getOrNull()

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "nutrix_secret_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_BITS = 128

        val KEY_ANTHROPIC = stringPreferencesKey("anthropic_api_key")
        val KEY_USDA = stringPreferencesKey("usda_api_key")
        val KEY_PROXY = stringPreferencesKey("proxy_base_url")
    }
}
