package com.example.phone

/**
 * Supported utilities and device-control capabilities inside MAX's Phone Control System.
 */
enum class PhoneFeature(val displayName: String, val displayBangla: String) {
  CALL("Phone Calls", "ফোন কল"),
  SMS("SMS", "এসএমএস"),
  CONTACTS("Contacts", "কন্টাক্টস"),
  ALARM("Alarm", "অ্যালার্ম"),
  TIMER("Timer", "টাইমার"),
  STOPWATCH("Stopwatch", "স্টপওয়াচ"),
  CALCULATOR("Calculator", "ক্যালকুলেটর"),
  FLASHLIGHT("Flashlight", "ফ্ল্যাশলাইট"),
}

/**
 * Volatile contact record returned only after explicit user lookup.
 * MAX never stores or syncs the user's address book in background storage.
 */
data class PhoneContact(
  val id: String,
  val displayName: String,
  val phoneNumber: String,
)

/**
 * Session-scoped record representing an alarm request confirmed by the user.
 */
data class AlarmEntry(
  val id: String,
  val hour: Int,
  val minute: Int,
  val label: String,
  val createdAtMillis: Long,
) {
  val formattedTime: String
    get() = "%02d:%02d".format(hour, minute)
}

/**
 * Live state for MAX's low-battery countdown timer.
 */
data class TimerStatus(
  val isRunning: Boolean = false,
  val isPaused: Boolean = false,
  val totalSeconds: Int = 0,
  val remainingSeconds: Int = 0,
  val label: String = "MAX Timer",
  val isFinished: Boolean = false,
)

/**
 * Live state for MAX's drift-free stopwatch.
 */
data class StopwatchStatus(
  val isRunning: Boolean = false,
  val elapsedMillis: Long = 0L,
  val laps: List<Long> = emptyList(),
)

/**
 * Single arithmetic evaluation record stored cleanly in volatile session memory.
 */
data class CalculationRecord(
  val expression: String,
  val result: String,
)

/**
 * Sensitive device actions that ALWAYS require explicit confirmation from the user
 * before launching system hardware, intents, or modifications.
 */
sealed interface PhoneConfirmationAction {
  data class PlaceCall(
    val recipientName: String?,
    val phoneNumber: String,
    val directCallRequested: Boolean,
  ) : PhoneConfirmationAction

  data class ComposeSms(
    val recipientName: String?,
    val phoneNumber: String,
    val messageBody: String,
  ) : PhoneConfirmationAction

  data class EditOrAddContact(
    val name: String,
    val phoneNumber: String,
    val email: String? = null,
  ) : PhoneConfirmationAction

  data class SetAlarm(
    val hour: Int,
    val minute: Int,
    val label: String,
  ) : PhoneConfirmationAction

  data class ToggleFlashlight(
    val enable: Boolean,
  ) : PhoneConfirmationAction
}

/**
 * Pending interactive confirmation presented to the user before executing a sensitive action.
 */
data class PendingPhoneConfirmation(
  val id: String,
  val feature: PhoneFeature,
  val action: PhoneConfirmationAction,
  val titleBangla: String,
  val titleEnglish: String,
  val messageBangla: String,
  val messageEnglish: String,
)

/**
 * Reactive state machine for the MAX Phone Control System.
 * Begins in [Dormant] and never executes sensitive actions automatically.
 */
sealed class PhoneState {
  data object Dormant : PhoneState()

  data class AwaitingConfirmation(
    val pending: PendingPhoneConfirmation,
  ) : PhoneState()

  data class PermissionRequired(
    val feature: PhoneFeature,
    val explanation: String,
  ) : PhoneState()

  data class BlockedByPrivacyPolicy(
    val feature: PhoneFeature,
    val reason: String,
  ) : PhoneState()

  data class UnsupportedFallback(
    val feature: PhoneFeature,
    val reason: String,
  ) : PhoneState()

  data class ActionReady(
    val feature: PhoneFeature,
    val message: String,
  ) : PhoneState()

  data class Error(
    val message: String,
  ) : PhoneState()
}
