package com.example.emotion

/**
 * Lightweight detection result returned by [EmotionDetector].
 */
data class EmotionDetectionResult(
  val userMood: UserMood,
  val suggestedEmotion: EmotionType,
  val confidence: Float,
  val reason: String,
)

/**
 * Interface decoupling mood detection strategy so ML/on-device classifiers can be plugged in later.
 */
interface EmotionDetectorContract {
  fun detect(inputText: String): EmotionDetectionResult
}

/**
 * EmotionDetector — Fast, zero-allocation conversational mood detector supporting both
 * English and Bangla text cues with low memory overhead.
 */
class EmotionDetector : EmotionDetectorContract {

  override fun detect(inputText: String): EmotionDetectionResult {
    val normalized = inputText.trim().lowercase()
    if (normalized.isEmpty()) {
      return EmotionDetectionResult(
        userMood = UserMood.NEUTRAL,
        suggestedEmotion = EmotionType.CALM,
        confidence = 0.80f,
        reason = "Empty or idle input defaults to calm readiness.",
      )
    }

    return when {
      containsAny(normalized, EXCITED_CUES) ->
        EmotionDetectionResult(
          userMood = UserMood.EXCITED,
          suggestedEmotion = EmotionType.EXCITED,
          confidence = 0.92f,
          reason = "Detected enthusiastic or celebratory cues.",
        )
      containsAny(normalized, HAPPY_CUES) ->
        EmotionDetectionResult(
          userMood = UserMood.HAPPY,
          suggestedEmotion = EmotionType.HAPPY,
          confidence = 0.90f,
          reason = "Detected positive and joyful sentiment.",
        )
      containsAny(normalized, SAD_WORRIED_CUES) ->
        EmotionDetectionResult(
          userMood = UserMood.SAD_OR_WORRIED,
          suggestedEmotion = EmotionType.CARING,
          confidence = 0.91f,
          reason = "Detected worry, stress, or sadness; switching to caring patience.",
        )
      containsAny(normalized, HELP_CUES) ->
        EmotionDetectionResult(
          userMood = UserMood.NEEDS_HELP,
          suggestedEmotion = EmotionType.SUPPORTIVE,
          confidence = 0.89f,
          reason = "User requested guidance or help; switching to supportive encouragement.",
        )
      containsAny(normalized, SORRY_CUES) ->
        EmotionDetectionResult(
          userMood = UserMood.APOLOGETIC_OR_ERROR,
          suggestedEmotion = EmotionType.SORRY,
          confidence = 0.87f,
          reason = "Detected apology or setback cue; responding gently.",
        )
      containsAny(normalized, RESPECTFUL_CUES) ->
        EmotionDetectionResult(
          userMood = UserMood.RESPECTFUL_OR_GRATEFUL,
          suggestedEmotion = EmotionType.RESPECTFUL,
          confidence = 0.88f,
          reason = "Detected courteous greeting or gratitude.",
        )
      else ->
        EmotionDetectionResult(
          userMood = UserMood.NEUTRAL,
          suggestedEmotion = EmotionType.CALM,
          confidence = 0.82f,
          reason = "Balanced conversational tone.",
        )
    }
  }

  private fun containsAny(text: String, cues: List<String>): Boolean {
    for (i in cues.indices) {
      if (text.contains(cues[i])) return true
    }
    return false
  }

  companion object {
    private val EXCITED_CUES =
      listOf(
        "excited", "amazing", "awesome", "yay", "woohoo", "incredible", "thrilled", "won",
        "অসাধারণ", "দারুণ খবর", "খুব মজা", "ফাটাফাটি", "জিতেছি", "ওয়াও",
      )

    private val HAPPY_CUES =
      listOf(
        "happy", "glad", "great", "good news", "wonderful", "joy", "smile", "love it", "feeling good",
        "খুশি", "ভালো লাগছে", "আনন্দ", "দারুণ", "চমৎকার", "ভালো আছি", "মজা",
      )

    private val SAD_WORRIED_CUES =
      listOf(
        "sad", "worried", "anxious", "stress", "stressed", "scared", "lonely", "tired", "upset",
        "bad day", "heartbroken", "nervous", "afraid",
        "দুঃখ", "মন খারাপ", "চিন্তা", "চিন্তিত", "ভয়", "কষ্ট", "একা", "ক্লান্ত", "খারাপ লাগছে", "টেনশন",
      )

    private val HELP_CUES =
      listOf(
        "help", "how do i", "how can", "stuck", "assist", "guide", "support", "fix", "explain",
        "সাহায্য", "হেল্প", "পারছি না", "কীভাবে", "কিভাবে", "বুঝতে পারছি না", "বলে দাও",
      )

    private val SORRY_CUES =
      listOf(
        "sorry", "apologize", "my bad", "forgive", "mistake", "failed", "wrong",
        "দুঃখিত", "মাফ", "ভুল হয়ে", "সরি",
      )

    private val RESPECTFUL_CUES =
      listOf(
        "thank", "thanks", "sir", "madam", "please", "respect", "appreciate", "namaste", "salam",
        "ধন্যবাদ", "নমস্কার", "সালাম", "আদাব", "কৃতজ্ঞ", "শুভেচ্ছা",
      )
  }
}
