package com.example.memory

import com.example.utils.ErrorTracker
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Readiness states for the MAX user memory engine.
 */
enum class MemoryEngineState {
  UNINITIALIZED,
  READY,
  DISABLED_BY_PRIVACY,
}

/**
 * Step 1 foundation interface preserved for backwards compatibility.
 */
interface MemoryStore {
  val state: StateFlow<MemoryEngineState>
  val entries: StateFlow<List<MemoryEntry>>

  fun initialize(retentionEnabled: Boolean)

  fun setRetentionEnabled(enabled: Boolean)

  fun remember(category: MemoryCategory, summary: String): Boolean

  fun clearAll()

  fun release()
}

/**
 * MemoryManager — Secure, user-controlled memory engine for MAX.
 *
 * Implements both Step 1 [MemoryStore] and Step 5 [MemoryController]:
 * - Supports User Name, User Preferences, Conversation Context, User Routines, User Selected Settings.
 * - Filters trivial noise and blocks sensitive secrets/credentials unless user explicitly allows.
 * - Supports Bangla memory commands ("MAX, এটা মনে রাখো", "MAX, এটা ভুলে যাও",
 *   "MAX, আমার Memory দেখাও", "MAX, সব Memory মুছে দাও").
 * - Persists securely via [MemoryRepositoryContract] when configured, or in bounded RAM when standalone.
 */
