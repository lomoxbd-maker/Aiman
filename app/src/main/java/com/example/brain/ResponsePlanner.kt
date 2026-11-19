package com.example.brain

import com.example.emotion.EmotionControllerContract
import com.example.emotion.EmotionState
import com.example.emotion.EmotionType
import com.example.emotion.PersonalityController
import com.example.memory.MemoryController
import com.example.tts.VoiceLanguage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * ResponsePlanner — Crafts warm, calm, human-like replies by weaving together:
 * 1. Personalized memory facts (only when relevant & permitted),
 * 2. Empathetic emotional tone ([EmotionManager]),
 * 3. Active conversation context ([ContextManager]), and
 * 4. Simple, jargon-free error explanations.
 */
class ResponsePlanner(
  private val emotionManager: EmotionControllerContract,
  private val memoryController: MemoryController? = null,
  private val personalityManager: PersonalityController? = null,
) {

  fun planInternetPermissionAsk(
    topic: String,
    language: VoiceLanguage,
  ): Pair<EmotionState, String> {
    emotionManager.setEmotion(EmotionType.RESPECTFUL, "Asking internet permission cleanly")
    val state = emotionManager.emotionState.value
    val reply =
      if (language == VoiceLanguage.ENGLISH_US) {
        "I need your permission before accessing the internet for '$topic'. Please tap Continue on the permission dialog so I can check it safely."
      } else {
        "'$topic' সম্পর্কে জানতে ইন্টারনেট ব্যবহারের অনুমতি প্রয়োজন। আপনার অনুমতি ছাড়া আমি অনলাইন হই না—অনুগ্রহ করে অনুমতি নিশ্চিত করুন।"
      }
    return state to reply
  }

  fun planClarificationAsk(
    question: String,
    language: VoiceLanguage,
  ): Pair<EmotionState, String> {
    val targetEmotion =
      if (language == VoiceLanguage.ENGLISH_US) EmotionType.HAPPY else EmotionType.CARING
    emotionManager.setEmotion(targetEmotion, "Asking helpful follow-up question")
    return emotionManager.emotionState.value to question
  }

  fun planOfflineReasoningReply(
    userInput: String,
    analysis: IntentAnalysis,
    context: ConversationContextSnapshot,
    language: VoiceLanguage,
  ): Pair<EmotionState, String> {
    val lower = userInput.lowercase()
    val savedName = memoryController?.getUserName()?.takeIf { it.isNotBlank() }
    val userName =
      savedName ?: if (language == VoiceLanguage.ENGLISH_US) "Sanju" else "সানজু"

    // 1. Offline Time / Date answers
    if (lower.contains("সময়") || lower.contains("কয়টা বাজে") || lower.contains("what time")) {
      emotionManager.setEmotion(EmotionType.HAPPY, "Offline time check")
      val nowTime = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())
      val reply =
        if (language == VoiceLanguage.ENGLISH_US) "It is $nowTime right now, $userName."
        else "এখন সময় $nowTime, $userName।"
      return emotionManager.emotionState.value to reply
    }

    if (lower.contains("তারিখ") || lower.contains("কী বার") || lower.contains("what date")) {
      emotionManager.setEmotion(EmotionType.HAPPY, "Offline date check")
      val today = SimpleDateFormat("EEEE, dd MMMM yyyy", Locale.getDefault()).format(Date())
      val reply =
        if (language == VoiceLanguage.ENGLISH_US) "Today is $today."
        else "আজ $today।"
      return emotionManager.emotionState.value to reply
    }

    // 2. Personal Recall grounded in MemoryManager (100% offline, device-local)
    if (analysis.primaryIntent == BrainIntent.PERSONAL_RECALL) {
      emotionManager.setEmotion(EmotionType.RESPECTFUL, "Offline personal recall")
      val memories = memoryController?.memories?.value.orEmpty()
      val reply =
        if (memories.isEmpty()) {
          if (language == VoiceLanguage.ENGLISH_US) {
            "I know your name is $userName, and we can save any preferences or notes whenever you ask me to remember them."
          } else {
            "আমি জানি আপনি $userName। আপনি চাইলে আপনার পছন্দ বা গুরুত্বপূর্ণ তথ্য মনে রাখতে বলতে পারেন—সবকিছু ডিভাইসে নিরাপদ থাকবে।"
          }
        } else {
          val highlights = memories.take(3).joinToString("; ") { it.content }
          if (language == VoiceLanguage.ENGLISH_US) {
            "$userName, from what you asked me to remember: $highlights."
          } else {
            "$userName, আপনার মনে রাখা তথ্যগুলো হলো: $highlights।"
          }
        }
      return emotionManager.emotionState.value to reply
    }

    // 3. Follow-up continuation using active conversation context
    if (analysis.primaryIntent == BrainIntent.FOLLOW_UP_CONTINUATION && context.activeTopic != null) {
      emotionManager.setEmotion(EmotionType.SUPPORTIVE, "Contextual continuation")
      val topic = context.activeTopic
      val matchingMemory =
        memoryController?.memories?.value
          ?.firstOrNull { it.content.contains(topic, ignoreCase = true) }
          ?.content
      val reply =
        when {
          matchingMemory != null && language == VoiceLanguage.ENGLISH_US ->
            "Continuing on $topic: I also noted '$matchingMemory'. Would you like me to look it up online if needed?"
          matchingMemory != null ->
            "$topic প্রসঙ্গে আপনার সংরক্ষিত তথ্যে আছে: '$matchingMemory'। প্রয়োজনে অনুমতি দিলে অনলাইনেও খুঁজে দেখতে পারি।"
          language == VoiceLanguage.ENGLISH_US ->
            "We were just talking about '$topic'. You can ask me to save a note about it or check online with your permission."
          else ->
            "আমরা '$topic' নিয়ে কথা বলছিলাম। আপনি চাইলে এটি নোট হিসেবে রাখতে পারেন বা অনুমতি দিলে অনলাইন থেকেও বিস্তারিত দেখতে পারি।"
        }
      return emotionManager.emotionState.value to reply
    }

    // 4. General personalized dialogue via PersonalityManager + EmotionManager + Memory awareness
    val (emotionState, baseReply) =
      personalityManager?.craftPersonalizedReply(userInput, language)
        ?: emotionManager.analyzeAndRespond(userInput, language)

    val relevantMemory = findRelevantMemoryHint(userInput)
    val enrichedReply =
      if (relevantMemory != null && analysis.primaryIntent == BrainIntent.GENERAL_QUESTION) {
        if (language == VoiceLanguage.ENGLISH_US) {
          "$baseReply By the way, I also remember: \"$relevantMemory\"."
        } else {
          "$baseReply পাশাপাশি মনে আছে, আপনি জানিয়েছিলেন: \"$relevantMemory\"।"
        }
      } else {
        baseReply
      }

    return emotionState to enrichedReply
  }

  fun planFriendlyErrorReply(
    reason: String?,
    language: VoiceLanguage,
  ): Pair<EmotionState, String> {
    emotionManager.setEmotion(EmotionType.CARING, "Graceful error recovery")
    val state = emotionManager.emotionState.value
    val reply =
      if (language == VoiceLanguage.ENGLISH_US) {
        "Something didn't work as expected${reason?.let { " ($it)" }.orEmpty()}. Let's try again calmly or phrase it another way."
      } else {
        "দুঃখিত সানজু, এই মুহূর্তে কাজটি সম্পন্ন করতে সাময়িক সমস্যা হয়েছে। একটু পরে বা অন্যভাবে বললে আমি আবার চেষ্টা করব।"
      }
    return state to reply
  }

  private fun findRelevantMemoryHint(query: String): String? {
    val tokens =
      query.lowercase()
        .split(TOKEN_SPLIT_REGEX)
        .filter { it.length >= 3 }
    if (tokens.isEmpty()) return null
    val memories = memoryController?.memories?.value.orEmpty()
    return memories.firstOrNull { mem ->
      val contentLower = mem.content.lowercase()
      tokens.any { token -> contentLower.contains(token) }
    }?.content
  }

  companion object {
    private val TOKEN_SPLIT_REGEX = Regex("[\\s,।?!]+")
  }
}
