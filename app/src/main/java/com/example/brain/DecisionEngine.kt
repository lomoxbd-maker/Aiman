package com.example.brain

import com.example.permissions.AppPermission
import com.example.permissions.PermissionRequestHandler
import com.example.permissions.PrivacyCheckResult
import com.example.permissions.PrivacyGuard
import com.example.permissions.SensitiveAction
import com.example.tts.VoiceLanguage

/**
 * DecisionEngine — Plans MAX's safest and smartest execution path BEFORE generating a response.
 *
 * Enforces:
 * 1. Offline-first execution (never uses internet for offline tasks).
 * 2. Missing-slot clarification (asks follow-up questions before guessing).
 * 3. Privacy & consent gate for internet-dependent tasks (asks permission cleanly; never browses secretly).
 */
class DecisionEngine(
  private val privacyGuard: PrivacyGuard? = null,
  private val permissionRequestHandler: PermissionRequestHandler? = null,
) {

  fun decide(
    rawInput: String,
    analysis: IntentAnalysis,
    context: ConversationContextSnapshot,
    language: VoiceLanguage,
    hasDeviceActionMatch: Boolean,
  ): BrainDecision {
    val preferBangla = language != VoiceLanguage.ENGLISH_US

    // 1. Clarification resolution rewrites the pending intent with the newly provided slot
    if (analysis.primaryIntent == BrainIntent.CLARIFICATION_ANSWER && context.pendingClarification != null) {
      val pending = context.pendingClarification
      val answer = analysis.extractedSlots[pending.missingSlot].orEmpty()
      val mergedTopic = answer.ifBlank { pending.originalQuery }
      if (pending.intent in IntentAnalyzer.INTERNET_INTENTS) {
        return evaluateInternetGate(
          intent = pending.intent,
          topic = mergedTopic,
          rewrittenQuery = "${pending.originalQuery} $answer".trim(),
          preferBangla = preferBangla,
        )
      }
      return BrainDecision(
        intent = pending.intent,
        mode = ExecutionMode.DEVICE_ACTION_DISPATCH,
        shouldUseInternet = false,
        requiresInternetConsent = false,
        delegateToActionHandler = true,
        rewrittenCommand = answer,
        suggestedTopic = mergedTopic,
      )
    }

    // 2. Ask a helpful follow-up question when essential information is missing
    if (analysis.missingSlots.isNotEmpty()) {
      val missing = analysis.missingSlots.first()
      val question = buildMissingInfoQuestion(analysis.primaryIntent, missing, preferBangla)
      return BrainDecision(
        intent = analysis.primaryIntent,
        mode = ExecutionMode.NEEDS_CLARIFICATION,
        shouldUseInternet = false,
        requiresInternetConsent = false,
        missingSlotQuestion = question,
        delegateToActionHandler = false,
        suggestedTopic = analysis.extractedSlots.values.firstOrNull(),
      )
    }

    // 3. Follow-up continuation on an active topic
    if (analysis.primaryIntent == BrainIntent.FOLLOW_UP_CONTINUATION && context.activeTopic != null) {
      val topic = context.activeTopic
      if (analysis.requiresInternet) {
        return evaluateInternetGate(
          intent = BrainIntent.WEB_RESEARCH,
          topic = topic,
          rewrittenQuery = if (preferBangla) "Google-এ $topic সম্পর্কে বিস্তারিত খুঁজে দেখো" else "search $topic details",
          preferBangla = preferBangla,
        )
      }
      return BrainDecision(
        intent = BrainIntent.FOLLOW_UP_CONTINUATION,
        mode = ExecutionMode.OFFLINE_LOCAL,
        shouldUseInternet = false,
        requiresInternetConsent = false,
        delegateToActionHandler = false,
        suggestedTopic = topic,
      )
    }

    // 4. Online intents — verify privacy & internet permission first
    if (analysis.requiresInternet) {
      val topic =
        analysis.extractedSlots["location"]
          ?: analysis.extractedSlots["query"]
          ?: analysis.extractedSlots["topic"]
          ?: rawInput.trim()
      return evaluateInternetGate(
        intent = analysis.primaryIntent,
        topic = topic,
        rewrittenQuery = rawInput.trim(),
        preferBangla = preferBangla,
      )
    }

    // 5. Device / App / Utility / Vision / Memory actions work completely offline
    if (hasDeviceActionMatch || analysis.primaryIntent in LOCAL_ACTION_INTENTS) {
      return BrainDecision(
        intent = analysis.primaryIntent,
        mode = ExecutionMode.DEVICE_ACTION_DISPATCH,
        shouldUseInternet = false,
        requiresInternetConsent = false,
        delegateToActionHandler = true,
        suggestedTopic = analysis.extractedSlots.values.firstOrNull() ?: analysis.summaryLabel,
      )
    }

    // 6. Pure offline conversational / memory / emotional reasoning
    return BrainDecision(
      intent = analysis.primaryIntent,
      mode = ExecutionMode.OFFLINE_LOCAL,
      shouldUseInternet = false,
      requiresInternetConsent = false,
      delegateToActionHandler = false,
      suggestedTopic = analysis.extractedSlots.values.firstOrNull() ?: context.activeTopic,
    )
  }

  private fun evaluateInternetGate(
    intent: BrainIntent,
    topic: String,
    rewrittenQuery: String,
    preferBangla: Boolean,
  ): BrainDecision {
    val auth =
      privacyGuard?.authorizeAction(SensitiveAction.BROWSE_INTERNET, initiatedByUser = true)
    if (auth is PrivacyCheckResult.Denied) {
      permissionRequestHandler?.requestForExplicitAction(AppPermission.INTERNET, preferBangla)
      return BrainDecision(
        intent = intent,
        mode = ExecutionMode.REQUIRES_INTERNET_PERMISSION,
        shouldUseInternet = false,
        requiresInternetConsent = true,
        delegateToActionHandler = false,
        rewrittenCommand = rewrittenQuery,
        suggestedTopic = topic,
      )
    }

    return BrainDecision(
      intent = intent,
      mode = ExecutionMode.INTERNET_EXECUTION,
      shouldUseInternet = true,
      requiresInternetConsent = false,
      delegateToActionHandler = true,
      rewrittenCommand = rewrittenQuery,
      suggestedTopic = topic,
    )
  }

  fun buildMissingInfoQuestion(
    intent: BrainIntent,
    missingSlot: String,
    preferBangla: Boolean,
  ): String {
    return when (intent) {
      BrainIntent.WEATHER_LOOKUP ->
        if (preferBangla) "অবশ্যই সানজু। কোন শহর বা এলাকার আবহাওয়া দেখতে চান?"
        else "Of course, Sanju. Which city or area would you like the weather for?"
      BrainIntent.CALL_CONTACT ->
        if (preferBangla) "সানজু, আপনি কাকে বা কোন নম্বরে কল করতে চান? বললেই আমি প্রস্তুত করে দিচ্ছি।"
        else "Who or which number would you like to call, Sanju?"
      BrainIntent.SEND_SMS ->
        if (preferBangla) "সানজু, কাকে মেসেজ পাঠাতে চান এবং কী লিখতে হবে?"
        else "Who should I prepare this SMS for, and what message would you like to send?"
      BrainIntent.TRANSLATION ->
        if (preferBangla) "অবশ্যই, কোন বাক্য বা শব্দটি অনুবাদ করতে চান? আমাকে বলুন।"
        else "Sure, which word or sentence would you like me to translate?"
      BrainIntent.YOUTUBE_SEARCH ->
        if (preferBangla) "YouTube-এ ঠিক কী বিষয় খুঁজে দেখতে চান?"
        else "What would you like me to search on YouTube?"
      BrainIntent.WEB_RESEARCH, BrainIntent.WIKIPEDIA_LOOKUP ->
        if (preferBangla) "কোন বিষয়টি সম্পর্কে খুঁজে দেখতে চান বলুন?"
        else "What topic would you like me to look up?"
      BrainIntent.CALCULATION ->
        if (preferBangla) "কোন হিসাবটি করে দিতে হবে? সংখ্যাগুলো বলুন বা লিখুন।"
        else "Which math expression would you like me to calculate?"
      else ->
        if (preferBangla) "সানজু, বিষয়টি আরেকটু পরিষ্কার করে বলবেন যেন সঠিকভাবে সাহায্য করতে পারি?"
        else "Could you share a bit more detail on '$missingSlot' so I can help accurately?"
    }
  }

  companion object {
    private val LOCAL_ACTION_INTENTS =
      setOf(
        BrainIntent.APP_LAUNCH,
        BrainIntent.CAMERA_VISION,
        BrainIntent.GALLERY_MEDIA,
        BrainIntent.FILE_DOCUMENT,
        BrainIntent.FLASHLIGHT_CONTROL,
        BrainIntent.TRANSLATION,
        BrainIntent.REMINDER_OR_NOTE,
        BrainIntent.CALENDAR_SCHEDULE,
        BrainIntent.CALL_CONTACT,
        BrainIntent.SEND_SMS,
        BrainIntent.CALCULATION,
        BrainIntent.TIMER_OR_STOPWATCH,
        BrainIntent.ALARM_SCHEDULE,
      )
  }
}
