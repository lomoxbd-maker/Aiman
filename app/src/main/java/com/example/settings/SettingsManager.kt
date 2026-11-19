package com.example.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.tts.SpeechStyle
import com.example.tts.VoiceLanguage
import com.example.utils.ErrorTracker
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.maxSettingsDataStore: DataStore<Preferences> by
  preferencesDataStore(name = "max_foundation_settings")

/**
 * Contract for reading and persisting MAX application settings.
 */
interface SettingsStore {
  val settingsFlow: Flow<MAXSettings>

  suspend fun setWakeWordEnabled(enabled: Boolean)

  suspend fun setWakeWordSensitivity(sensitivity: Float)

  suspend fun setTtsSpeechRate(rate: Float)

  suspend fun setTtsPitch(pitch: Float)

  suspend fun setVoiceLanguage(language: VoiceLanguage)

  suspend fun setSpeechStyle(style: SpeechStyle)

  suspend fun setMemoryRetentionEnabled(enabled: Boolean)

  suspend fun setStrictPrivacyLock(enabled: Boolean)

  suspend fun setLowPowerStandbyMode(enabled: Boolean)

  suspend fun setDarkThemePreferred(preferred: Boolean)

  suspend fun resetDefaults()
}

/**
 * SettingsManager — Stores MAX application preferences safely using Jetpack DataStore.
 */
class SettingsManager(
  private val context: Context,
  private val errorTracker: ErrorTracker,
) : SettingsStore {

  override val settingsFlow: Flow<MAXSettings> =
    context.maxSettingsDataStore.data
      .catch { exception ->
        if (exception is IOException) {
          errorTracker.logError(MODULE_NAME, "DataStore read error; using default settings.", exception)
          emit(emptyPreferences())
        } else {
          throw exception
        }
      }
      .map { prefs ->
        val langTag = prefs[KEY_VOICE_LANGUAGE] ?: VoiceLanguage.BANGLA_BD.tag
        val styleName = prefs[KEY_SPEECH_STYLE] ?: SpeechStyle.CALM.name
        val parsedStyle =
          runCatching { SpeechStyle.valueOf(styleName) }.getOrDefault(SpeechStyle.CALM)

        MAXSettings(
          wakeWordEnabled = prefs[KEY_WAKE_WORD_ENABLED] ?: true,
          wakeWordSensitivity = (prefs[KEY_WAKE_WORD_SENSITIVITY] ?: 0.72f).coerceIn(0.2f, 1.0f),
          ttsSpeechRate = (prefs[KEY_TTS_RATE] ?: 0.96f).coerceIn(0.55f, 1.85f),
          ttsPitch = (prefs[KEY_TTS_PITCH] ?: 1.0f).coerceIn(0.60f, 1.65f),
          voiceLanguage = VoiceLanguage.fromTag(langTag),
          speechStyle = parsedStyle,
          memoryRetentionEnabled = prefs[KEY_MEMORY_RETENTION] ?: true,
          strictPrivacyLock = prefs[KEY_STRICT_PRIVACY_LOCK] ?: true,
          lowPowerStandbyMode = prefs[KEY_LOW_POWER_STANDBY] ?: true,
          darkThemePreferred = prefs[KEY_DARK_THEME] ?: true,
        )
      }

  override suspend fun setWakeWordEnabled(enabled: Boolean) {
    updatePreference(KEY_WAKE_WORD_ENABLED, enabled)
  }

  override suspend fun setWakeWordSensitivity(sensitivity: Float) {
    updatePreference(KEY_WAKE_WORD_SENSITIVITY, sensitivity.coerceIn(0.2f, 1.0f))
  }

  override suspend fun setTtsSpeechRate(rate: Float) {
    updatePreference(KEY_TTS_RATE, rate.coerceIn(0.55f, 1.85f))
  }

  override suspend fun setTtsPitch(pitch: Float) {
    updatePreference(KEY_TTS_PITCH, pitch.coerceIn(0.60f, 1.65f))
  }

  override suspend fun setVoiceLanguage(language: VoiceLanguage) {
    updatePreference(KEY_VOICE_LANGUAGE, language.tag)
  }

  override suspend fun setSpeechStyle(style: SpeechStyle) {
    updatePreference(KEY_SPEECH_STYLE, style.name)
  }

  override suspend fun setMemoryRetentionEnabled(enabled: Boolean) {
    updatePreference(KEY_MEMORY_RETENTION, enabled)
  }

  override suspend fun setStrictPrivacyLock(enabled: Boolean) {
    updatePreference(KEY_STRICT_PRIVACY_LOCK, enabled)
  }

  override suspend fun setLowPowerStandbyMode(enabled: Boolean) {
    updatePreference(KEY_LOW_POWER_STANDBY, enabled)
  }

  override suspend fun setDarkThemePreferred(preferred: Boolean) {
    updatePreference(KEY_DARK_THEME, preferred)
  }

  override suspend fun resetDefaults() {
    runCatching {
      context.maxSettingsDataStore.edit { it.clear() }
      errorTracker.logInfo(MODULE_NAME, "Settings reset to foundation defaults.")
    }.onFailure { error ->
      errorTracker.logError(MODULE_NAME, "Failed to reset settings.", error)
    }
  }

  private suspend fun <T> updatePreference(key: Preferences.Key<T>, value: T) {
    runCatching {
      context.maxSettingsDataStore.edit { prefs -> prefs[key] = value }
    }.onFailure { error ->
      errorTracker.logError(MODULE_NAME, "Failed to update preference ${key.name}", error)
    }
  }

  companion object {
    private const val MODULE_NAME = "SettingsManager"
    private val KEY_WAKE_WORD_ENABLED = booleanPreferencesKey("wake_word_enabled")
    private val KEY_WAKE_WORD_SENSITIVITY = floatPreferencesKey("wake_word_sensitivity")
    private val KEY_TTS_RATE = floatPreferencesKey("tts_speech_rate")
    private val KEY_TTS_PITCH = floatPreferencesKey("tts_pitch")
    private val KEY_VOICE_LANGUAGE = stringPreferencesKey("voice_language")
    private val KEY_SPEECH_STYLE = stringPreferencesKey("speech_style")
    private val KEY_MEMORY_RETENTION = booleanPreferencesKey("memory_retention_enabled")
    private val KEY_STRICT_PRIVACY_LOCK = booleanPreferencesKey("strict_privacy_lock")
    private val KEY_LOW_POWER_STANDBY = booleanPreferencesKey("low_power_standby")
    private val KEY_DARK_THEME = booleanPreferencesKey("dark_theme_preferred")
  }
}
