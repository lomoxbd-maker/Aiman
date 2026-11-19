package com.example.brain

/**
 * IntentAnalyzer — Lightweight semantic intent & slot analyzer in Bangla, Banglish, and English.
 *
 * Understands full user intent and missing parameters (instead of naive single-word matching),
 * links follow-up turns using active conversation context, and determines cleanly whether an
 * internet connection is actually required or if MAX can handle the task offline.
 */
class IntentAnalyzer {

  fun analyze(
    rawInput: String,
    context: ConversationContextSnapshot,
  ): IntentAnalysis {
    val clean = rawInput.trim()
    val lower = clean.lowercase()

    // 1. If a follow-up clarification was pending, resolve the user's answer into that intent!
    val pending = context.pendingClarification
    if (pending != null && clean.isNotEmpty() && !looksLikeBrandNewCommand(lower)) {
      return IntentAnalysis(
        primaryIntent = BrainIntent.CLARIFICATION_ANSWER,
        confidence = 0.94f,
        requiresInternet = pending.intent in INTERNET_INTENTS,
        isPersonalQuery = false,
        extractedSlots = mapOf(pending.missingSlot to clean),
        missingSlots = emptyList(),
        summaryLabel = "Clarification for ${pending.intent.name}",
      )
    }

    // 2. Dismissal / end-of-conversation intent (offline)
    if (isDismissal(lower)) {
      return IntentAnalysis(
        primaryIntent = BrainIntent.DISMISSAL,
        confidence = 0.96f,
        requiresInternet = false,
        isPersonalQuery = false,
        summaryLabel = "Conversation closing cue",
      )
    }

    // 3. Offline explicit Memory Commands
    if (containsAny(lower, "মনে রাখো", "ভুলে যাও", "memory দেখাও", "memory মুছে", "remember this", "forget that")) {
      return IntentAnalysis(
        primaryIntent = BrainIntent.MEMORY_COMMAND,
        confidence = 0.95f,
        requiresInternet = false,
        isPersonalQuery = true,
        summaryLabel = "Explicit memory management",
      )
    }

    // 4. Contextual follow-up continuation ("আরো বলো", "ওটা সম্পর্কে", "tell me more")
    if (context.activeTopic != null &&
      containsAny(lower, "আরো বলো", "আর একটু বলো", "এটা সম্পর্কে", "ওটা আবার", "tell me more", "what about that", "more details")
    ) {
      return IntentAnalysis(
        primaryIntent = BrainIntent.FOLLOW_UP_CONTINUATION,
        confidence = 0.88f,
        requiresInternet = context.lastIntent in INTERNET_INTENTS,
        isPersonalQuery = false,
        extractedSlots = mapOf("topic" to context.activeTopic),
        summaryLabel = "Follow-up on '${context.activeTopic}'",
      )
    }

    // 5. Device Apps & Hardware Controls (offline / local Android intents)
    if (containsAny(lower, "whatsapp", "হোয়াটসঅ্যাপ", "messenger", "মেসেঞ্জার", "facebook", "ফেসবুক", "gmail", "জিমেইল", "maps", "ম্যাপস", "chrome", "ক্রোম") &&
      containsAny(lower, "খোলো", "চালাও", "open", "launch", "start", "পাঠাও", "message", "mail")
    ) {
      return IntentAnalysis(
        primaryIntent = BrainIntent.APP_LAUNCH,
        confidence = 0.92f,
        requiresInternet = false,
        isPersonalQuery = false,
        summaryLabel = "App launch request",
      )
    }

    if (containsAny(lower, "camera", "ক্যামেরা", "selfie", "সেলফি", "photo", "ছবি তোলো", "qr", "কিউআর", "record video", "ভিডিও")) {
      return IntentAnalysis(
        primaryIntent = BrainIntent.CAMERA_VISION,
        confidence = 0.93f,
        requiresInternet = false,
        isPersonalQuery = false,
        summaryLabel = "Camera / vision request",
      )
    }

    if (containsAny(lower, "gallery", "গ্যালারি", "photos", "ফটো দেখাও")) {
      return IntentAnalysis(
        primaryIntent = BrainIntent.GALLERY_MEDIA,
        confidence = 0.92f,
        requiresInternet = false,
        isPersonalQuery = false,
        summaryLabel = "Gallery media request",
      )
    }

    if (containsAny(lower, "open file", "ফাইল খোলো", "read pdf", "pdf পড়ো")) {
      return IntentAnalysis(
        primaryIntent = BrainIntent.FILE_DOCUMENT,
        confidence = 0.90f,
        requiresInternet = false,
        isPersonalQuery = false,
        summaryLabel = "File / document reader",
      )
    }

    if (containsAny(lower, "flashlight", "torch", "ফ্ল্যাশ", "টর্চ")) {
      return IntentAnalysis(
        primaryIntent = BrainIntent.FLASHLIGHT_CONTROL,
        confidence = 0.94f,
        requiresInternet = false,
        isPersonalQuery = false,
        summaryLabel = "Flashlight control",
      )
    }

    // 6. Smart Tools: Translation, Reminders, Notes, Calendar (offline local)
    if (containsAny(lower, "অনুবাদ", "translate", "translation")) {
      val contentAfterColon = extractTopicAfterKeywords(clean, listOf("অনুবাদ করো", "translate", "অনুবাদ", "translation"))
      val missing = if (contentAfterColon.isBlank()) listOf("text_to_translate") else emptyList()
      return IntentAnalysis(
        primaryIntent = BrainIntent.TRANSLATION,
        confidence = 0.92f,
        requiresInternet = false,
        isPersonalQuery = false,
        extractedSlots = if (contentAfterColon.isNotBlank()) mapOf("text" to contentAfterColon) else emptyMap(),
        missingSlots = missing,
        summaryLabel = "Offline translation",
      )
    }

    if (containsAny(lower, "রিমাইন্ডার", "মনে করিয়ে", "reminder", "নোট", "note")) {
      return IntentAnalysis(
        primaryIntent = BrainIntent.REMINDER_OR_NOTE,
        confidence = 0.91f,
        requiresInternet = false,
        isPersonalQuery = false,
        summaryLabel = "Reminder or smart note",
      )
    }

    if (containsAny(lower, "ক্যালেন্ডার", "শিডিউল", "calendar", "schedule", "মিটিং")) {
      return IntentAnalysis(
        primaryIntent = BrainIntent.CALENDAR_SCHEDULE,
        confidence = 0.90f,
        requiresInternet = false,
        isPersonalQuery = true,
        summaryLabel = "Calendar schedule lookup",
      )
    }

    // 7. Phone Control & Utilities (offline + confirmation)
    if (containsAny(lower, "কল করো", "ফোন করো", "call ") || lower == "কল" || lower == "call") {
      val phoneOrName = extractPhoneOrTarget(clean)
      val missing = if (phoneOrName.isBlank()) listOf("recipient") else emptyList()
      return IntentAnalysis(
        primaryIntent = BrainIntent.CALL_CONTACT,
        confidence = 0.91f,
        requiresInternet = false,
        isPersonalQuery = true,
        extractedSlots = if (phoneOrName.isNotBlank()) mapOf("recipient" to phoneOrName) else emptyMap(),
        missingSlots = missing,
        summaryLabel = "Phone call assistance",
      )
    }

    if (containsAny(lower, "sms", "মেসেজ পাঠাও") && !lower.contains("whatsapp")) {
      val phoneOrName = extractPhoneOrTarget(clean)
      val missing = if (phoneOrName.isBlank()) listOf("recipient") else emptyList()
      return IntentAnalysis(
        primaryIntent = BrainIntent.SEND_SMS,
        confidence = 0.89f,
        requiresInternet = false,
        isPersonalQuery = true,
        extractedSlots = if (phoneOrName.isNotBlank()) mapOf("recipient" to phoneOrName) else emptyMap(),
        missingSlots = missing,
        summaryLabel = "SMS composer assistance",
      )
    }

    if (containsAny(lower, "calculate", "হিসাব", "কত হয়", "যোগ", "বিয়োগ", "গুণ") ||
      Regex("\\d+\\s*[+\\-*/×÷]\\s*\\d+").containsMatchIn(clean)
    ) {
      val hasMathExpr = Regex("\\d+").containsMatchIn(clean)
      return IntentAnalysis(
        primaryIntent = BrainIntent.CALCULATION,
        confidence = 0.92f,
        requiresInternet = false,
        isPersonalQuery = false,
        extractedSlots = if (hasMathExpr) mapOf("expression" to clean) else emptyMap(),
        missingSlots = if (!hasMathExpr) listOf("expression") else emptyList(),
        summaryLabel = "Offline calculator",
      )
    }

    if (containsAny(lower, "timer", "টাইমার", "stopwatch", "স্টপওয়াচ")) {
      return IntentAnalysis(
        primaryIntent = BrainIntent.TIMER_OR_STOPWATCH,
        confidence = 0.91f,
        requiresInternet = false,
        isPersonalQuery = false,
        summaryLabel = "Timer / Stopwatch utility",
      )
    }

    if (containsAny(lower, "alarm", "অ্যালার্ম")) {
      return IntentAnalysis(
        primaryIntent = BrainIntent.ALARM_SCHEDULE,
        confidence = 0.91f,
        requiresInternet = false,
        isPersonalQuery = false,
        summaryLabel = "Alarm scheduling",
      )
    }

    // 8. Internet-requiring intents (STRICTLY gated behind Internet permission & consent)
    if (containsAny(lower, "weather", "আবহাওয়া", "তাপমাত্রা", "বৃষ্টি হবে")) {
      val city =
        extractTopicAfterKeywords(
          clean,
          listOf("weather in", "weather of", "weather", "আবহাওয়া কেমন", "আবহাওয়া", "তাপমাত্রা", "বৃষ্টি হবে"),
        ).ifBlank {
          if (context.lastIntent == BrainIntent.WEATHER_LOOKUP) context.activeTopic.orEmpty() else ""
        }
      val missing = if (city.isBlank()) listOf("location") else emptyList()
      return IntentAnalysis(
        primaryIntent = BrainIntent.WEATHER_LOOKUP,
        confidence = 0.93f,
        requiresInternet = true,
        isPersonalQuery = false,
        extractedSlots = if (city.isNotBlank()) mapOf("location" to city) else emptyMap(),
        missingSlots = missing,
        summaryLabel = "Weather lookup (online)",
      )
    }

    if (containsAny(lower, "খবর", "news", "সংক্ষেপে বলো", "summarize")) {
      return IntentAnalysis(
        primaryIntent = BrainIntent.NEWS_SUMMARY,
        confidence = 0.91f,
        requiresInternet = true,
        isPersonalQuery = false,
        extractedSlots = mapOf("topic" to extractTopicAfterKeywords(clean, listOf("খবর", "news", "summarize", "সংক্ষেপে বলো")).ifBlank { "latest news" }),
        summaryLabel = "News & webpage summary (online)",
      )
    }

    if (containsAny(lower, "youtube", "ইউটিউব")) {
      val topic = extractTopicAfterKeywords(clean, listOf("youtube-এ", "youtube", "ইউটিউবে", "ইউটিউব", "সার্চ করো", "search"))
      val missing = if (topic.isBlank()) listOf("query") else emptyList()
      return IntentAnalysis(
        primaryIntent = BrainIntent.YOUTUBE_SEARCH,
        confidence = 0.92f,
        requiresInternet = true,
        isPersonalQuery = false,
        extractedSlots = if (topic.isNotBlank()) mapOf("query" to topic) else emptyMap(),
        missingSlots = missing,
        summaryLabel = "YouTube search (online)",
      )
    }

    if (containsAny(lower, "wikipedia", "উইকিপিডিয়া")) {
      val topic = extractTopicAfterKeywords(clean, listOf("wikipedia", "উইকিপিডিয়া"))
      val missing = if (topic.isBlank()) listOf("query") else emptyList()
      return IntentAnalysis(
        primaryIntent = BrainIntent.WIKIPEDIA_LOOKUP,
        confidence = 0.91f,
        requiresInternet = true,
        isPersonalQuery = false,
        extractedSlots = if (topic.isNotBlank()) mapOf("query" to topic) else emptyMap(),
        missingSlots = missing,
        summaryLabel = "Wikipedia lookup (online)",
      )
    }

    if (containsAny(lower, "google", "খুঁজে দেখো", "search", "ইন্টারনেটে", "ওয়েবে", "গুগল")) {
      val query = extractTopicAfterKeywords(clean, listOf("google-এ", "google", "গুগল", "খুঁজে দেখো", "search for", "search", "ইন্টারনেটে", "ওয়েবে"))
      val missing = if (query.isBlank()) listOf("query") else emptyList()
      return IntentAnalysis(
        primaryIntent = BrainIntent.WEB_RESEARCH,
        confidence = 0.91f,
        requiresInternet = true,
        isPersonalQuery = false,
        extractedSlots = if (query.isNotBlank()) mapOf("query" to query) else emptyMap(),
        missingSlots = missing,
        summaryLabel = "Web research (online)",
      )
    }

    // 9. Personal recall / memory-grounded question ("আমার নাম কী", "আমি কী পছন্দ করি", "আমার সম্পর্কে কী জানো")
    if (containsAny(lower, "আমার নাম", "আমার পছন্দ", "আমার লক্ষ্য", "আমার অভ্যাস", "আমার সম্পর্কে", "my name", "about me", "what do you remember", "what do i like")) {
      return IntentAnalysis(
        primaryIntent = BrainIntent.PERSONAL_RECALL,
        confidence = 0.90f,
        requiresInternet = false,
        isPersonalQuery = true,
        summaryLabel = "Personal memory recall (offline)",
      )
    }

    // 10. Emotional check-in & support (offline empathetic reasoning)
    if (containsAny(lower, "খুশি", "ভালো আছি", "দুঃখ", "মন খারাপ", "চিন্তিত", "টেনশন", "ক্লান্ত", "অবসাদ", "sad", "worried", "anxious", "stressed", "tired", "happy", "excited", "অসাধারণ")) {
      return IntentAnalysis(
        primaryIntent = BrainIntent.EMOTIONAL_SUPPORT,
        confidence = 0.88f,
        requiresInternet = false,
        isPersonalQuery = false,
        summaryLabel = "Empathetic support (offline)",
      )
    }

    // 11. Warm Greeting or Identity Check-in (offline)
    if (containsAny(lower, "কেমন আছো", "কেমন আছেন", "তুমি কে", "আসসালামু আলাইকুম", "সালাম", "নমস্কার", "হ্যালো", "হাই", "hello", "hi", "hey", "how are you", "who are you", "good morning", "good evening", "কয়টা বাজে", "সময় কত", "আজ কী বার", "তারিখ কত", "what time", "what date")) {
      return IntentAnalysis(
        primaryIntent = BrainIntent.GREETING_CHECKIN,
        confidence = 0.90f,
        requiresInternet = false,
        isPersonalQuery = false,
        summaryLabel = "Greeting & offline clock/identity",
      )
    }

    // 12. Open-ended factual question that explicitly asks "কি" / "কী" / "why" / "how" / "what is" without matching offline info
    val looksOnlineFactual =
      containsAny(lower, "who won", "latest score", "current price", "দাম কত", "কে জিতেছে", "বিস্তারিত তথ্য অনলাইনে")
    return IntentAnalysis(
      primaryIntent = BrainIntent.GENERAL_QUESTION,
      confidence = 0.72f,
      requiresInternet = looksOnlineFactual,
      isPersonalQuery = false,
      extractedSlots = if (clean.isNotBlank()) mapOf("topic" to clean) else emptyMap(),
      summaryLabel = if (looksOnlineFactual) "Online factual question" else "Conversational dialogue (offline)",
    )
  }

