package com.example.tts

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import com.example.utils.ErrorTracker
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Contract for MAX's Bangla & multilingual text-to-speech subsystem.
 */
interface TTSController {
  val state: StateFlow<TTSState>
  val voiceState: StateFlow<VoiceState>
  val config: StateFlow<TTSConfig>
  val voiceConfig: StateFlow<VoiceConfig>

  fun initialize()

  fun updateConfig(newConfig: TTSConfig)

  fun updateVoiceConfig(newConfig: VoiceConfig)

  fun speak(text: String)

  fun speak(text: String, style: SpeechStyle)

  fun stopSpeaking()

  fun pauseSpeaking()

  fun resumeSpeaking()

  fun setSpeechSpeed(speed: Float)

  fun setPitch(pitch: Float)

  fun setSpeechStyle(style: SpeechStyle)

  fun setLanguage(language: VoiceLanguage)

  fun applyEmotionalVoiceProfile(profile: com.example.emotion.EmotionalVoiceProfile)

  fun prepareVoiceResponseSession()

  fun release()
}

/**
 * TTSManager — Natural Bangla & multilingual Text-to-Speech controller for MAX.
 *
 * Key design features:
 * - Selects the highest-quality local natural Android TTS voice available for Bangla (`bn-BD` / `bn-IN`)
 *   with automatic graceful fallback across engines and devices.
 * - Splits text into sentence chunks and inserts subtle silent pauses between sentences so speech
 *   sounds calm and natural rather than robotic.
 * - Supports full playback controls (`speak`, `stopSpeaking`, `pauseSpeaking`, `resumeSpeaking`,
 *   `setSpeechSpeed`, `setPitch`).
 * - Privacy-safe: never touches microphone, camera, gallery, or files.
 */
