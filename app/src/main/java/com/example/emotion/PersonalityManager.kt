package com.example.emotion

import com.example.memory.MemoryController
import com.example.memory.MemoryType
import com.example.tts.VoiceLanguage

/**
 * PersonalityManager — Shapes MAX's warm, non-robotic human personality by combining
 * [EmotionManager] mood understanding with user-approved context from [MemoryManager].
 *
 * Ensures varied, personal greetings and farewells so MAX never repeats the exact same phrasing.
 * Privacy rule: Reads memory strictly during active user interaction and never leaks or shares data.
 */
interface PersonalityController {
  fun craftPersonalizedReply(
    userInput: String,
    language: VoiceLanguage,
  ): Pair<EmotionState, String>
}

class PersonalityManager(
  private val emotionManager: EmotionControllerContract,
  private val memoryController: MemoryController,
) : PersonalityController {

  private val recentGreetingsAndFarewells = ArrayDeque<String>(6)

  override fun craftPersonalizedReply(
    userInput: String,
    language: VoiceLanguage,
  ): Pair<EmotionState, String> {
    val cleanLower = userInput.trim().lowercase()
    val userName = memoryController.getUserName()
    val isEnglish = language == VoiceLanguage.ENGLISH_US

    // Vary greetings and farewells naturally without repeating identical lines back-to-back.
    if (isGreeting(cleanLower)) {
      emotionManager.setEmotion(EmotionType.RESPECTFUL, "Warm greeting")
      val pool = if (isEnglish) ENGLISH_GREETINGS else BANGLA_GREETINGS
      val line = pickNonRepeating(pool)
      val personalized = formatWithName(line, userName)
      return emotionManager.emotionState.value to personalized
    }

    if (isFarewell(cleanLower)) {
      emotionManager.setEmotion(EmotionType.CARING, "Warm farewell")
      val pool = if (isEnglish) ENGLISH_FAREWELLS else BANGLA_FAREWELLS
      val line = pickNonRepeating(pool)
      val personalized = formatWithName(line, userName)
      return emotionManager.emotionState.value to personalized
    }

    val (emotionState, baseReply) = emotionManager.analyzeAndRespond(userInput, language)
    val personalizedReply = formatWithName(baseReply, userName)

    // Note user preferences only when enabled in MemorySettings.
    val memSettings = memoryController.memorySettings.value
    if (memSettings.memoryEnabled && memSettings.autoRememberUserPreferences) {
      if (
        (cleanLower.contains("i prefer") || cleanLower.contains("আমার পছন্দ")) &&
          userInput.trim().length > 10
      ) {
        memoryController.saveMemory(
          type = MemoryType.USER_PREFERENCE,
          content = userInput.trim(),
          hasExplicitPermission = false,
        )
      }
    }

    return emotionState to personalizedReply
  }

  private fun formatWithName(base: String, userName: String?): String {
    return if (!userName.isNullOrBlank() && !base.contains(userName)) {
      "$userName, $base"
    } else {
      base
    }
  }

  private fun pickNonRepeating(pool: List<String>): String {
    val fresh = pool.filterNot { recentGreetingsAndFarewells.contains(it) }
    val selected = (if (fresh.isNotEmpty()) fresh else pool).first()
    recentGreetingsAndFarewells.addLast(selected)
    if (recentGreetingsAndFarewells.size > 5) {
      recentGreetingsAndFarewells.removeFirst()
    }
    return selected
  }

  private fun isGreeting(lower: String): Boolean {
    val cues =
      listOf(
        "hello",
        "hi ",
        "hey max",
        "good morning",
        "good afternoon",
        "good evening",
        "নমস্কার",
        "হ্যালো",
        "হাই",
        "সালাম",
        "শুভ সকাল",
        "কেমন আছো",
        "কেমন আছেন",
      )
    return lower == "hi" || lower == "hey" || cues.any { lower.contains(it) }
  }

  private fun isFarewell(lower: String): Boolean {
    val cues =
      listOf(
        "bye",
        "goodbye",
        "see you",
        "good night",
        "take care",
        "বিদায়",
        "আল্লাহ হাফেজ",
        "ভালো থেকো",
        "পরে কথা হবে",
        "শুভ রাত্রি",
      )
    return cues.any { lower.contains(it) }
  }

  companion object {
    private val BANGLA_GREETINGS =
      listOf(
        "নমস্কার! আমি ম্যাক্স। আজ আপনাকে কীভাবে সাহায্য করতে পারি?",
        "স্বাগতম! মন দিয়ে শুনছি, বলুন আজ কী নিয়ে কথা বলব?",
        "হ্যালো! আশা করি ভালো আছেন। আপনার কোন কাজে সাহায্য করতে পারি?",
        "শুভেচ্ছা! আমি শান্তভাবে প্রস্তুত আছি, আপনি বললেই শুরু করব।",
      )

    private val ENGLISH_GREETINGS =
      listOf(
        "Hello! Good to talk with you. How can I help today?",
        "Hi there! I am listening calmly—what is on your mind?",
        "Warm welcome! Tell me what you would like to work on together.",
        "Hey! Nice to hear from you. How can I make things easier right now?",
      )

    private val BANGLA_FAREWELLS =
      listOf(
        "ভালো থাকবেন। আবার যখনই ডাকবেন, আমি পাশে আছি।",
        "ঠিক আছে, নিজের যত্ন নেবেন। পরে আবার কথা হবে।",
        "শুভকামনা রইল! প্রয়োজন হলেই বলবেন, ম্যাক্স সবসময় প্রস্তুত।",
        "অনেক ভালো থাকুন। সুন্দর সময় কাটুক আপনার!",
      )

    private val ENGLISH_FAREWELLS =
      listOf(
        "Take good care of yourself! Whenever you need me, I am right here.",
        "Goodbye for now—wishing you a peaceful rest of the day.",
        "Talk soon! Feel free to call on me anytime you want a hand.",
        "Stay well! Have a wonderful time ahead.",
      )
  }
}
