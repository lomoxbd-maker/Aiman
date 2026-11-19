package com.example.phone

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import com.example.utils.ErrorTracker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * ContactsManager — Privacy-preserving contacts helper for MAX.
 *
 * Strict guarantees:
 * - Never accesses or scans contacts automatically in the background.
 * - Never edits or inserts contacts automatically (requires confirmation and uses Android's
 *   built-in `INSERT_OR_EDIT` contact sheet so the user sees and approves every change).
 * - Immediately closes ContentResolver cursors (`.use {}`) and caps memory footprint (`MAX_RESULTS`).
 */
class ContactsManager(
  private val context: Context,
  private val errorTracker: ErrorTracker,
) {

  private val _searchResults = MutableStateFlow<List<PhoneContact>>(emptyList())
  val searchResults: StateFlow<List<PhoneContact>> = _searchResults.asStateFlow()

  fun hasContactsPermission(): Boolean {
    return ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
      PackageManager.PERMISSION_GRANTED
  }

  /**
   * Queries contacts ONLY when [initiatedByUser] is true and `READ_CONTACTS` is granted.
   * Limits results cleanly for low RAM and closes the cursor right after reading.
   */
  fun searchContacts(
    query: String,
    initiatedByUser: Boolean,
  ): Result<List<PhoneContact>> {
    if (!initiatedByUser) {
      val msg = "Blocked automatic contact access."
      errorTracker.logWarning(MODULE_NAME, msg)
      return Result.failure(SecurityException(msg))
    }

    if (!hasContactsPermission()) {
      return Result.failure(SecurityException("Contacts permission is required to search contacts."))
    }

    val trimmed = query.trim()
    val found = mutableListOf<PhoneContact>()
    val seenNumbers = hashSetOf<String>()

    return runCatching {
      val projection =
        arrayOf(
          ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
          ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
          ContactsContract.CommonDataKinds.Phone.NUMBER,
        )

      val selection =
        if (trimmed.isNotEmpty()) {
          "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ? OR ${ContactsContract.CommonDataKinds.Phone.NUMBER} LIKE ?"
        } else {
          null
        }
      val selectionArgs = if (trimmed.isNotEmpty()) arrayOf("%$trimmed%", "%$trimmed%") else null

      context.contentResolver.query(
        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
        projection,
        selection,
        selectionArgs,
        "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC",
      )?.use { cursor ->
        val idIdx = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
        val nameIdx = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
        val numIdx = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)

        while (cursor.moveToNext() && found.size < MAX_RESULTS) {
          val id = cursor.getString(idIdx) ?: continue
          val name = cursor.getString(nameIdx)?.trim().orEmpty()
          val number = cursor.getString(numIdx)?.trim().orEmpty()
          if (name.isNotEmpty() && number.isNotEmpty() && seenNumbers.add(number)) {
            found.add(PhoneContact(id = id, displayName = name, phoneNumber = number))
          }
        }
      }

      _searchResults.value = found
      errorTracker.logInfo(MODULE_NAME, "User contact lookup returned ${found.size} contact(s).")
      found.toList()
    }.onFailure { err ->
      errorTracker.logError(MODULE_NAME, "Contact lookup failed: ${err.message}", err)
    }
  }

  /**
   * Resolves a picked contact URI returned from Android's zero-permission system Contact Picker.
   */
  fun onSystemContactPicked(uri: android.net.Uri): PhoneContact? {
    return runCatching {
      val projection =
        arrayOf(
          ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
          ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
          ContactsContract.CommonDataKinds.Phone.NUMBER,
        )
      context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
          val id = cursor.getString(0) ?: System.currentTimeMillis().toString()
          val name = cursor.getString(1)?.trim().orEmpty().ifEmpty { "Selected Contact" }
          val number = cursor.getString(2)?.trim().orEmpty()
          if (number.isNotEmpty()) {
            val contact = PhoneContact(id, name, number)
            _searchResults.value = listOf(contact)
            return@runCatching contact
          }
        }
        null
      }
    }.onFailure { err ->
      errorTracker.logWarning(MODULE_NAME, "Failed reading picked contact URI: ${err.message}")
    }.getOrNull()
  }

  /**
   * Opens Android's native Add/Edit Contact UI ONLY after explicit user confirmation.
   */
  fun launchConfirmedContactEditor(
    name: String,
    phoneNumber: String,
    email: String?,
    confirmedByUser: Boolean,
  ): Result<Unit> {
    if (!confirmedByUser) {
      return Result.failure(SecurityException("Cannot modify contacts without user confirmation."))
    }

    val intent =
      Intent(Intent.ACTION_INSERT_OR_EDIT).apply {
        type = ContactsContract.Contacts.CONTENT_ITEM_TYPE
        if (name.isNotBlank()) putExtra(ContactsContract.Intents.Insert.NAME, name.trim())
        if (phoneNumber.isNotBlank()) putExtra(ContactsContract.Intents.Insert.PHONE, phoneNumber.trim())
        if (!email.isNullOrBlank()) putExtra(ContactsContract.Intents.Insert.EMAIL, email.trim())
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }

    val resolved = runCatching { intent.resolveActivity(context.packageManager) }.getOrNull()
      ?: return Result.failure(UnsupportedOperationException("No Contacts app installed on device."))

    return runCatching {
      context.startActivity(intent)
      errorTracker.logInfo(MODULE_NAME, "Opened native contact editor (${resolved.packageName}) with confirmation.")
    }
  }

  fun clearCachedResults() {
    _searchResults.value = emptyList()
  }

  companion object {
    private const val MODULE_NAME = "ContactsManager"
    private const val MAX_RESULTS = 25
  }
}