  fun extractEntities(text: String): List<String> {
    return text
      .replace(Regex("(?i)max,?"), "")
      .split(Regex("[\\s,।?!:]+"))
      .map { it.trim() }
      .filter { it.length >= 3 && it.lowercase() !in STOP_WORDS }
      .take(4)
  }

  private fun looksLikeBrandNewCommand(lower: String): Boolean {
    return containsAny(
      lower,
      "whatsapp", "youtube", "camera", "ক্যামেরা", "gallery", "গ্যালারি", "alarm", "অ্যালার্ম",
      "মনে রাখো", "ভুলে যাও", "ক্যালেন্ডার", "ফ্ল্যাশ", "cancel", "বাতিল",
    )
  }

  private fun isDismissal(lower: String): Boolean {
    return lower == "না" ||
      lower == "no" ||
      lower == "nope" ||
      lower == "done" ||
      containsAny(lower, "না ধন্যবাদ", "আর কিছু না", "থাক এখন", "no thanks", "nothing else", "that is all", "that's all")
  }

  private fun extractPhoneOrTarget(input: String): String {
    val number = Regex("[+\\d][\\d-]{3,}").find(input)?.value
    if (!number.isNullOrBlank()) return number
    val stripped =
      extractTopicAfterKeywords(
        input,
        listOf("কল করো", "ফোন করো", "call", "sms", "মেসেজ পাঠাও", "মেসেজ"),
      )
    val genericWords = setOf("কাউকে", "একজনকে", "কেউ", "someone", "anyone", "person", "friend", "নম্বরে")
    return if (stripped.lowercase() in genericWords) "" else stripped
  }

