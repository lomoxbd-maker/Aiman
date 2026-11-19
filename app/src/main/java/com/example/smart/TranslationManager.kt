package com.example.smart

import com.example.utils.ErrorTracker
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * TranslationManager — Privacy-preserving offline Bangla <-> English translator.
 *
 * Privacy guarantee: Never uploads user text, documents, or OCR output to external servers.
 */
class TranslationManager(
  private val errorTracker: ErrorTracker,
  maxHistory: Int = 16,
) {

  private val historyLimit = maxHistory.coerceIn(6, 32)
  private val _history = MutableStateFlow<List<TranslationRecord>>(emptyList())
  val history: StateFlow<List<TranslationRecord>> = _history.asStateFlow()

  private val _lastRecord = MutableStateFlow<TranslationRecord?>(null)
  val lastRecord: StateFlow<TranslationRecord?> = _lastRecord.asStateFlow()

  fun translate(
    sourceText: String,
    direction: TranslationDirection? = null,
  ): Result<TranslationRecord> {
    val clean = sourceText.trim()
    if (clean.isBlank()) {
      return Result.failure(IllegalArgumentException("Please enter text to translate."))
    }

    val resolvedDirection =
      direction ?: if (containsBangla(clean)) {
        TranslationDirection.BANGLA_TO_ENGLISH
      } else {
        TranslationDirection.ENGLISH_TO_BANGLA
      }

    val translated =
      when (resolvedDirection) {
        TranslationDirection.BANGLA_TO_ENGLISH -> translateBanglaToEnglish(clean)
        TranslationDirection.ENGLISH_TO_BANGLA -> translateEnglishToBangla(clean)
      }

    val record =
      TranslationRecord(
        id = UUID.randomUUID().toString(),
        sourceText = clean,
        translatedText = translated,
        direction = resolvedDirection,
        createdAtMillis = System.currentTimeMillis(),
      )
    _lastRecord.value = record
    _history.value = (listOf(record) + _history.value).take(historyLimit)
    errorTracker.logInfo(MODULE_NAME, "Translated (${resolvedDirection.displayLabel}) offline on-device.")
    return Result.success(record)
  }

  /**
   * Parses commands like:
   * - "MAX, এটা বাংলা থেকে ইংরেজিতে অনুবাদ করো: আমি ভালো আছি"
   * - "translate good morning to bangla"
   */
  fun extractTranslationPayload(command: String): Pair<String, TranslationDirection>? {
    val lower = command.lowercase()
    val explicitBnToEn =
      lower.contains("বাংলা থেকে ইংরেজি") || lower.contains("bangla to english")
    val explicitEnToBn =
      lower.contains("ইংরেজি থেকে বাংলা") || lower.contains("english to bangla") || lower.contains("to bangla")

    val hasVerb =
      lower.contains("অনুবাদ") || lower.contains("translate")
    if (!hasVerb) return null

    val stripped =
      command
        .replace(Regex("(?i)max[,:\\s-]*"), "")
        .replace(Regex("বাংলা\\s*থেকে\\s*ইংরেজি(তে)?"), "")
        .replace(Regex("ইংরেজি\\s*থেকে\\s*বাংলা(য়|তে)?"), "")
        .replace(Regex("(?i)bangla\\s*to\\s*english"), "")
        .replace(Regex("(?i)english\\s*to\\s*bangla"), "")
        .replace(Regex("(?i)to\\s*(english|bangla)"), "")
        .replace("এটা", "")
        .replace("অনুবাদ করো", "")
        .replace("অনুবাদ", "")
        .replace(Regex("(?i)translate"), "")
        .trim(' ', ':', '-', ',', '।', '.')

    val fallbackSample =
      stripped.ifBlank {
        if (explicitEnToBn) "Hello, how can I help you today?"
        else "আমি আজ খুব ভালো আছি এবং কাজে মনোযোগ দিচ্ছি।"
      }

    val direction =
      when {
        explicitBnToEn -> TranslationDirection.BANGLA_TO_ENGLISH
        explicitEnToBn -> TranslationDirection.ENGLISH_TO_BANGLA
        containsBangla(fallbackSample) -> TranslationDirection.BANGLA_TO_ENGLISH
        else -> TranslationDirection.ENGLISH_TO_BANGLA
      }
    return fallbackSample to direction
  }

  fun clearHistory() {
    _history.value = emptyList()
    _lastRecord.value = null
  }

  private fun translateBanglaToEnglish(input: String): String {
    val clean = input.trim().trimEnd('।', '.', '!', '?')
    BN_TO_EN_PHRASES.entries.firstOrNull { (k, _) ->
      clean.equals(k, ignoreCase = true)
    }?.let { return it.value }

    var working = clean
    BN_TO_EN_PHRASES.entries
      .sortedByDescending { it.key.length }
      .forEach { (bn, en) ->
        if (working.contains(bn)) {
          working = working.replace(bn, en)
        }
      }

    val tokens = working.split(Regex("\\s+"))
    val mappedTokens =
      tokens.map { token ->
        val punct = token.takeLastWhile { it in ",.?!।;:" }
        val bare = token.dropLast(punct.length)
        val replacement = BN_TO_EN_WORDS[bare] ?: bare
        "$replacement$punct"
      }
    val joined = mappedTokens.joinToString(" ").replace(Regex("\\s+"), " ").trim()
    return joined.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
  }

  private fun translateEnglishToBangla(input: String): String {
    val clean = input.trim().trimEnd('.', '!', '?')
    EN_TO_BN_PHRASES.entries.firstOrNull { (k, _) ->
      clean.equals(k, ignoreCase = true)
    }?.let { return it.value }

    var working = clean.lowercase()
    EN_TO_BN_PHRASES.entries
      .sortedByDescending { it.key.length }
      .forEach { (en, bn) ->
        if (working.contains(en.lowercase())) {
          working = working.replace(en.lowercase(), bn)
        }
      }

    val tokens = working.split(Regex("\\s+"))
    val mapped =
      tokens.map { token ->
        val punct = token.takeLastWhile { it in ",.?!;:" }
        val bare = token.dropLast(punct.length)
        val replacement = EN_TO_BN_WORDS[bare] ?: bare
        "$replacement$punct"
      }
    return mapped.joinToString(" ").replace(Regex("\\s+"), " ").trim()
  }

  private fun containsBangla(text: String): Boolean =
    text.any { it in '\u0980'..'\u09FF' }

  companion object {
    private const val MODULE_NAME = "TranslationManager"

    private val BN_TO_EN_PHRASES =
      linkedMapOf(
        "আমি ভালো আছি" to "I am doing well.",
        "আমি আজ খুব ভালো আছি এবং কাজে মনোযোগ দিচ্ছি" to "I am doing very well today and focusing on work.",
        "আপনি কেমন আছেন" to "How are you?",
        "তুমি কেমন আছো" to "How are you?",
        "শুভ সকাল" to "Good morning.",
        "শুভ বিকাল" to "Good afternoon.",
        "শুভ রাত্রি" to "Good night.",
        "ধন্যবাদ" to "Thank you.",
        "অনেক ধন্যবাদ" to "Thank you very much.",
        "আমি তোমাকে সাহায্য করতে পারি" to "I can help you.",
        "আমি আপনাকে সাহায্য করতে পারি" to "I can help you.",
        "আগামীকাল সকাল ৮টায় মিটিং আছে" to "There is a meeting tomorrow at 8 AM.",
        "আজকের আবহাওয়া খুব সুন্দর" to "Today's weather is very pleasant.",
        "একটু অপেক্ষা করুন" to "Please wait a moment.",
        "সবকিছু ঠিক আছে" to "Everything is alright.",
      )

    private val EN_TO_BN_PHRASES =
      linkedMapOf(
        "hello, how can i help you today" to "হ্যালো, আজ আমি আপনাকে কীভাবে সাহায্য করতে পারি?",
        "i am doing well" to "আমি ভালো আছি।",
        "how are you" to "আপনি কেমন আছেন?",
        "good morning" to "শুভ সকাল।",
        "good night" to "শুভ রাত্রি।",
        "thank you very much" to "অনেক ধন্যবাদ।",
        "thank you" to "ধন্যবাদ।",
        "everything is alright" to "সবকিছু ঠিক আছে।",
        "please wait a moment" to "অনুগ্রহ করে একটু অপেক্ষা করুন।",
        "don't worry, i am here to help" to "চিন্তা করবেন না, আমি সাহায্য করার জন্য আছি।",
      )

    private val BN_TO_EN_WORDS =
      mapOf(
        "আমি" to "I",
        "তুমি" to "you",
        "আপনি" to "you",
        "আমরা" to "we",
        "এটা" to "this",
        "ওটা" to "that",
        "ভালো" to "good",
        "খুব" to "very",
        "সুন্দর" to "beautiful",
        "আজ" to "today",
        "আগামীকাল" to "tomorrow",
        "সকাল" to "morning",
        "বিকাল" to "afternoon",
        "সন্ধ্যা" to "evening",
        "রাত" to "night",
        "সময়" to "time",
        "কাজ" to "work",
        "নোট" to "note",
        "ফাইল" to "file",
        "বই" to "book",
        "বাংলা" to "Bangla",
        "ইংরেজি" to "English",
        "সাহায্য" to "help",
        "খুশি" to "happy",
        "শান্ত" to "calm",
        "বাড়ি" to "home",
        "জল" to "water",
        "পানি" to "water",
        "হ্যাঁ" to "yes",
        "না" to "no",
        "এবং" to "and",
        "আছি" to "am",
        "আছে" to "is available",
      )

    private val EN_TO_BN_WORDS =
      mapOf(
        "i" to "আমি",
        "you" to "আপনি",
        "we" to "আমরা",
        "this" to "এটা",
        "that" to "ওটা",
        "good" to "ভালো",
        "very" to "খুব",
        "today" to "আজ",
        "tomorrow" to "আগামীকাল",
        "morning" to "সকাল",
        "evening" to "সন্ধ্যা",
        "night" to "রাত",
        "work" to "কাজ",
        "note" to "নোট",
        "reminder" to "রিমাইন্ডার",
        "calendar" to "ক্যালেন্ডার",
        "file" to "ফাইল",
        "help" to "সাহায্য",
        "happy" to "খুশি",
        "calm" to "শান্ত",
        "please" to "অনুগ্রহ করে",
        "yes" to "হ্যাঁ",
        "no" to "না",
        "and" to "এবং",
        "hello" to "হ্যালো",
        "friend" to "বন্ধু",
      )
  }
}
