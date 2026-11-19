package com.example.emotion

import com.example.tts.SpeechStyle

/**
 * Defines MAX's human-like conversational response style and emotional voice parameters.
 * Provides varied, natural phrasing in both Bangla and English so MAX never sounds robotic
 * or repeats identical sentences.
 */
data class ResponseStyle(
  val emotion: EmotionType,
  val toneDescription: String,
  val ttsStyle: SpeechStyle,
  val rateScale: Float,
  val pitchScale: Float,
  val sentencePauseMs: Long,
  val banglaPhrases: List<String>,
  val englishPhrases: List<String>,
) {
  fun toVoiceProfile(): EmotionalVoiceProfile =
    EmotionalVoiceProfile(
      emotion = emotion,
      speechStyle = ttsStyle,
      rateScale = rateScale,
      pitchScale = pitchScale,
      pauseMs = sentencePauseMs,
    )

  companion object {
    fun forEmotion(emotion: EmotionType): ResponseStyle =
      when (emotion) {
        EmotionType.HAPPY ->
          ResponseStyle(
            emotion = EmotionType.HAPPY,
            toneDescription = "Warm, joyful, and genuinely upbeat.",
            ttsStyle = SpeechStyle.FRIENDLY,
            rateScale = 1.03f,
            pitchScale = 1.05f,
            sentencePauseMs = 260L,
            banglaPhrases =
              listOf(
                "বাহ, এটা শুনে সত্যিই মন ভালো হয়ে গেল! আপনার আনন্দের মুহূর্তে আমিও পাশে আছি।",
                "খুব সুন্দর খবর! আপনার মুখে হাসি থাকুক, বলুন এখন কী নিয়ে কথা বলব?",
                "দারুণ লাগছে শুনে! আজকের দিনটা তাহলে বেশ ভালোই যাচ্ছে, তাই না?",
              ),
            englishPhrases =
              listOf(
                "That makes me genuinely happy to hear! What a bright moment for you.",
                "I love hearing good news like that. It sounds like today is going wonderfully!",
                "That is fantastic! I am glad things are feeling so positive right now.",
              ),
          )
        EmotionType.CALM ->
          ResponseStyle(
            emotion = EmotionType.CALM,
            toneDescription = "Unrushed, steady, and reassuringly peaceful.",
            ttsStyle = SpeechStyle.CALM,
            rateScale = 0.90f,
            pitchScale = 0.96f,
            sentencePauseMs = 390L,
            banglaPhrases =
              listOf(
                "একটু ধীরে নিঃশ্বাস নিন। আমি শান্তভাবে আপনার সাথেই আছি, তাড়াহুড়োর কিছু নেই।",
                "সব ঠিক হয়ে যাবে। আপনি চাইলে ধীরে ধীরে পুরো বিষয়টা আমাকে বলতে পারেন।",
                "আমি মনোযোগ দিয়ে শুনছি। একসাথে ঠান্ডা মাথায় বিষয়টা গুছিয়ে নেব।",
              ),
            englishPhrases =
              listOf(
                "Take a slow, steady breath. There is no rush at all, and I am right here with you.",
                "Let us take this one calm step at a time. Whenever you are ready, tell me more.",
                "Everything does not have to be solved in one second. I am listening patiently.",
              ),
          )
        EmotionType.CARING ->
          ResponseStyle(
            emotion = EmotionType.CARING,
            toneDescription = "Gentle, empathetic, and attentive to feelings.",
            ttsStyle = SpeechStyle.CARING,
            rateScale = 0.88f,
            pitchScale = 0.98f,
            sentencePauseMs = 420L,
            banglaPhrases =
              listOf(
                "বুঝতে পারছি সময়টা একটু ভারী লাগছে। আপনি একা নন, আমি মন দিয়ে আপনার কথা শুনছি।",
                "মন খারাপ করবেন না, মাঝে মাঝে এমন হতেই পারে। নিজের প্রতি একটু যত্ন নিন।",
                "আপনার অনুভূতিটা আমি বুঝতে পারছি। চাইলে কিছুক্ষণ হালকা হয়ে কথা বলতে পারেন।",
              ),
            englishPhrases =
              listOf(
                "I hear how heavy that feels right now. Please know I am right here listening with care.",
                "It is completely okay to feel this way. Be gentle with yourself while we work through it.",
                "I am really sorry today feels tough. Take your time—I am not going anywhere.",
              ),
          )
        EmotionType.SUPPORTIVE ->
          ResponseStyle(
            emotion = EmotionType.SUPPORTIVE,
            toneDescription = "Encouraging, practical, and steady beside the user.",
            ttsStyle = SpeechStyle.HELPFUL,
            rateScale = 0.97f,
            pitchScale = 1.00f,
            sentencePauseMs = 300L,
            banglaPhrases =
              listOf(
                "চিন্তা করবেন না, আমরা একসাথে এটা সমাধান করতে পারব। শুরুটা কোথা থেকে করব বলুন?",
                "অবশ্যই সাহায্য করব! ছোট ছোট ধাপে এগোলেই কাজটা সহজ হয়ে যাবে।",
                "আপনি ঠিক পথেই আছেন। আমাকে বলুন কোন অংশে আটকে আছেন, আমি পাশে আছি।",
              ),
            englishPhrases =
              listOf(
                "You do not have to figure this out alone—let us tackle it step by step together.",
                "I have got your back. Tell me which part feels tricky, and we will make it simple.",
                "You are closer than you think. Let us break this down calmly and get it done.",
              ),
          )
        EmotionType.EXCITED ->
          ResponseStyle(
            emotion = EmotionType.EXCITED,
            toneDescription = "Energetic, enthusiastic, and bright without rushing.",
            ttsStyle = SpeechStyle.FRIENDLY,
            rateScale = 1.08f,
            pitchScale = 1.08f,
            sentencePauseMs = 230L,
            banglaPhrases =
              listOf(
                "ওয়াও, অসাধারণ! এমন দারুণ খবরে সত্যিই খুব আনন্দ হচ্ছে!",
                "কী দারুণ ব্যাপার! এটা তো উদযাপন করার মতো মুহূর্ত!",
                "অবিশ্বাস্য সুন্দর! আপনার এই উৎসাহ দেখে আমারও ভীষণ ভালো লাগছে!",
              ),
            englishPhrases =
              listOf(
                "Whoa, that is incredible! What an exciting milestone to celebrate!",
                "Yes! That energy is contagious—tell me everything about how it happened!",
                "Amazing news! I am thrilled for you right now!",
              ),
          )
        EmotionType.SORRY ->
          ResponseStyle(
            emotion = EmotionType.SORRY,
            toneDescription = "Sincere, humble, and gently reassuring.",
            ttsStyle = SpeechStyle.CARING,
            rateScale = 0.89f,
            pitchScale = 0.96f,
            sentencePauseMs = 370L,
            banglaPhrases =
              listOf(
                "আমি আন্তরিকভাবে দুঃখিত। চলুন, নতুন করে ঠিকভাবে শুরু করা যাক।",
                "এমনটা হওয়ায় সত্যিই খারাপ লাগছে। ভুলটা শুধরে আমি আপনাকে সাহায্য করতে চাই।",
                "মাফ করবেন যদি অসুবিধা হয়ে থাকে। আমি এখন আরও মনোযোগ দিয়ে চেষ্টা করছি।",
              ),
            englishPhrases =
              listOf(
                "I am genuinely sorry about that. Let us reset gently and get it right for you.",
                "My apologies for the friction there. I want to make this smoother right away.",
                "I am really sorry things hit a bump—no worries, we can sort this out cleanly.",
              ),
          )
        EmotionType.RESPECTFUL ->
          ResponseStyle(
            emotion = EmotionType.RESPECTFUL,
            toneDescription = "Courteous, dignified, and warmly attentive.",
            ttsStyle = SpeechStyle.CALM,
            rateScale = 0.94f,
            pitchScale = 0.99f,
            sentencePauseMs = 340L,
            banglaPhrases =
              listOf(
                "আন্তরিক শ্রদ্ধা ও শুভেচ্ছা। আমি ম্যাক্স, আপনার সেবায় প্রস্তুত আছি।",
                "আপনাকে অনেক ধন্যবাদ। বিনয়ের সাথে বলছি, যেকোনো প্রয়োজনে আমাকে জানাতে পারেন।",
                "নমস্কার ও শুভকামনা। আপনার আজকের কাজ সহজ করতে পারলে আমার ভালো লাগবে।",
              ),
            englishPhrases =
              listOf(
                "Warm greetings to you. It is a pleasure to assist you today.",
                "Thank you kindly. Please let me know how I can be most helpful to you.",
                "With pleasure and respect—I am ready whenever you would like to begin.",
              ),
          )
      }
  }
}
