package com.example.memory

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.utils.ErrorTracker
import java.io.IOException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.maxMemoryDataStore: DataStore<Preferences> by
  preferencesDataStore(name = "max_secure_memory_store")

/**
 * Storage boundary for MAX Memory System.
 */
interface MemoryRepositoryContract {
  val settingsFlow: Flow<MemorySettings>

  suspend fun loadAllMemories(): List<MemoryModel>

  suspend fun saveAllMemories(items: List<MemoryModel>, encrypt: Boolean)

  suspend fun saveSettings(settings: MemorySettings)

  suspend fun clearStorage()
}

/**
 * MemoryRepository — Lightweight, private local storage backed by Jetpack DataStore and
 * Android Keystore AES-GCM encryption (with safe sandbox fallback on environments without
 * hardware keystore support).
 *
 * Privacy guarantee: Data stays strictly inside MAX's private app storage and is never shared.
 */
class MemoryRepository(
  private val context: Context,
  private val errorTracker: ErrorTracker,
) : MemoryRepositoryContract {

  override val settingsFlow: Flow<MemorySettings> =
    context.maxMemoryDataStore.data
      .catch { e ->
        if (e is IOException) {
          errorTracker.logError(MODULE_NAME, "Failed reading memory settings.", e)
          emit(emptyPreferences())
        } else {
          throw e
        }
      }
      .map { prefs ->
        MemorySettings(
          memoryEnabled = prefs[KEY_MEMORY_ENABLED] ?: true,
          allowSensitiveInformation = prefs[KEY_ALLOW_SENSITIVE] ?: false,
          autoRememberUserPreferences = prefs[KEY_AUTO_PREFERENCES] ?: true,
          maxStoredMemories = (prefs[KEY_MAX_ENTRIES] ?: 50).coerceIn(10, 100),
          encryptAtRest = prefs[KEY_ENCRYPT_AT_REST] ?: true,
        )
      }

  override suspend fun loadAllMemories(): List<MemoryModel> {
    return runCatching {
      val prefs = context.maxMemoryDataStore.data.first()
      val payload = prefs[KEY_MEMORIES_PAYLOAD] ?: return emptyList()
      val isEncrypted = prefs[KEY_PAYLOAD_ENCRYPTED] ?: false
      val jsonText = if (isEncrypted) decryptPayload(payload) else payload
      decodeJsonList(jsonText)
    }.getOrElse { error ->
      errorTracker.logError(MODULE_NAME, "Unable to decode stored memories; returning empty list.", error)
      emptyList()
    }
  }

  override suspend fun saveAllMemories(items: List<MemoryModel>, encrypt: Boolean) {
    runCatching {
      val json = encodeJsonList(items)
      val encryptedResult = if (encrypt) encryptPayload(json) else null
      val finalPayload = encryptedResult ?: json
      val didEncrypt = encryptedResult != null

      context.maxMemoryDataStore.edit { prefs ->
        prefs[KEY_MEMORIES_PAYLOAD] = finalPayload
        prefs[KEY_PAYLOAD_ENCRYPTED] = didEncrypt
      }
    }.onFailure { error ->
      errorTracker.logError(MODULE_NAME, "Failed saving memories securely.", error)
    }
  }

  override suspend fun saveSettings(settings: MemorySettings) {
    runCatching {
      context.maxMemoryDataStore.edit { prefs ->
        prefs[KEY_MEMORY_ENABLED] = settings.memoryEnabled
        prefs[KEY_ALLOW_SENSITIVE] = settings.allowSensitiveInformation
        prefs[KEY_AUTO_PREFERENCES] = settings.autoRememberUserPreferences
        prefs[KEY_MAX_ENTRIES] = settings.maxStoredMemories.coerceIn(10, 100)
        prefs[KEY_ENCRYPT_AT_REST] = settings.encryptAtRest
      }
    }.onFailure { error ->
      errorTracker.logError(MODULE_NAME, "Failed saving MemorySettings.", error)
    }
  }

  override suspend fun clearStorage() {
    runCatching {
      context.maxMemoryDataStore.edit { prefs ->
        prefs.remove(KEY_MEMORIES_PAYLOAD)
        prefs.remove(KEY_PAYLOAD_ENCRYPTED)
      }
    }.onFailure { error ->
      errorTracker.logError(MODULE_NAME, "Failed clearing MemoryRepository.", error)
    }
  }

  private fun encodeJsonList(items: List<MemoryModel>): String {
    val array = JSONArray()
    items.forEach { item ->
      val obj =
        JSONObject().apply {
          put("id", item.id)
          put("type", item.type.name)
          put("key", item.key)
          put("content", item.content)
          put("isSensitive", item.isSensitive)
          put("userApproved", item.userApproved)
          put("createdAtMillis", item.createdAtMillis)
          put("updatedAtMillis", item.updatedAtMillis)
        }
      array.put(obj)
    }
    return array.toString()
  }

  private fun decodeJsonList(jsonText: String): List<MemoryModel> {
    if (jsonText.isBlank()) return emptyList()
    val array = JSONArray(jsonText)
    return buildList(array.length()) {
      for (i in 0 until array.length()) {
        val obj = array.optJSONObject(i) ?: continue
        val typeName = obj.optString("type", MemoryType.CONVERSATION_CONTEXT.name)
        val parsedType =
          runCatching { MemoryType.valueOf(typeName) }.getOrDefault(MemoryType.CONVERSATION_CONTEXT)
        val content = obj.optString("content", "")
        if (content.isNotBlank()) {
          add(
            MemoryModel(
              id = obj.optString("id"),
              type = parsedType,
              key = obj.optString("key", ""),
              content = content,
              isSensitive = obj.optBoolean("isSensitive", false),
              userApproved = obj.optBoolean("userApproved", true),
              createdAtMillis = obj.optLong("createdAtMillis", System.currentTimeMillis()),
              updatedAtMillis = obj.optLong("updatedAtMillis", System.currentTimeMillis()),
            ),
          )
        }
      }
    }
  }

  private fun encryptPayload(plainText: String): String? {
    return runCatching {
      val key = getOrCreateAesKey() ?: return null
      val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
      cipher.init(Cipher.ENCRYPT_MODE, key)
      val iv = cipher.iv
      val cipherBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
      val ivEncoded = Base64.encodeToString(iv, Base64.NO_WRAP)
      val dataEncoded = Base64.encodeToString(cipherBytes, Base64.NO_WRAP)
      "$ivEncoded:$dataEncoded"
    }.getOrNull()
  }

  private fun decryptPayload(encoded: String): String {
    val parts = encoded.split(":", limit = 2)
    if (parts.size != 2) return encoded
    val key = getOrCreateAesKey() ?: return ""
    val iv = Base64.decode(parts[0], Base64.NO_WRAP)
    val cipherBytes = Base64.decode(parts[1], Base64.NO_WRAP)
    val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
    cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
    return String(cipher.doFinal(cipherBytes), Charsets.UTF_8)
  }

  private fun getOrCreateAesKey(): SecretKey? {
    return runCatching {
      val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
      (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

      val generator =
        KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
      val spec =
        KeyGenParameterSpec.Builder(
          KEY_ALIAS,
          KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
          .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
          .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
          .setKeySize(256)
          .build()
      generator.init(spec)
      generator.generateKey()
    }.getOrNull()
  }

  companion object {
    private const val MODULE_NAME = "MemoryRepository"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "max_memory_aes_key"
    private const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"

    private val KEY_MEMORIES_PAYLOAD = stringPreferencesKey("memories_payload")
    private val KEY_PAYLOAD_ENCRYPTED = booleanPreferencesKey("payload_encrypted")
    private val KEY_MEMORY_ENABLED = booleanPreferencesKey("memory_enabled")
    private val KEY_ALLOW_SENSITIVE = booleanPreferencesKey("allow_sensitive")
    private val KEY_AUTO_PREFERENCES = booleanPreferencesKey("auto_preferences")
    private val KEY_MAX_ENTRIES = intPreferencesKey("max_entries")
    private val KEY_ENCRYPT_AT_REST = booleanPreferencesKey("encrypt_at_rest")
  }
}
