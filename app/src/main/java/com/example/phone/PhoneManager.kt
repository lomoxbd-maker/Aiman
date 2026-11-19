package com.example.phone

import android.net.Uri
import com.example.permissions.AppPermission
import com.example.permissions.PermissionController
import com.example.permissions.PermissionRequestHandler
import com.example.permissions.PermissionStatus
import com.example.permissions.PrivacyCheckResult
import com.example.permissions.PrivacyGuard
import com.example.permissions.SensitiveAction
import com.example.utils.ErrorTracker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Listener notified by [PhoneManager] so [com.example.core.MAXCore],
 * [com.example.conversation.ConversationManager], and [com.example.tts.TTSManager]
 * can deliver calm spoken confirmations and alerts.
 */
interface PhoneEventListener {
  fun onPhoneAnnouncement(bangla: String, english: String, speakAloud: Boolean = true)
}

/**
 * Contract for MAX's unified Phone Control subsystem.
 */
interface PhoneController : PhoneCapability {
  val state: StateFlow<PhoneState>
  val contacts: StateFlow<List<PhoneContact>>
  val scheduledAlarms: StateFlow<List<AlarmEntry>>
  val timerStatus: StateFlow<TimerStatus>
  val stopwatchStatus: StateFlow<StopwatchStatus>
  val calculationHistory: StateFlow<List<CalculationRecord>>
  val isTorchOn: StateFlow<Boolean>
  val pendingConfirmation: StateFlow<PendingPhoneConfirmation?>

  fun requestCall(
    phoneNumberOrName: String,
    recipientName: String? = null,
    preferDirectCall: Boolean = false,
    initiatedByUser: Boolean,
    preferBangla: Boolean = false,
  ): Boolean

  fun requestSms(
    phoneNumberOrName: String,
    messageBody: String,
    recipientName: String? = null,
    initiatedByUser: Boolean,
  ): Boolean

  fun searchContacts(query: String, initiatedByUser: Boolean, preferBangla: Boolean = false): Boolean

  fun onSystemContactPicked(uri: Uri): PhoneContact?

  fun requestAddOrEditContact(
    name: String,
    phoneNumber: String,
    email: String? = null,
    initiatedByUser: Boolean,
  ): Boolean

  fun requestSetAlarm(
    hour: Int,
    minute: Int,
    label: String,
    initiatedByUser: Boolean,
  ): Boolean

  fun openSystemAlarms(initiatedByUser: Boolean): Boolean

  fun startTimer(durationSeconds: Int, label: String = "MAX Timer", initiatedByUser: Boolean): Boolean

  fun pauseTimer()

  fun resumeTimer()

  fun resetTimer()

  fun startStopwatch(initiatedByUser: Boolean): Boolean

  fun pauseStopwatch()

  fun lapStopwatch()

  fun resetStopwatch()

  fun calculate(expression: String, initiatedByUser: Boolean): CalculationRecord?

  fun requestToggleFlashlight(enable: Boolean, initiatedByUser: Boolean, requireConfirmation: Boolean = true): Boolean

  fun confirmPendingAction(): Boolean

  fun cancelPendingAction()

  fun releaseTaskResources()

  fun release()
}

/**
 * PhoneManager — Central coordinator for Phone Calls, SMS, Contacts, Alarm, Timer, Stopwatch,
 * Calculator, and Flashlight.
 *
 * Strict privacy & confirmation guarantees:
 * - Never triggers calls, SMS, contact edits, alarms, or flashlight automatically.
 * - Sensitive actions always stage a [PendingPhoneConfirmation] requiring explicit user confirmation.
 * - Asks for runtime permissions cleanly through [PermissionRequestHandler] only when required.
 * - Never reads call logs or SMS messages automatically.
 */
