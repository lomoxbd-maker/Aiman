package com.example.brain

import com.example.emotion.EmotionControllerContract
import com.example.emotion.EmotionState
import com.example.emotion.EmotionType
import com.example.emotion.PersonalityController
import com.example.memory.MemoryController
import com.example.permissions.PermissionRequestHandler
import com.example.permissions.PrivacyGuard
import com.example.tts.TTSController
import com.example.tts.VoiceLanguage
import com.example.utils.ErrorTracker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class BrainOutcome(
  val emotionState: EmotionState,
  val reply: String,
  val decision: BrainDecision,
  val usedInternet: Boolean,
)

/**
 * AIBrainManager — Central cognitive coordinator for MAX.
 *
 * Thinks before responding by uniting:
 * - [IntentAnalyzer] (semantic intent & slot extraction),
 * - [ContextManager] (active multi-turn conversation memory),
 * - [DecisionEngine] (offline-first, clarification & privacy permission gates), and
 * - [ResponsePlanner] (Emotion + Memory + Personality natural phrasing),
 * connected cleanly with ConversationManager, InternetManager, and TTSManager.
 */
class AIBrainManager(
  private val emotionManager: EmotionControllerContract,
  private val memoryController: MemoryController? = null,
  private val personalityManager: PersonalityController? = null,
  privacyGuard: PrivacyGuard? = null,
  permissionRequestHandler: PermissionRequestHandler? = null,
  private val ttsController: TTSController? = null,
  private val errorTracker: ErrorTracker? = null,
  val intentAnalyzer: IntentAnalyzer = IntentAnalyzer(),
  val contextManager: ContextManager = ContextManager(),
  val decisionEngine: DecisionEngine = DecisionEngine(privacyGuard, permissionRequestHandler),
  val responsePlanner: ResponsePlanner =
    ResponsePlanner(
      emotionManager = emotionManager,
      memoryController = memoryController,
      personalityManager = personalityManager,
    ),
) {

  private val _brainState = MutableStateFlow<BrainLifecycleState>(BrainLifecycleState.Dormant)
  val brainState: StateFlow<BrainLifecycleState> = _brainState.asStateFlow()

  val contextSnapshot: StateFlow<ConversationContextSnapshot> = contextManager.snapshot

  fun thinkAndPlan(
    userInput: String,
    language: VoiceLanguage,
    actionExecutor: ((String) -> String?)? = null,
  ): BrainOutcome {
    val cleanInput = userInput.trim()
    val preferBangla = language != VoiceLanguage.ENGLISH_US

    return try {
      _brainState.value = BrainLifecycleState.Thinking("Understanding intent & context")
      val currentContext = contextManager.snapshot.value
      val analysis = intentAnalyzer.analyze(cleanInput, currentContext)
      val entities = intentAnalyzer.extractEntities(cleanInput)
      contextManager.recordTurn(
        speaker = "User",
        text = cleanInput,
        intent = analysis.primaryIntent,
        topic = analysis.extractedSlots.values.firstOrNull(),
        entities = entities,
      )

      _brainState.value = BrainLifecycleState.Thinking("Planning offline/online strategy")
      val decision =
        decisionEngine.decide(
          rawInput = cleanInput,
          analysis = analysis,
          context = currentContext,
          language = language,
          hasDeviceActionMatch = actionExecutor != null && isCommandRoutable(analysis.primaryIntent),
        )

      val outcome =
        when (decision.mode) {
          ExecutionMode.NEEDS_CLARIFICATION -> {
            val missingSlot = analysis.missingSlots.firstOrNull() ?: "details"
            val qBangla = decisionEngine.buildMissingInfoQuestion(analysis.primaryIntent, missingSlot, true)
            val qEnglish = decisionEngine.buildMissingInfoQuestion(analysis.primaryIntent, missingSlot, false)
            contextManager.setPendingClarification(
              PendingClarification(
                intent = analysis.primaryIntent,
                missingSlot = missingSlot,
                originalQuery = cleanInput,
                questionBangla = qBangla,
                questionEnglish = qEnglish,
              ),
            )
            val (emState, reply) =
              responsePlanner.planClarificationAsk(
                question = decision.missingSlotQuestion ?: if (preferBangla) qBangla else qEnglish,
                language = language,
              )
            _brainState.value = BrainLifecycleState.AwaitingClarification(missingSlot, analysis.primaryIntent)
            BrainOutcome(emState, reply, decision, usedInternet = false)
          }

          ExecutionMode.REQUIRES_INTERNET_PERMISSION -> {
            val topic = decision.suggestedTopic ?: cleanInput
            val (emState, reply) = responsePlanner.planInternetPermissionAsk(topic, language)
            _brainState.value = BrainLifecycleState.AskingInternetConsent(topic)
            BrainOutcome(emState, reply, decision, usedInternet = false)
          }

          ExecutionMode.DEVICE_ACTION_DISPATCH, ExecutionMode.INTERNET_EXECUTION -> {
            contextManager.clearPendingClarification()
            val commandText =
              if (analysis.primaryIntent == BrainIntent.CLARIFICATION_ANSWER &&
                currentContext.pendingClarification != null
              ) {
                contextManager.buildResolvedClarificationCommand(
                  pending = currentContext.pendingClarification,
                  answer = cleanInput,
                  language = language,
                )
              } else {
                decision.rewrittenCommand ?: cleanInput
              }
            val delegatedReply = actionExecutor?.invoke(commandText)
            if (!delegatedReply.isNullOrBlank()) {
              emotionManager.setEmotion(
                EmotionType.SUPPORTIVE,
                "AI Brain action completed",
              )
              val emState = emotionManager.emotionState.value
              _brainState.value =
                BrainLifecycleState.Responded(
                  intent = decision.intent,
                  usedInternet = decision.shouldUseInternet,
                )
              BrainOutcome(emState, delegatedReply, decision, usedInternet = decision.shouldUseInternet)
            } else {
              val (emState, offlineReply) =
                responsePlanner.planOfflineReasoningReply(cleanInput, analysis, contextManager.snapshot.value, language)
              _brainState.value = BrainLifecycleState.Responded(decision.intent, usedInternet = false)
              BrainOutcome(emState, offlineReply, decision, usedInternet = false)
            }
          }

          ExecutionMode.OFFLINE_LOCAL -> {
            contextManager.clearPendingClarification()
            val (emState, reply) =
              responsePlanner.planOfflineReasoningReply(cleanInput, analysis, contextManager.snapshot.value, language)
            _brainState.value = BrainLifecycleState.Responded(decision.intent, usedInternet = false)
            BrainOutcome(emState, reply, decision, usedInternet = false)
          }

          ExecutionMode.GRACEFUL_ERROR -> {
            val (emState, reply) = responsePlanner.planFriendlyErrorReply(null, language)
            _brainState.value = BrainLifecycleState.ErrorFallback(reply)
            BrainOutcome(emState, reply, decision, usedInternet = false)
          }
        }

      contextManager.recordTurn(
        speaker = "MAX",
        text = outcome.reply,
        intent = outcome.decision.intent,
        topic = outcome.decision.suggestedTopic,
      )
      ttsController?.applyEmotionalVoiceProfile(outcome.emotionState.voiceProfile)
      errorTracker?.logInfo(
        MODULE_NAME,
        "Thought cleanly -> intent=${outcome.decision.intent}, mode=${outcome.decision.mode}, internet=${outcome.usedInternet}",
      )
      outcome
    } catch (e: Exception) {
      errorTracker?.logError(MODULE_NAME, "AI Brain recovered cleanly from error", e)
      val (emState, friendly) = responsePlanner.planFriendlyErrorReply(e.message, language)
      _brainState.value = BrainLifecycleState.ErrorFallback(friendly)
      BrainOutcome(
        emotionState = emState,
        reply = friendly,
        decision =
          BrainDecision(
            intent = BrainIntent.GENERAL_QUESTION,
            mode = ExecutionMode.GRACEFUL_ERROR,
            shouldUseInternet = false,
            requiresInternetConsent = false,
          ),
        usedInternet = false,
      )
    }
  }

  fun clearSessionContext() {
    contextManager.clearActiveSession()
    _brainState.value = BrainLifecycleState.Dormant
  }

  fun release() {
    contextManager.clearActiveSession()
    _brainState.value = BrainLifecycleState.Dormant
  }

  private fun isCommandRoutable(intent: BrainIntent): Boolean {
    return intent !in setOf(
      BrainIntent.GREETING_CHECKIN,
      BrainIntent.EMOTIONAL_SUPPORT,
      BrainIntent.PERSONAL_RECALL,
      BrainIntent.DISMISSAL,
    )
  }

  companion object {
    private const val MODULE_NAME = "AIBrainManager"
  }
}