class MemoryManager(
  private val errorTracker: ErrorTracker,
  private val repository: MemoryRepositoryContract? = null,
  private val capacity: Int = MAX_ENTRIES,
  ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : MemoryStore, MemoryController {

  private val lock = Any()
  private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)

  private val legacyBuffer = ArrayDeque<MemoryEntry>(capacity)
  private val structuredBuffer = ArrayDeque<MemoryModel>(capacity)

  private val _state = MutableStateFlow(MemoryEngineState.UNINITIALIZED)
  override val state: StateFlow<MemoryEngineState> = _state.asStateFlow()

  private val _entries = MutableStateFlow<List<MemoryEntry>>(emptyList())
  override val entries: StateFlow<List<MemoryEntry>> = _entries.asStateFlow()

  private val _memories = MutableStateFlow<List<MemoryModel>>(emptyList())
  override val memories: StateFlow<List<MemoryModel>> = _memories.asStateFlow()

  private val _memorySettings = MutableStateFlow(MemorySettings(maxStoredMemories = capacity))
  override val memorySettings: StateFlow<MemorySettings> = _memorySettings.asStateFlow()

  override fun initialize(retentionEnabled: Boolean) {
    _state.value =
      if (retentionEnabled) MemoryEngineState.READY else MemoryEngineState.DISABLED_BY_PRIVACY
    _memorySettings.value = _memorySettings.value.copy(memoryEnabled = retentionEnabled)

    if (repository != null) {
      scope.launch {
        val persistedSettings = runCatching { repository.settingsFlow.first() }.getOrNull()
        if (persistedSettings != null) {
          _memorySettings.value = persistedSettings.copy(memoryEnabled = retentionEnabled)
        }
        if (retentionEnabled) {
          val loaded = repository.loadAllMemories()
          synchronized(lock) {
            structuredBuffer.clear()
            loaded.takeLast(capacity).forEach { structuredBuffer.addLast(it) }
            publishStructuredSnapshotLocked()
          }
        }
      }
    }
    errorTracker.logInfo(MODULE_NAME, "MemoryManager initialized (${_state.value}).")
  }

  override fun setRetentionEnabled(enabled: Boolean) {
    _state.value = if (enabled) MemoryEngineState.READY else MemoryEngineState.DISABLED_BY_PRIVACY
    _memorySettings.value = _memorySettings.value.copy(memoryEnabled = enabled)
    if (!enabled) {
      clearAll()
    }
  }

  override fun remember(category: MemoryCategory, summary: String): Boolean {
    val mappedType =
      when (category) {
        MemoryCategory.USER_PREFERENCE -> MemoryType.USER_PREFERENCE
        MemoryCategory.CONTEXT_NOTE -> MemoryType.CONVERSATION_CONTEXT
        MemoryCategory.SYSTEM_SESSION -> MemoryType.USER_SETTING
      }
    val result = saveMemory(mappedType, summary, hasExplicitPermission = false)
    if (!result.accepted) return false

    synchronized(lock) {
      if (legacyBuffer.size >= capacity) legacyBuffer.removeFirst()
      legacyBuffer.addLast(
        MemoryEntry(
          id = UUID.randomUUID().toString(),
          category = category,
          summary = summary.trim(),
          createdAtMillis = System.currentTimeMillis(),
        ),
      )
      _entries.value = legacyBuffer.reversed()
    }
    return true
  }

  override fun clearAll() {
    deleteAllMemories()
  }

  override fun release() {
    synchronized(lock) {
      legacyBuffer.clear()
      structuredBuffer.clear()
      _entries.value = emptyList()
      _memories.value = emptyList()
    }
    scope.cancel()
    _state.value = MemoryEngineState.UNINITIALIZED
  }

  // --- Step 5 MemoryController API ---

  override fun parseCommand(inputText: String): MemoryCommand? {
    val trimmed = inputText.trim()
    if (trimmed.isEmpty()) return null
    val normalized = trimmed.lowercase().replace(",", " ").replace(WHITESPACE_REGEX, " ")

    return when {
      normalized.contains("সব memory মুছে দাও") ||
        normalized.contains("সব মেমরি মুছে দাও") ||
        normalized.contains("clear all memory") ||
        normalized.contains("delete all memories") -> MemoryCommand.ClearAll

      normalized.contains("আমার memory দেখাও") ||
        normalized.contains("আমার মেমরি দেখাও") ||
        normalized.contains("show my memory") ||
        normalized.contains("show memory") -> MemoryCommand.ShowAll

      normalized.contains("এটা মনে রাখো") || normalized.contains("মনে রাখো") -> {
        val payload = trimmed.replace(BN_REMEMBER_REGEX, "").trim()
        MemoryCommand.Remember(payload)
      }

      normalized.contains("remember that") || normalized.startsWith("remember ") -> {
        val payload = trimmed.replace(EN_REMEMBER_REGEX, "").trim()
        MemoryCommand.Remember(payload)
      }

      normalized.contains("এটা ভুলে যাও") || normalized.contains("ভুলে যাও") -> {
        val query = trimmed.replace(BN_FORGET_REGEX, "").trim()
        MemoryCommand.Forget(query)
      }

      normalized.contains("forget ") -> {
        val query = trimmed.replace(EN_FORGET_REGEX, "").trim()
        MemoryCommand.Forget(query)
      }

      else -> null
    }
  }

  override fun executeCommand(command: MemoryCommand): MemoryActionResult {
    return when (command) {
      is MemoryCommand.Remember -> {
        if (command.payload.isBlank()) {
          MemoryActionResult(
            accepted = false,
            messageBangla = "কী মনে রাখতে হবে তা একটু স্পষ্ট করে বলুন, আমি সাথে সাথেই মনে রাখব।",
            messageEnglish = "Please tell me what you would like me to remember.",
          )
        } else {
          val inferredType = inferMemoryType(command.payload)
          val extractedContent = extractCleanContent(inferredType, command.payload)
          saveMemory(
            type = inferredType,
            content = extractedContent,
            key = if (inferredType == MemoryType.USER_NAME) "name" else "",
            hasExplicitPermission = true,
          )
        }
      }

      is MemoryCommand.Forget -> forgetMatching(command.targetQuery)

      MemoryCommand.ShowAll -> {
        val current = _memories.value
        if (current.isEmpty()) {
          MemoryActionResult(
            accepted = true,
            messageBangla = "আপনার কোনো মেমরি এখনো সংরক্ষিত নেই।",
            messageEnglish = "You have no saved memories right now.",
            affectedItems = emptyList(),
          )
        } else {
          val previewBn =
            current.take(5).joinToString(" | ") { "[${it.type.displayName}] ${it.content}" }
          MemoryActionResult(
            accepted = true,
            messageBangla = "আপনার ${current.size}টি মেমরি আছে: $previewBn",
            messageEnglish = "You have ${current.size} saved memories: $previewBn",
            affectedItems = current,
          )
        }
      }

      MemoryCommand.ClearAll -> deleteAllMemories()
    }
  }

  override fun saveMemory(
    type: MemoryType,
    content: String,
    key: String,
    hasExplicitPermission: Boolean,
  ): MemoryActionResult {
    val cfg = _memorySettings.value
    if (_state.value != MemoryEngineState.READY || !cfg.memoryEnabled) {
      return MemoryActionResult(
        accepted = false,
        messageBangla = "মেমরি এখন প্রাইভেসি সেটিংস দ্বারা বন্ধ আছে।",
        messageEnglish = "Memory saving is currently disabled by your privacy settings.",
      )
    }

    val clean = content.trim()
    if (!isUsefulMemory(clean)) {
      return MemoryActionResult(
        accepted = false,
        messageBangla = "এটি অপ্রয়োজনীয় তথ্য হওয়ায় সংরক্ষণ করা হয়নি।",
        messageEnglish = "Skipped trivial input to keep your memory clean.",
      )
    }

    val sensitive = containsSensitiveInformation(clean)
    if (sensitive && !cfg.allowSensitiveInformation && !hasExplicitPermission) {
      errorTracker.logWarning(MODULE_NAME, "Blocked sensitive data from memory without permission.")
      return MemoryActionResult(
        accepted = false,
        messageBangla = "নিরাপত্তার জন্য পাসওয়ার্ড, পিন বা সংবেদনশীল তথ্য অনুমতি ছাড়া সংরক্ষণ করা হয় না।",
        messageEnglish = "Sensitive information (PIN, password, card, or token) is never saved without permission.",
      )
    }

    val now = System.currentTimeMillis()
    val model =
      MemoryModel(
        type = type,
        key = key,
        content = clean,
        isSensitive = sensitive,
        userApproved = hasExplicitPermission || !sensitive,
        createdAtMillis = now,
        updatedAtMillis = now,
      )

    synchronized(lock) {
      // Replace existing user name or exact duplicate to keep storage lean.
      if (type == MemoryType.USER_NAME) {
        structuredBuffer.removeAll { it.type == MemoryType.USER_NAME }
      } else {
        structuredBuffer.removeAll {
          it.type == type && it.content.equals(clean, ignoreCase = true)
        }
      }

      val maxCap = cfg.maxStoredMemories.coerceAtMost(capacity)
      while (structuredBuffer.size >= maxCap) {
        structuredBuffer.removeFirst()
      }
      structuredBuffer.addLast(model)
      publishStructuredSnapshotLocked()

      if (legacyBuffer.size >= capacity) legacyBuffer.removeFirst()
      legacyBuffer.addLast(
        MemoryEntry(
          id = model.id,
          category =
            when (type) {
              MemoryType.USER_PREFERENCE -> MemoryCategory.USER_PREFERENCE
              MemoryType.USER_SETTING -> MemoryCategory.SYSTEM_SESSION
              else -> MemoryCategory.CONTEXT_NOTE
            },
          summary = clean,
          createdAtMillis = now,
        ),
      )
      _entries.value = legacyBuffer.reversed()
    }

    persistSnapshotAsync()
    errorTracker.logInfo(MODULE_NAME, "Saved ${type.displayName} memory securely.")
    return MemoryActionResult(
      accepted = true,
      messageBangla = "ঠিক আছে, আমি মনে রেখেছি: $clean",
      messageEnglish = "Got it, I will remember: $clean",
      affectedItems = listOf(model),
    )
  }

  override fun deleteMemoryById(id: String): Boolean {
    val removed: Boolean
    synchronized(lock) {
      removed = structuredBuffer.removeAll { it.id == id }
      legacyBuffer.removeAll { it.id == id }
      if (removed) {
        publishStructuredSnapshotLocked()
        _entries.value = legacyBuffer.reversed()
      }
    }
    if (removed) {
      persistSnapshotAsync()
      errorTracker.logInfo(MODULE_NAME, "Deleted memory item $id by user request.")
    }
    return removed
  }

  override fun deleteAllMemories(): MemoryActionResult {
    synchronized(lock) {
      legacyBuffer.clear()
      structuredBuffer.clear()
      _entries.value = emptyList()
      _memories.value = emptyList()
    }
    scope.launch { repository?.clearStorage() }
    errorTracker.logInfo(MODULE_NAME, "All user memories deleted.")
    return MemoryActionResult(
      accepted = true,
      messageBangla = "সব মেমরি সম্পূর্ণভাবে মুছে ফেলা হয়েছে।",
      messageEnglish = "All saved memories have been permanently cleared.",
    )
  }

  override fun getUserName(): String? {
    return _memories.value.firstOrNull { it.type == MemoryType.USER_NAME }?.content
  }

  override fun getMemoriesByType(type: MemoryType): List<MemoryModel> {
    return _memories.value.filter { it.type == type }
  }

  override fun updateMemorySettings(settings: MemorySettings) {
    _memorySettings.value = settings
    _state.value =
      if (settings.memoryEnabled) MemoryEngineState.READY else MemoryEngineState.DISABLED_BY_PRIVACY
    scope.launch { repository?.saveSettings(settings) }
  }

  private fun forgetMatching(query: String): MemoryActionResult {
    val cleanQuery = query.trim()
    val removedItems: List<MemoryModel>
    synchronized(lock) {
      if (cleanQuery.isBlank()) {
        val last = structuredBuffer.removeLastOrNull()
        removedItems = if (last != null) listOf(last) else emptyList()
      } else {
        removedItems =
          structuredBuffer.filter { it.content.contains(cleanQuery, ignoreCase = true) }
        structuredBuffer.removeAll { it.content.contains(cleanQuery, ignoreCase = true) }
      }
      if (removedItems.isNotEmpty()) {
        val removedIds = removedItems.map { it.id }.toHashSet()
        legacyBuffer.removeAll { it.id in removedIds }
        publishStructuredSnapshotLocked()
        _entries.value = legacyBuffer.reversed()
      }
    }

    if (removedItems.isEmpty()) {
      return MemoryActionResult(
        accepted = false,
        messageBangla = "এই তথ্যের সাথে মিলে এমন কোনো মেমরি খুঁজে পাওয়া যায়নি।",
        messageEnglish = "I could not find a matching memory to forget.",
      )
    }

    persistSnapshotAsync()
    return MemoryActionResult(
      accepted = true,
      messageBangla = "ঠিক আছে, আমি বিষয়টি ভুলে গেছি।",
      messageEnglish = "Done, I have forgotten that.",
      affectedItems = removedItems,
    )
  }

  private fun publishStructuredSnapshotLocked() {
    _memories.value = structuredBuffer.reversed()
  }

  private fun persistSnapshotAsync() {
    val repo = repository ?: return
    val snapshot = _memories.value.reversed()
    val encrypt = _memorySettings.value.encryptAtRest
    scope.launch {
      repo.saveAllMemories(snapshot, encrypt)
    }
  }

  private fun isUsefulMemory(text: String): Boolean {
    if (text.length < 3) return false
    val lower = text.lowercase()
    return lower !in TRIVIAL_NOISE
  }

  private fun containsSensitiveInformation(text: String): Boolean {
    val lower = text.lowercase()
    if (SENSITIVE_KEYWORDS.any { lower.contains(it) }) return true
    // Detect 12+ digit card/NID sequences or 4-6 digit explicit PIN/OTP patterns.
    if (CARD_NUMBER_REGEX.containsMatchIn(text)) return true
    return false
  }

  private fun inferMemoryType(payload: String): MemoryType {
    val lower = payload.lowercase()
    return when {
      lower.contains("আমার নাম") || lower.contains("my name is") || lower.contains("call me") ->
        MemoryType.USER_NAME
      lower.contains("প্রতিদিন") ||
        lower.contains("সকালে") ||
        lower.contains("রাতে") ||
        lower.contains("every day") ||
        lower.contains("routine") ||
        lower.contains("morning") -> MemoryType.USER_ROUTINE
      lower.contains("পছন্দ") ||
        lower.contains("ভালোবাসি") ||
        lower.contains("like") ||
        lower.contains("prefer") ||
        lower.contains("favorite") -> MemoryType.USER_PREFERENCE
      lower.contains("setting") || lower.contains("সেটিং") || lower.contains("ভাষা") ->
        MemoryType.USER_SETTING
      else -> MemoryType.CONVERSATION_CONTEXT
    }
  }

  private fun extractCleanContent(type: MemoryType, payload: String): String {
    if (type != MemoryType.USER_NAME) return payload
    return payload
      .replace(NAME_PREFIX_REGEX, "")
      .trim()
      .ifEmpty { payload }
  }

  companion object {
    private const val MODULE_NAME = "MemoryManager"
    private const val MAX_ENTRIES = 50

    private val WHITESPACE_REGEX = Regex("\\s+")
    private val BN_REMEMBER_REGEX = Regex("^(?i)(max[,:\\s-]*)?(এটা\\s*)?মনে\\s*রাখো[:\\s-]*")
    private val EN_REMEMBER_REGEX = Regex("^(?i)(max[,:\\s-]*)?remember(\\s+that)?[:\\s-]*")
    private val BN_FORGET_REGEX = Regex("^(?i)(max[,:\\s-]*)?(এটা\\s*)?ভুলে\\s*যাও[:\\s-]*")
    private val EN_FORGET_REGEX = Regex("^(?i)(max[,:\\s-]*)?forget[:\\s-]*")
    private val CARD_NUMBER_REGEX = Regex("\\b\\d{12,19}\\b")
    private val NAME_PREFIX_REGEX = Regex("^(?i)(আমার নাম|my name is|call me)\\s*")

    private val TRIVIAL_NOISE =
      setOf("ok", "okay", "hi", "hello", "hmm", "yes", "no", "হ্যাঁ", "না", "ওকে")

    private val SENSITIVE_KEYWORDS =
      listOf(
        "password",
        "passcode",
        "pin",
        "otp",
        "cvv",
        "credit card",
        "bank account",
        "secret key",
        "api key",
        "পাসওয়ার্ড",
        "পাসওয়ার্ড",
        "পিন",
        "ওটিপি",
        "ব্যাংক অ্যাকাউন্ট",
      )
  }
}