class TTSManager(
  private val context: Context,
  private val errorTracker: ErrorTracker,
) : TTSController {

  private var engine: TextToSpeech? = null
  private val lock = Any()

  private var activeLocale: Locale = VoiceLanguage.BANGLA_BD.locale
  private var activeVoiceName: String = "System Default"
  private var activeEnginePackage: String = "system"
  private var isFallbackVoice: Boolean = false
  private var activeEmotionProfile: com.example.emotion.EmotionalVoiceProfile? = null
  private var lastAppliedRate: Float = -1f
  private var lastAppliedPitch: Float = -1f

  // Sentence queue retained strictly in memory for pause/resume of current utterance only.
  private var pendingSentences: List<String> = emptyList()
  private var currentSentenceIndex: Int = 0
  private var currentUtteranceBatchId: String = ""

  private val _state = MutableStateFlow(TTSState.UNINITIALIZED)
  override val state: StateFlow<TTSState> = _state.asStateFlow()

  private val _voiceState = MutableStateFlow<VoiceState>(VoiceState.Uninitialized)
  override val voiceState: StateFlow<VoiceState> = _voiceState.asStateFlow()

  private val _config = MutableStateFlow(TTSConfig())
  override val config: StateFlow<TTSConfig> = _config.asStateFlow()

  private val _voiceConfig = MutableStateFlow(VoiceConfig())
  override val voiceConfig: StateFlow<VoiceConfig> = _voiceConfig.asStateFlow()

  override fun initialize() {
    if (_state.value == TTSState.INITIALIZING || _state.value == TTSState.READY) return
    _state.value = TTSState.INITIALIZING
    _voiceState.value = VoiceState.Initializing

    val preferredEnginePkg = detectPreferredEnginePackage()
    createTtsEngine(preferredEnginePkg, allowFallbackToDefault = preferredEnginePkg != null)
  }

  private fun createTtsEngine(
    enginePackage: String?,
    allowFallbackToDefault: Boolean,
  ) {
    runCatching {
      lateinit var instance: TextToSpeech
      val listener =
        TextToSpeech.OnInitListener { status ->
          if (status == TextToSpeech.SUCCESS) {
            // Check if a better engine (such as Google Speech Services) is listed and preferred
            // when we booted on default engine, or finish setup cleanly.
            val currentEng = runCatching { instance.defaultEngine }.getOrNull() ?: enginePackage ?: "system"
            val betterEngine =
              if (enginePackage == null) {
                chooseBestInstalledEngine(instance, currentEng)
              } else {
                null
              }

            if (betterEngine != null && !betterEngine.equals(currentEng, ignoreCase = true)) {
              runCatching { instance.shutdown() }
              createTtsEngine(betterEngine, allowFallbackToDefault = true)
              return@OnInitListener
            }

            engine = instance
            activeEnginePackage = enginePackage ?: currentEng
            configureBestVoiceForLanguage(_voiceConfig.value.language)
            applyAudioConfig(_voiceConfig.value)
            attachProgressListener()
            _state.value = TTSState.READY
            _voiceState.value =
              VoiceState.Ready(
                activeLocaleTag = activeLocale.toLanguageTag(),
                voiceName = activeVoiceName,
                isFallbackVoice = isFallbackVoice,
              )
            errorTracker.logInfo(
              MODULE_NAME,
              "TTSManager ready (engine=$activeEnginePackage, locale=${activeLocale.toLanguageTag()}, voice=$activeVoiceName, fallback=$isFallbackVoice).",
            )
          } else if (allowFallbackToDefault) {
            errorTracker.logWarning(MODULE_NAME, "Preferred TTS engine failed; falling back to default engine.")
            runCatching { instance.shutdown() }
            createTtsEngine(enginePackage = null, allowFallbackToDefault = false)
          } else {
            markUnsupportedFallback("Android TextToSpeech engine failed to initialize (status=$status).")
          }
        }

      instance =
        if (enginePackage != null) {
          TextToSpeech(context.applicationContext, listener, enginePackage)
        } else {
          TextToSpeech(context.applicationContext, listener)
        }
      engine = instance
    }.onFailure { error ->
      if (allowFallbackToDefault) {
        createTtsEngine(enginePackage = null, allowFallbackToDefault = false)
      } else {
        errorTracker.logError(MODULE_NAME, "Failed to create TextToSpeech engine.", error)
        markUnsupportedFallback("TextToSpeech unavailable on this device; visual text fallback active.")
      }
    }
  }

  private fun detectPreferredEnginePackage(): String? {
    val pm = context.packageManager
    val candidates = listOf(GOOGLE_TTS_PACKAGE, SAMSUNG_TTS_PACKAGE)
    return candidates.firstOrNull { pkg ->
      runCatching {
        pm.getPackageInfo(pkg, 0)
        true
      }.getOrDefault(false)
    }
  }

  private fun chooseBestInstalledEngine(tts: TextToSpeech, currentEngine: String): String? {
    val engines = runCatching { tts.engines }.getOrNull() ?: return null
    if (engines.isEmpty()) return null
    val google = engines.firstOrNull { it.name.equals(GOOGLE_TTS_PACKAGE, ignoreCase = true) }
    if (google != null && !currentEngine.equals(GOOGLE_TTS_PACKAGE, ignoreCase = true)) {
      return google.name
    }
    return null
  }

  override fun speak(text: String) {
    speak(text, _voiceConfig.value.speechStyle)
  }

  override fun speak(text: String, style: SpeechStyle) {
    val clean = text.trim()
    if (clean.isEmpty()) return

    if (_voiceConfig.value.speechStyle != style) {
      _voiceConfig.value = _voiceConfig.value.copy(speechStyle = style)
    }

    if (_state.value == TTSState.UNINITIALIZED) {
      initialize()
    }

    val tts = engine
    if (tts == null || _state.value == TTSState.UNSUPPORTED_FALLBACK) {
      errorTracker.logWarning(
        MODULE_NAME,
        "Speak requested while TTS is in fallback mode; displaying text only.",
      )
      return
    }

    val normalizedSpeechText = prepareForNaturalSpeech(clean)
    val sentences = splitIntoNaturalSentences(normalizedSpeechText)
    if (sentences.isEmpty()) return

    synchronized(lock) {
      pendingSentences = sentences
      currentSentenceIndex = 0
      currentUtteranceBatchId = UUID.randomUUID().toString()
      enqueueFromIndex(tts, startIndex = 0, cfg = _voiceConfig.value)
    }
  }

  override fun stopSpeaking() {
    synchronized(lock) {
      pendingSentences = emptyList()
      currentSentenceIndex = 0
      currentUtteranceBatchId = ""
    }
    runCatching { engine?.stop() }
    if (_state.value == TTSState.READY) {
      _voiceState.value =
        VoiceState.Ready(
          activeLocaleTag = activeLocale.toLanguageTag(),
          voiceName = activeVoiceName,
          isFallbackVoice = isFallbackVoice,
        )
    }
  }

  override fun pauseSpeaking() {
    val current = _voiceState.value
    if (current !is VoiceState.Speaking) return

    synchronized(lock) {
      currentUtteranceBatchId = "" // Ignore trailing listener callbacks from flushed queue.
    }
    runCatching { engine?.stop() }

    _voiceState.value =
      VoiceState.Paused(
        resumeFromSentenceIndex = current.currentSentenceIndex,
        totalSentences = current.totalSentences,
        activeStyle = current.activeStyle,
      )
    errorTracker.logInfo(
      MODULE_NAME,
      "Speech paused at sentence ${current.currentSentenceIndex + 1}/${current.totalSentences}.",
    )
  }

  override fun resumeSpeaking() {
    val paused = _voiceState.value as? VoiceState.Paused ?: return
    val tts = engine ?: return

    synchronized(lock) {
      if (paused.resumeFromSentenceIndex >= pendingSentences.size) {
        _voiceState.value =
          VoiceState.Ready(
            activeLocaleTag = activeLocale.toLanguageTag(),
            voiceName = activeVoiceName,
            isFallbackVoice = isFallbackVoice,
          )
        return
      }
      currentSentenceIndex = paused.resumeFromSentenceIndex
      currentUtteranceBatchId = UUID.randomUUID().toString()
      enqueueFromIndex(tts, startIndex = paused.resumeFromSentenceIndex, cfg = _voiceConfig.value)
    }
  }

  override fun setSpeechSpeed(speed: Float) {
    val clamped = speed.coerceIn(0.65f, 1.25f)
    val updated = _voiceConfig.value.copy(speechSpeed = clamped)
    updateVoiceConfig(updated)
  }

  override fun setPitch(pitch: Float) {
    val clamped = pitch.coerceIn(0.75f, 1.35f)
    val updated = _voiceConfig.value.copy(pitch = clamped)
    updateVoiceConfig(updated)
  }

  override fun setSpeechStyle(style: SpeechStyle) {
    updateVoiceConfig(_voiceConfig.value.copy(speechStyle = style))
  }

  override fun setLanguage(language: VoiceLanguage) {
    val updated = _voiceConfig.value.copy(language = language)
    _voiceConfig.value = updated
    _config.value = _config.value.copy(languageTag = language.tag)

    if (_state.value == TTSState.READY) {
      configureBestVoiceForLanguage(language)
      applyAudioConfig(updated)
      if (_voiceState.value is VoiceState.Ready) {
        _voiceState.value =
          VoiceState.Ready(
            activeLocaleTag = activeLocale.toLanguageTag(),
            voiceName = activeVoiceName,
            isFallbackVoice = isFallbackVoice,
          )
      }
    }
  }

  override fun updateConfig(newConfig: TTSConfig) {
    val sanitized =
      newConfig.copy(
        speechRate = newConfig.speechRate.coerceIn(0.65f, 1.25f),
        pitch = newConfig.pitch.coerceIn(0.75f, 1.35f),
      )
    _config.value = sanitized
    val updatedVoice =
      _voiceConfig.value.copy(
        language = VoiceLanguage.fromTag(sanitized.languageTag),
        speechSpeed = sanitized.speechRate,
        pitch = sanitized.pitch,
      )
    _voiceConfig.value = updatedVoice
    applyAudioConfig(updatedVoice)
  }

  override fun updateVoiceConfig(newConfig: VoiceConfig) {
    val sanitized =
      newConfig.copy(
        speechSpeed = newConfig.speechSpeed.coerceIn(0.65f, 1.25f),
        pitch = newConfig.pitch.coerceIn(0.75f, 1.35f),
      )
    val languageChanged = sanitized.language != _voiceConfig.value.language
    _voiceConfig.value = sanitized
    _config.value =
      TTSConfig(
        speechRate = sanitized.speechSpeed,
        pitch = sanitized.pitch,
        languageTag = sanitized.language.tag,
      )
    if (languageChanged && _state.value == TTSState.READY) {
      configureBestVoiceForLanguage(sanitized.language)
    }
    applyAudioConfig(sanitized)
  }

  override fun applyEmotionalVoiceProfile(profile: com.example.emotion.EmotionalVoiceProfile) {
    activeEmotionProfile = profile
    _voiceConfig.value = _voiceConfig.value.copy(speechStyle = profile.speechStyle)
    applyAudioConfig(_voiceConfig.value)
    errorTracker.logInfo(
      MODULE_NAME,
      "Prepared emotional voice profile: ${profile.emotion.displayName} (${profile.speechStyle.displayName}).",
    )
  }

  override fun prepareVoiceResponseSession() {
    if (_state.value == TTSState.UNINITIALIZED) {
      initialize()
    }
    errorTracker.logInfo(MODULE_NAME, "TTS response channel prepared (state=${_state.value}).")
  }

  override fun release() {
    stopSpeaking()
    runCatching { engine?.shutdown() }
    engine = null
    lastAppliedRate = -1f
    lastAppliedPitch = -1f
    _state.value = TTSState.UNINITIALIZED
    _voiceState.value = VoiceState.Uninitialized
  }

  private fun enqueueFromIndex(tts: TextToSpeech, startIndex: Int, cfg: VoiceConfig) {
    applyAudioConfig(cfg)
    val total = pendingSentences.size
    val batchId = currentUtteranceBatchId

    _voiceState.value =
      VoiceState.Speaking(
        currentSentenceIndex = startIndex,
        totalSentences = total,
        activeStyle = cfg.speechStyle,
        activeLocaleTag = activeLocale.toLanguageTag(),
      )

    for (index in startIndex until total) {
      val queueMode = if (index == startIndex) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
      val utteranceId = "$batchId:$index"
      val params =
        Bundle().apply {
          putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
        }
      tts.speak(pendingSentences[index], queueMode, params, utteranceId)

      if (cfg.naturalSentencePauses && index < total - 1) {
        val pauseDuration = activeEmotionProfile?.pauseMs ?: cfg.speechStyle.sentencePauseMs
        tts.playSilentUtterance(
          pauseDuration,
          TextToSpeech.QUEUE_ADD,
          "$batchId:pause:$index",
        )
      }
    }
  }

  private fun attachProgressListener() {
    engine?.setOnUtteranceProgressListener(
      object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {
          val parts = utteranceId?.split(":") ?: return
          if (parts.size == 2 && parts[0] == currentUtteranceBatchId) {
            val idx = parts[1].toIntOrNull() ?: return
            currentSentenceIndex = idx
            val cfg = _voiceConfig.value
            _voiceState.value =
              VoiceState.Speaking(
                currentSentenceIndex = idx,
                totalSentences = pendingSentences.size,
                activeStyle = cfg.speechStyle,
                activeLocaleTag = activeLocale.toLanguageTag(),
              )
          }
        }

        override fun onDone(utteranceId: String?) {
          val parts = utteranceId?.split(":") ?: return
          if (parts.size == 2 && parts[0] == currentUtteranceBatchId) {
            val idx = parts[1].toIntOrNull() ?: return
            if (idx >= pendingSentences.lastIndex) {
              _voiceState.value =
                VoiceState.Ready(
                  activeLocaleTag = activeLocale.toLanguageTag(),
                  voiceName = activeVoiceName,
                  isFallbackVoice = isFallbackVoice,
                )
            }
          }
        }

        @Deprecated("Deprecated in API 21")
        override fun onError(utteranceId: String?) {
          handleSpeechError(utteranceId, -1)
        }

        override fun onError(utteranceId: String?, errorCode: Int) {
          handleSpeechError(utteranceId, errorCode)
        }
      },
    )
  }

  private fun handleSpeechError(utteranceId: String?, errorCode: Int) {
    if (utteranceId != null && !utteranceId.startsWith(currentUtteranceBatchId)) return
    errorTracker.logWarning(MODULE_NAME, "TTS utterance error (code=$errorCode); recovering to ready.")
    _voiceState.value =
      VoiceState.Ready(
        activeLocaleTag = activeLocale.toLanguageTag(),
        voiceName = activeVoiceName,
        isFallbackVoice = isFallbackVoice,
      )
  }

  /**
   * Picks the most natural local voice available for [preferredLanguage], falling back across:
   * 1. Preferred language (`bn-BD`)
   * 2. Secondary Bangla variant (`bn-IN`)
   * 3. English US (`en-US`)
   * 4. System default locale
   */
  private fun configureBestVoiceForLanguage(preferredLanguage: VoiceLanguage) {
    val tts = engine ?: return

    val candidateLocales =
      buildList {
        add(preferredLanguage.locale)
        if (preferredLanguage == VoiceLanguage.BANGLA_BD) {
          add(VoiceLanguage.BANGLA_IN.locale)
        } else if (preferredLanguage == VoiceLanguage.BANGLA_IN) {
          add(VoiceLanguage.BANGLA_BD.locale)
        }
        add(Locale.US)
        add(Locale.getDefault())
      }.distinct()

    var chosenLocale: Locale? = null
    for (candidate in candidateLocales) {
      val support = runCatching { tts.isLanguageAvailable(candidate) }.getOrDefault(TextToSpeech.LANG_NOT_SUPPORTED)
      if (support >= TextToSpeech.LANG_AVAILABLE) {
        tts.language = candidate
        chosenLocale = candidate
        break
      }
    }

    val resolvedLocale = chosenLocale ?: Locale.US
    activeLocale = resolvedLocale
    isFallbackVoice = !resolvedLocale.language.equals(preferredLanguage.locale.language, ignoreCase = true)

    val naturalVoice = selectMostNaturalVoice(tts, resolvedLocale)
    if (naturalVoice != null) {
      runCatching { tts.voice = naturalVoice }
      activeVoiceName = naturalVoice.name
    } else {
      activeVoiceName = "${resolvedLocale.displayLanguage} Standard"
    }

    if (isFallbackVoice) {
      errorTracker.logWarning(
        MODULE_NAME,
        "Preferred ${preferredLanguage.displayLabel} voice unavailable on this engine; using fallback ${resolvedLocale.toLanguageTag()}.",
      )
    }
  }

  private fun selectMostNaturalVoice(tts: TextToSpeech, locale: Locale): Voice? {
    val allVoices = runCatching { tts.voices }.getOrNull() ?: return null
    val matching =
      allVoices.filter { voice ->
        voice.locale.language.equals(locale.language, ignoreCase = true)
      }
    if (matching.isEmpty()) return null

    // Rank highest audio quality first: exact region > high/very-high quality > neural/local > latency.
    return matching.maxByOrNull { voice ->
      scoreVoiceNaturalness(voice, locale, _voiceConfig.value.preferOfflineNaturalVoice)
    }
  }

  private fun applyAudioConfig(cfg: VoiceConfig) {
    val rateScale = activeEmotionProfile?.rateScale ?: 1.0f
    val pitchScale = activeEmotionProfile?.pitchScale ?: 1.0f
    val finalRate = (cfg.effectiveSpeechRate() * rateScale).coerceIn(0.65f, 1.25f)
    val finalPitch = (cfg.effectivePitch() * pitchScale).coerceIn(0.75f, 1.35f)
    val tts = engine ?: return
    runCatching {
      if (kotlin.math.abs(finalRate - lastAppliedRate) > 0.005f) {
        tts.setSpeechRate(finalRate)
        lastAppliedRate = finalRate
      }
      if (kotlin.math.abs(finalPitch - lastAppliedPitch) > 0.005f) {
        tts.setPitch(finalPitch)
        lastAppliedPitch = finalPitch
      }
    }
  }

  private fun markUnsupportedFallback(reason: String) {
    _state.value = TTSState.UNSUPPORTED_FALLBACK
    _voiceState.value = VoiceState.UnsupportedFallback(reason)
    errorTracker.logWarning(MODULE_NAME, reason)
  }

  companion object {
    private const val MODULE_NAME = "TTSManager"
    private const val GOOGLE_TTS_PACKAGE = "com.google.android.tts"
    private const val SAMSUNG_TTS_PACKAGE = "com.samsung.SMT"
    private val SENTENCE_BOUNDARY_REGEX = Regex("(?<=[।.!?\\n])\\s+")
    private val RAW_URL_REGEX = Regex("https?://[^\\s]+")
    private val MARKDOWN_NOISE_REGEX = Regex("[*_`~#]+")
    private val PAUSE_PUNCTUATION_REGEX = Regex("(\\.{3}|—|•|;)")
    private val WHITESPACE_REGEX = Regex("\\s+")

    fun scoreVoiceNaturalness(
      voice: Voice,
      targetLocale: Locale,
      preferOffline: Boolean,
    ): Int {
      var score = 0
      if (voice.locale.country.equals(targetLocale.country, ignoreCase = true)) {
        score += 120
      }
      if (voice.quality >= Voice.QUALITY_VERY_HIGH) {
        score += 200
      } else if (voice.quality >= Voice.QUALITY_HIGH) {
        score += 140
      } else if (voice.quality <= Voice.QUALITY_LOW) {
        score -= 80
      }
      val lowerName = voice.name.lowercase()
      if (lowerName.contains("neural") || lowerName.contains("wavenet") || lowerName.contains("studio")) {
        score += 90
      }
      if (lowerName.contains("local") || !voice.isNetworkConnectionRequired) {
        score += if (preferOffline) 70 else 20
      }
      score -= (voice.latency / 100)
      return score
    }

    /**
     * Cleans formatting symbols and long URLs so MAX pronounces responses naturally
     * rather than spelling out raw URL characters mechanically.
     */
    fun prepareForNaturalSpeech(text: String): String {
      var cleaned = text.trim()
      cleaned = cleaned.replace(RAW_URL_REGEX) { match ->
        val host = runCatching { java.net.URI(match.value).host }.getOrNull()
        host?.removePrefix("www.") ?: ""
      }
      cleaned = cleaned.replace(MARKDOWN_NOISE_REGEX, "")
      cleaned = cleaned.replace(PAUSE_PUNCTUATION_REGEX, ", ")
      return cleaned.replace(WHITESPACE_REGEX, " ").trim()
    }

    /**
     * Splits Bangla and English passages into crisp conversational sentences for natural pausing.
     */
    fun splitIntoNaturalSentences(text: String): List<String> {
      val trimmed = text.trim()
      if (trimmed.isEmpty()) return emptyList()
      return trimmed
        .split(SENTENCE_BOUNDARY_REGEX)
        .map { it.trim() }
        .filter { it.isNotEmpty() }
    }
  }
}