class PhoneManager(
  private val permissionController: PermissionController,
  private val privacyGuard: PrivacyGuard,
  private val permissionRequestHandler: PermissionRequestHandler,
  private val callManager: CallManager,
  private val smsManager: SMSManager,
  private val contactsManager: ContactsManager,
  private val alarmManager: AlarmManager,
  private val timerManager: TimerManager,
  private val stopwatchManager: StopwatchManager,
  private val calculatorManager: CalculatorManager,
  private val flashlightManager: FlashlightManager,
  private val errorTracker: ErrorTracker,
) : PhoneController {

  private var eventListener: PhoneEventListener? = null

  private val _state = MutableStateFlow<PhoneState>(PhoneState.Dormant)
  override val state: StateFlow<PhoneState> = _state.asStateFlow()

  private val _pendingConfirmation = MutableStateFlow<PendingPhoneConfirmation?>(null)
  override val pendingConfirmation: StateFlow<PendingPhoneConfirmation?> =
    _pendingConfirmation.asStateFlow()

  override val contacts: StateFlow<List<PhoneContact>> = contactsManager.searchResults
  override val scheduledAlarms: StateFlow<List<AlarmEntry>> = alarmManager.scheduledAlarms
  override val timerStatus: StateFlow<TimerStatus> = timerManager.status
  override val stopwatchStatus: StateFlow<StopwatchStatus> = stopwatchManager.status
  override val calculationHistory: StateFlow<List<CalculationRecord>> = calculatorManager.history
  override val isTorchOn: StateFlow<Boolean> = flashlightManager.isTorchOn

  override val isPrivacyLocked: Boolean = false
  override val isTelephonySupported: Boolean
    get() = callManager.isTelephonySupported()

  init {
    timerManager.setOnCompletedListener { completedLabel ->
      eventListener?.onPhoneAnnouncement(
        bangla = "'$completedLabel' টাইমারের সময় শেষ হয়েছে।",
        english = "Your timer '$completedLabel' has finished.",
        speakAloud = true,
      )
    }
  }

  fun setEventListener(listener: PhoneEventListener?) {
    eventListener = listener
  }

  override fun requestCall(
    phoneNumberOrName: String,
    recipientName: String?,
    preferDirectCall: Boolean,
    initiatedByUser: Boolean,
    preferBangla: Boolean,
  ): Boolean {
    if (!verifyUserInitiated(PhoneFeature.CALL, initiatedByUser)) return false

    if (preferDirectCall) {
      val auth = privacyGuard.authorizeAction(SensitiveAction.MAKE_CALL, initiatedByUser = true)
      if (auth is PrivacyCheckResult.Denied) {
        permissionRequestHandler.requestForExplicitAction(AppPermission.PHONE_CALL, preferBangla)
        _state.value = PhoneState.PermissionRequired(PhoneFeature.CALL, auth.reason)
        return false
      }
    }

    val cleanNumber = callManager.normalizePhoneNumber(phoneNumberOrName)
    if (cleanNumber.isEmpty()) {
      _state.value = PhoneState.Error("Please provide a valid phone number.")
      return false
    }

    val displayTarget = recipientName?.takeIf { it.isNotBlank() }?.let { "$it ($cleanNumber)" } ?: cleanNumber
    val pending =
      PendingPhoneConfirmation(
        id = "confirm_call_${System.currentTimeMillis()}",
        feature = PhoneFeature.CALL,
        action = PhoneConfirmationAction.PlaceCall(recipientName, cleanNumber, preferDirectCall),
        titleBangla = "কল নিশ্চিত করুন",
        titleEnglish = "Confirm Phone Call",
        messageBangla = "আপনি কি $displayTarget নম্বরে কল করতে চান?",
        messageEnglish = "Do you want to place a call to $displayTarget?",
      )
    stageConfirmation(pending)
    return true
  }

  override fun requestSms(
    phoneNumberOrName: String,
    messageBody: String,
    recipientName: String?,
    initiatedByUser: Boolean,
  ): Boolean {
    if (!verifyUserInitiated(PhoneFeature.SMS, initiatedByUser)) return false

    val cleanNumber = callManager.normalizePhoneNumber(phoneNumberOrName)
    if (cleanNumber.isEmpty()) {
      _state.value = PhoneState.Error("Please provide a recipient phone number for SMS.")
      return false
    }

    val displayTarget = recipientName?.takeIf { it.isNotBlank() }?.let { "$it ($cleanNumber)" } ?: cleanNumber
    val cleanBody = messageBody.trim()
    val pending =
      PendingPhoneConfirmation(
        id = "confirm_sms_${System.currentTimeMillis()}",
        feature = PhoneFeature.SMS,
        action = PhoneConfirmationAction.ComposeSms(recipientName, cleanNumber, cleanBody),
        titleBangla = "SMS পাঠানোর আগে নিশ্চিত করুন",
        titleEnglish = "Confirm SMS Message",
        messageBangla = "প্রাপক: $displayTarget\nবার্তা: ${cleanBody.ifEmpty { "(খালি বার্তা)" }}\nSMS অ্যাপ খুলবেন?",
        messageEnglish = "Open SMS composer to $displayTarget with message: \"${cleanBody.ifEmpty { "(empty)" }}\"?",
      )
    stageConfirmation(pending)
    return true
  }

  override fun searchContacts(
    query: String,
    initiatedByUser: Boolean,
    preferBangla: Boolean,
  ): Boolean {
    if (!verifyUserInitiated(PhoneFeature.CONTACTS, initiatedByUser)) return false

    val policyAllowed = privacyGuard.policy.value.isAllowedByUser(AppPermission.CONTACTS)
    val osGranted = permissionController.checkStatus(AppPermission.CONTACTS) == PermissionStatus.GRANTED
    if (!policyAllowed || !osGranted) {
      permissionRequestHandler.requestForExplicitAction(AppPermission.CONTACTS, preferBangla)
      _state.value =
        PhoneState.PermissionRequired(
          PhoneFeature.CONTACTS,
          "Contacts permission is required when you ask MAX to find a contact.",
        )
      return false
    }

    val outcome = contactsManager.searchContacts(query, initiatedByUser = true)
    privacyGuard.notifyResourcesReleased(SensitiveAction.ACCESS_CONTACTS)
    return outcome.fold(
      onSuccess = { list ->
        val msg = "Found ${list.size} contact(s) matching '${query.ifBlank { "all" }}'."
        _state.value = PhoneState.ActionReady(PhoneFeature.CONTACTS, msg)
        eventListener?.onPhoneAnnouncement(
          bangla = "${list.size}টি কন্টাক্ট পাওয়া গেছে।",
          english = msg,
          speakAloud = true,
        )
        true
      },
      onFailure = { err ->
        _state.value = PhoneState.Error(err.localizedMessage ?: "Contact lookup failed.")
        false
      },
    )
  }

  override fun onSystemContactPicked(uri: Uri): PhoneContact? {
    val picked = contactsManager.onSystemContactPicked(uri)
    if (picked != null) {
      _state.value = PhoneState.ActionReady(PhoneFeature.CONTACTS, "Selected ${picked.displayName} (${picked.phoneNumber}).")
    }
    return picked
  }

  override fun requestAddOrEditContact(
    name: String,
    phoneNumber: String,
    email: String?,
    initiatedByUser: Boolean,
  ): Boolean {
    if (!verifyUserInitiated(PhoneFeature.CONTACTS, initiatedByUser)) return false
    if (name.isBlank() && phoneNumber.isBlank()) {
      _state.value = PhoneState.Error("Please enter a contact name or number.")
      return false
    }

    val pending =
      PendingPhoneConfirmation(
        id = "confirm_contact_${System.currentTimeMillis()}",
        feature = PhoneFeature.CONTACTS,
        action = PhoneConfirmationAction.EditOrAddContact(name.trim(), phoneNumber.trim(), email?.trim()),
        titleBangla = "কন্টাক্ট সংরক্ষণ নিশ্চিত করুন",
        titleEnglish = "Confirm Contact Edit",
        messageBangla = "আপনি কি '${name.trim()}' (${phoneNumber.trim()}) কন্টাক্ট যোগ/পরিবর্তন করতে চান?",
        messageEnglish = "Open Android Contacts to add or edit '${name.trim()}' (${phoneNumber.trim()})?",
      )
    stageConfirmation(pending)
    return true
  }

  override fun requestSetAlarm(
    hour: Int,
    minute: Int,
    label: String,
    initiatedByUser: Boolean,
  ): Boolean {
    if (!verifyUserInitiated(PhoneFeature.ALARM, initiatedByUser)) return false
    if (hour !in 0..23 || minute !in 0..59) {
      _state.value = PhoneState.Error("Alarm time must be between 00:00 and 23:59.")
      return false
    }

    val timeStr = "%02d:%02d".format(hour, minute)
    val cleanLabel = label.trim().ifEmpty { "MAX Alarm" }
    val pending =
      PendingPhoneConfirmation(
        id = "confirm_alarm_${System.currentTimeMillis()}",
        feature = PhoneFeature.ALARM,
        action = PhoneConfirmationAction.SetAlarm(hour, minute, cleanLabel),
        titleBangla = "অ্যালার্ম নিশ্চিত করুন",
        titleEnglish = "Confirm Alarm",
        messageBangla = "আপনি কি $timeStr-এ '$cleanLabel' অ্যালার্ম সেট করতে চান?",
        messageEnglish = "Set an alarm for $timeStr ($cleanLabel)?",
      )
    stageConfirmation(pending)
    return true
  }

  override fun openSystemAlarms(initiatedByUser: Boolean): Boolean {
    if (!verifyUserInitiated(PhoneFeature.ALARM, initiatedByUser)) return false
    val opened = alarmManager.openSystemAlarmsApp()
    if (!opened) {
      _state.value = PhoneState.UnsupportedFallback(PhoneFeature.ALARM, "System Clock app not found.")
    } else {
      _state.value = PhoneState.ActionReady(PhoneFeature.ALARM, "Opened system alarms.")
    }
    return opened
  }

  override fun startTimer(
    durationSeconds: Int,
    label: String,
    initiatedByUser: Boolean,
  ): Boolean {
    if (!verifyUserInitiated(PhoneFeature.TIMER, initiatedByUser)) return false
    val started = timerManager.startTimer(durationSeconds, label)
    if (started) {
      val msg = "Started timer for ${durationSeconds}s ($label)."
      _state.value = PhoneState.ActionReady(PhoneFeature.TIMER, msg)
      eventListener?.onPhoneAnnouncement(
        bangla = "${durationSeconds} সেকেন্ডের টাইমার চালু করা হয়েছে।",
        english = msg,
        speakAloud = true,
      )
    } else {
      _state.value = PhoneState.Error("Timer duration must be greater than zero seconds.")
    }
    return started
  }

  override fun pauseTimer() = timerManager.pauseTimer()

  override fun resumeTimer() = timerManager.resumeTimer()

  override fun resetTimer() = timerManager.resetTimer()

  override fun startStopwatch(initiatedByUser: Boolean): Boolean {
    if (!verifyUserInitiated(PhoneFeature.STOPWATCH, initiatedByUser)) return false
    stopwatchManager.start()
    _state.value = PhoneState.ActionReady(PhoneFeature.STOPWATCH, "Stopwatch running.")
    return true
  }

  override fun pauseStopwatch() = stopwatchManager.pause()

  override fun lapStopwatch() = stopwatchManager.recordLap()

  override fun resetStopwatch() = stopwatchManager.reset()

  override fun calculate(expression: String, initiatedByUser: Boolean): CalculationRecord? {
    if (!verifyUserInitiated(PhoneFeature.CALCULATOR, initiatedByUser)) return null
    val result = calculatorManager.evaluate(expression)
    return result.fold(
      onSuccess = { rec ->
        _state.value = PhoneState.ActionReady(PhoneFeature.CALCULATOR, "${rec.expression} = ${rec.result}")
        eventListener?.onPhoneAnnouncement(
          bangla = "${rec.expression} এর উত্তর হলো ${rec.result}।",
          english = "The result of ${rec.expression} is ${rec.result}.",
          speakAloud = true,
        )
        rec
      },
      onFailure = { err ->
        _state.value = PhoneState.Error(err.localizedMessage ?: "Invalid math expression.")
        null
      },
    )
  }

  override fun requestToggleFlashlight(
    enable: Boolean,
    initiatedByUser: Boolean,
    requireConfirmation: Boolean,
  ): Boolean {
    if (!verifyUserInitiated(PhoneFeature.FLASHLIGHT, initiatedByUser)) return false
    if (!flashlightManager.isFlashSupported()) {
      val msg = "Flashlight hardware is unavailable on this device."
      _state.value = PhoneState.UnsupportedFallback(PhoneFeature.FLASHLIGHT, msg)
      return false
    }

    if (requireConfirmation) {
      val stateWordBn = if (enable) "চালু" else "বন্ধ"
      val stateWordEn = if (enable) "ON" else "OFF"
      val pending =
        PendingPhoneConfirmation(
          id = "confirm_flash_${System.currentTimeMillis()}",
          feature = PhoneFeature.FLASHLIGHT,
          action = PhoneConfirmationAction.ToggleFlashlight(enable),
          titleBangla = "ফ্ল্যাশলাইট নিশ্চিত করুন",
          titleEnglish = "Confirm Flashlight",
          messageBangla = "আপনি কি ডিভাইসের ফ্ল্যাশলাইট $stateWordBn করতে চান?",
          messageEnglish = "Turn the device flashlight $stateWordEn?",
        )
      stageConfirmation(pending)
      return true
    }

    return executeFlashlightChange(enable)
  }

  override fun confirmPendingAction(): Boolean {
    val pending = _pendingConfirmation.value ?: return false
    _pendingConfirmation.value = null

    return when (val action = pending.action) {
      is PhoneConfirmationAction.PlaceCall -> {
        val result =
          callManager.executeConfirmedCall(
            phoneNumber = action.phoneNumber,
            confirmedByUser = true,
            preferDirectCall = action.directCallRequested,
          )
        privacyGuard.notifyResourcesReleased(SensitiveAction.MAKE_CALL)
        when (result) {
          is CallLaunchResult.Launched -> {
            val modeText = if (result.dialModeUsed) "Opened Phone dialer" else "Placed call"
            val msg = "$modeText for ${result.normalizedNumber}."
            _state.value = PhoneState.ActionReady(PhoneFeature.CALL, msg)
            eventListener?.onPhoneAnnouncement(
              bangla = "${result.normalizedNumber} নম্বরের জন্য ডায়ালার খোলা হয়েছে।",
              english = msg,
              speakAloud = true,
            )
            true
          }
          is CallLaunchResult.Unsupported -> {
            _state.value = PhoneState.UnsupportedFallback(PhoneFeature.CALL, result.reason)
            false
          }
          is CallLaunchResult.Failed -> {
            _state.value = PhoneState.Error(result.reason)
            false
          }
        }
      }

      is PhoneConfirmationAction.ComposeSms -> {
        when (
          val result =
            smsManager.openConfirmedSmsComposer(
              recipientNumber = action.phoneNumber,
              messageBody = action.messageBody,
              confirmedByUser = true,
            )
        ) {
          is SmsLaunchResult.ComposerOpened -> {
            val msg = "Opened SMS composer for ${result.recipient}."
            _state.value = PhoneState.ActionReady(PhoneFeature.SMS, msg)
            eventListener?.onPhoneAnnouncement(
              bangla = "${result.recipient} নম্বরের জন্য SMS কম্পোজার খোলা হয়েছে।",
              english = msg,
              speakAloud = true,
            )
            true
          }
          is SmsLaunchResult.Unsupported -> {
            _state.value = PhoneState.UnsupportedFallback(PhoneFeature.SMS, result.reason)
            false
          }
          is SmsLaunchResult.Failed -> {
            _state.value = PhoneState.Error(result.reason)
            false
          }
        }
      }

      is PhoneConfirmationAction.EditOrAddContact -> {
        val outcome =
          contactsManager.launchConfirmedContactEditor(
            name = action.name,
            phoneNumber = action.phoneNumber,
            email = action.email,
            confirmedByUser = true,
          )
        outcome.fold(
          onSuccess = {
            _state.value = PhoneState.ActionReady(PhoneFeature.CONTACTS, "Opened native contact editor.")
            true
          },
          onFailure = { err ->
            _state.value = PhoneState.UnsupportedFallback(PhoneFeature.CONTACTS, err.localizedMessage ?: "No contacts editor found.")
            false
          },
        )
      }

      is PhoneConfirmationAction.SetAlarm -> {
        val outcome = alarmManager.setConfirmedAlarm(action.hour, action.minute, action.label, confirmedByUser = true)
        outcome.fold(
          onSuccess = { entry ->
            val msg = "Alarm scheduled for ${entry.formattedTime} (${entry.label})."
            _state.value = PhoneState.ActionReady(PhoneFeature.ALARM, msg)
            eventListener?.onPhoneAnnouncement(
              bangla = "${entry.formattedTime}-এ '${entry.label}' অ্যালার্ম সেট করা হয়েছে।",
              english = msg,
              speakAloud = true,
            )
            true
          },
          onFailure = { err ->
            _state.value = PhoneState.Error(err.localizedMessage ?: "Could not set alarm.")
            false
          },
        )
      }

      is PhoneConfirmationAction.ToggleFlashlight -> executeFlashlightChange(action.enable)
    }
  }

  override fun cancelPendingAction() {
    _pendingConfirmation.value = null
    if (_state.value is PhoneState.AwaitingConfirmation) {
      _state.value = PhoneState.Dormant
    }
  }

  override fun releaseTaskResources() {
    flashlightManager.release()
    contactsManager.clearCachedResults()
    _pendingConfirmation.value = null
  }

  override fun release() {
    timerManager.release()
    stopwatchManager.release()
    flashlightManager.release()
    contactsManager.clearCachedResults()
    alarmManager.clearSessionAlarms()
    calculatorManager.clearHistory()
    eventListener = null
    _pendingConfirmation.value = null
    _state.value = PhoneState.Dormant
  }

  private fun executeFlashlightChange(enable: Boolean): Boolean {
    return flashlightManager.setTorch(enable, initiatedByUser = true).fold(
      onSuccess = { actual ->
        val msg = "Flashlight turned ${if (actual) "ON" else "OFF"}."
        _state.value = PhoneState.ActionReady(PhoneFeature.FLASHLIGHT, msg)
        eventListener?.onPhoneAnnouncement(
          bangla = if (actual) "ফ্ল্যাশলাইট চালু করা হয়েছে।" else "ফ্ল্যাশলাইট বন্ধ করা হয়েছে।",
          english = msg,
          speakAloud = true,
        )
        true
      },
      onFailure = { err ->
        _state.value = PhoneState.UnsupportedFallback(PhoneFeature.FLASHLIGHT, err.localizedMessage ?: "Flashlight unavailable.")
        false
      },
    )
  }

  private fun stageConfirmation(pending: PendingPhoneConfirmation) {
    _pendingConfirmation.value = pending
    _state.value = PhoneState.AwaitingConfirmation(pending)
    errorTracker.logInfo(MODULE_NAME, "Awaiting user confirmation for ${pending.feature.displayName}.")
  }

  private fun verifyUserInitiated(feature: PhoneFeature, initiatedByUser: Boolean): Boolean {
    if (!initiatedByUser) {
      val reason = "Blocked automatic ${feature.displayName} action. Explicit user request required."
      errorTracker.logWarning(MODULE_NAME, reason)
      _state.value = PhoneState.BlockedByPrivacyPolicy(feature, reason)
      return false
    }
    return true
  }

  companion object {
    private const val MODULE_NAME = "PhoneManager"
  }
}
