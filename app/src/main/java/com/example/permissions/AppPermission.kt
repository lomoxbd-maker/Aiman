package com.example.permissions

import android.Manifest
import android.os.Build

/**
 * How an Android capability is gated across OS versions.
 */
enum class PermissionMechanism {
  RUNTIME_OS_PERMISSION,
  ANDROID_13_RUNTIME_NOTIFICATION,
  ZERO_PERMISSION_PICKER,
  SAF_DOCUMENT_PICKER,
  INSTALL_TIME_POLICY_GATED,
}

/**
 * All 7 permission & privacy domains managed by MAX.
 */
enum class AppPermission(
  val displayName: String,
  val manifestPermission: String?,
  val mechanism: PermissionMechanism,
  val purpose: String,
  val rationaleBangla: String,
  val rationaleEnglish: String,
  val isFoundationDormant: Boolean,
) {
  MICROPHONE_WAKE_WORD(
    displayName = "Microphone",
    manifestPermission = Manifest.permission.RECORD_AUDIO,
    mechanism = PermissionMechanism.RUNTIME_OS_PERMISSION,
    purpose = "Used strictly for ephemeral 'MAX' wake-word standby when armed; audio is never saved.",
    rationaleBangla =
      "শুধুমাত্র 'MAX' ওয়েক ওয়ার্ড শোনার জন্য মাইক্রোফোন অনুমতি প্রয়োজন। কোনো অডিও বা ব্যক্তিগত কথা রেকর্ড বা সংরক্ষণ করা হয় না।",
    rationaleEnglish =
      "MAX uses the microphone only to detect the wake word 'MAX' in RAM when armed. Normal conversations are never recorded or saved.",
    isFoundationDormant = false,
  ),
  CAMERA(
    displayName = "Camera",
    manifestPermission = Manifest.permission.CAMERA,
    mechanism = PermissionMechanism.RUNTIME_OS_PERMISSION,
    purpose = "User-triggered photo, selfie, video recording, and QR scanning; closed immediately after.",
    rationaleBangla =
      "আপনি নিজে চাইলে তবেই ক্যামেরা ব্যবহার হবে (ছবি, সেলফি, ভিডিও বা QR স্ক্যান)। কাজ শেষেই ক্যামেরা বন্ধ করা হয়।",
    rationaleEnglish =
      "MAX never opens the camera automatically. Permission is requested only when you take a photo, selfie, video, or scan QR.",
    isFoundationDormant = false,
  ),
  GALLERY(
    displayName = "Gallery / Media",
    manifestPermission = null,
    mechanism = PermissionMechanism.ZERO_PERMISSION_PICKER,
    purpose = "Zero-permission Android Photo Picker opened only on explicit user request.",
    rationaleBangla =
      "আপনার গ্যালারি কখনোই স্বয়ংক্রিয়ভাবে পড়া হয় না; কেবল আপনি ছবি বেছে নিলে তবেই Photo Picker খোলা হবে।",
    rationaleEnglish =
      "MAX never scans your gallery automatically and uses the zero-permission Android Photo Picker only when you choose an image.",
    isFoundationDormant = false,
  ),
  FILES(
    displayName = "Files",
    manifestPermission = null,
    mechanism = PermissionMechanism.SAF_DOCUMENT_PICKER,
    purpose = "Uses Android Storage Access Framework only when user selects a file.",
    rationaleBangla =
      "আপনার ডিভাইসের ফাইল স্বয়ংক্রিয়ভাবে পড়া হয় না; কেবল আপনি ফাইল নির্বাচন করলে তবেই অ্যাক্সেস হবে।",
    rationaleEnglish =
      "MAX never reads device storage automatically; files are accessed only when you explicitly pick a document.",
    isFoundationDormant = false,
  ),
  LOCATION(
    displayName = "Location",
    manifestPermission = Manifest.permission.ACCESS_FINE_LOCATION,
    mechanism = PermissionMechanism.RUNTIME_OS_PERMISSION,
    purpose = "Future location-aware assistance only when explicitly triggered.",
    rationaleBangla =
      "MAX কখনোই ব্যাকগ্রাউন্ডে বা স্বয়ংক্রিয়ভাবে আপনার GPS লোকেশন ব্যবহার করে না।",
    rationaleEnglish =
      "MAX never uses GPS automatically. Location is checked only if you explicitly ask for location-based help.",
    isFoundationDormant = true,
  ),
  NOTIFICATIONS(
    displayName = "Notifications",
    manifestPermission =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.POST_NOTIFICATIONS
      } else {
        null
      },
    mechanism = PermissionMechanism.ANDROID_13_RUNTIME_NOTIFICATION,
    purpose = "Delivers user-requested reminders and assistant alerts.",
    rationaleBangla =
      "আপনার সেট করা রিমাইন্ডার ও জরুরি অ্যালার্ট দেখাতে নোটিফিকেশন অনুমতি ব্যবহৃত হয়।",
    rationaleEnglish =
      "Notification access lets MAX deliver reminders and alerts you explicitly ask for.",
    isFoundationDormant = false,
  ),
  INTERNET(
    displayName = "Internet",
    manifestPermission = Manifest.permission.INTERNET,
    mechanism = PermissionMechanism.INSTALL_TIME_POLICY_GATED,
    purpose = "User-requested HTTPS search, webpage reading, and summary generation; never browses automatically.",
    rationaleBangla =
      "MAX স্বয়ংক্রিয়ভাবে ইন্টারনেট ব্রাউজ বা ডেটা শেয়ার করে না; আপনার অনুমতি ছাড়া অনলাইন সংযোগ বন্ধ থাকে।",
    rationaleEnglish =
      "MAX never browses the internet automatically; network actions stay locked unless you permit them.",
    isFoundationDormant = false,
  ),
  PHONE_CALL(
    displayName = "Phone Calls",
    manifestPermission = Manifest.permission.CALL_PHONE,
    mechanism = PermissionMechanism.RUNTIME_OS_PERMISSION,
    purpose = "Places phone calls strictly after your explicit request and confirmation (falls back safely to dialer).",
    rationaleBangla =
      "MAX কখনোই স্বয়ংক্রিয়ভাবে কাউকে কল করে না বা কল লগ পড়ে না। কেবল আপনার নিশ্চিত করা কল শুরু করতে অনুমতি চাওয়া হয়।",
    rationaleEnglish =
      "MAX never calls anyone automatically or reads call history. Permission is asked only to initiate calls you confirm.",
    isFoundationDormant = false,
  ),
  CONTACTS(
    displayName = "Contacts",
    manifestPermission = Manifest.permission.READ_CONTACTS,
    mechanism = PermissionMechanism.RUNTIME_OS_PERMISSION,
    purpose = "Looks up a contact number strictly when you ask to find, call, or message someone.",
    rationaleBangla =
      "MAX গোপনে আপনার কন্টাক্ট পড়ে না বা পরিবর্তন করে না; কেবল আপনি কোনো কন্টাক্ট খুঁজতে বললে অনুমতি চাওয়া হয়।",
    rationaleEnglish =
      "MAX never reads or edits contacts silently. Permission is used only when you explicitly ask to look up a contact.",
    isFoundationDormant = false,
  ),
  CALENDAR(
    displayName = "Calendar",
    manifestPermission = Manifest.permission.READ_CALENDAR,
    mechanism = PermissionMechanism.RUNTIME_OS_PERMISSION,
    purpose = "Reads today's schedule strictly when asked; never creates or modifies calendar events without your action.",
    rationaleBangla =
      "আপনি আজকের ক্যালেন্ডার দেখতে বললে তবেই MAX আপনার নির্ধারিত ইভেন্ট পড়বে। কখনো নিজে থেকে কিছু পরিবর্তন করবে না।",
    rationaleEnglish =
      "MAX reads your calendar schedule only when you ask to check events and never edits events automatically.",
    isFoundationDormant = false,
  ),
}

enum class PermissionStatus {
  GRANTED,
  NOT_GRANTED,
  DORMANT_LOCKED,
}

data class PermissionSnapshot(
  val permission: AppPermission,
  val status: PermissionStatus,
  val userAllowedInPolicy: Boolean = !permission.isFoundationDormant,
)