  private fun extractTopicAfterKeywords(
    input: String,
    keywords: List<String>,
  ): String {
    var result = input.replace(Regex("(?i)max,?"), "")
    keywords.forEach { kw ->
      result = result.replace(Regex(Regex.escape(kw), RegexOption.IGNORE_CASE), "")
    }
    return result
      .replace("একটা", "")
      .replace("কিছু", "")
      .replace("কেমন", "")
      .replace("আজকের", "")
      .replace("সম্পর্কে", "")
      .trim(' ', ',', ':', '?', '।', '-')
  }

  private fun containsAny(text: String, vararg tokens: String): Boolean =
    tokens.any { text.contains(it) }

  companion object {
    val INTERNET_INTENTS =
      setOf(
        BrainIntent.WEATHER_LOOKUP,
        BrainIntent.NEWS_SUMMARY,
        BrainIntent.WEB_RESEARCH,
        BrainIntent.YOUTUBE_SEARCH,
        BrainIntent.WIKIPEDIA_LOOKUP,
      )

    private val STOP_WORDS =
      setOf(
        "the", "and", "for", "with", "about", "what", "how", "can", "you", "max", "please",
        "আমি", "তুমি", "আপনি", "আমার", "এটা", "ওটা", "কি", "কী", "কেমন", "একটা", "বলো", "করো", "দাও",
      )
  }
}
