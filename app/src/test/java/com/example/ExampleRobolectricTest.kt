package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.conversation.ConversationManager
import com.example.core.CoreLifecycleState
import com.example.emotion.EmotionDetector
import com.example.emotion.EmotionManager
import com.example.emotion.EmotionType
import com.example.emotion.PersonalityManager
import com.example.emotion.UserMood
import com.example.memory.MemoryCommand
import com.example.memory.MemoryManager
import com.example.memory.MemoryType
import com.example.speech.SpeechManager
import com.example.speech.SpeechState
import com.example.tts.SpeechStyle
import com.example.tts.TTSManager
import com.example.tts.VoiceConfig
import com.example.tts.VoiceLanguage
import com.example.utils.ErrorLogger
import com.example.wakeword.WakeWordConfig
import com.example.wakeword.WakeWordListener
import com.example.wakeword.WakeWordManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `app name matches MAX`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("MAX", appName)
  }

  @Test
  fun `wake word MAX gates speech preparation`() {
    val logger = ErrorLogger()
    val speechManager = SpeechManager(logger)
    speechManager.initialize(recognitionAvailable = true)
    assertEquals(SpeechState.DORMANT_STANDBY, speechManager.state.value)

    speechManager.prepareForVoiceConversation("HELLO")
    assertEquals(SpeechState.DORMANT_STANDBY, speechManager.state.value)

    val wakeManager = WakeWordManager(logger)
    var notifiedKeyword: String? = null
    wakeManager.addListener(
      object : WakeWordListener {
        override fun onWakeWordDetected(keyword: String, confidence: Float, detectedAtMillis: Long) {
          notifiedKeyword = keyword
          speechManager.prepareForVoiceConversation(keyword)
        }
      },
    )

    wakeManager.simulateWakeWordDetection()
    assertEquals(WakeWordConfig.DEFAULT_KEYWORD, notifiedKeyword)
    assertEquals(SpeechState.PREPARED_FOR_CONVERSATION, speechManager.state.value)
    wakeManager.release()
  }

  @Test
  fun `bangla natural sentence splitting handles dari and english punctuation`() {
    val sentences =
      TTSManager.splitIntoNaturalSentences(
        "নমস্কার, আমি ম্যাক্স। আপনি কেমন আছেন? Let me help!",
      )
    assertEquals(3, sentences.size)
    assertEquals("নমস্কার, আমি ম্যাক্স।", sentences[0])
    assertEquals("আপনি কেমন আছেন?", sentences[1])
    assertEquals("Let me help!", sentences[2])
  }

  @Test
  fun `voice config applies calm style smoothing without robotic defaults`() {
    val calmConfig = VoiceConfig(language = VoiceLanguage.BANGLA_BD, speechStyle = SpeechStyle.CALM)
    assertTrue(calmConfig.effectiveSpeechRate() < 1.0f)
    assertTrue(calmConfig.effectivePitch() < 1.0f)
  }

  @Test
  fun `emotion detector handles happy, worried, and help cues in bangla and english`() {
    val detector = EmotionDetector()

    val happy = detector.detect("আমি আজকে খুব খুশি!")
    assertEquals(UserMood.HAPPY, happy.userMood)
    assertEquals(EmotionType.HAPPY, happy.suggestedEmotion)

    val worried = detector.detect("I feel really worried and sad today")
    assertEquals(UserMood.SAD_OR_WORRIED, worried.userMood)
    assertEquals(EmotionType.CARING, worried.suggestedEmotion)

    val help = detector.detect("কীভাবে এটা করব সাহায্য করো")
    assertEquals(UserMood.NEEDS_HELP, help.userMood)
    assertEquals(EmotionType.SUPPORTIVE, help.suggestedEmotion)
  }

  @Test
  fun `emotion manager avoids repeating identical sentences back to back`() {
    val logger = ErrorLogger()
    val manager = EmotionManager(EmotionDetector(), logger)

    val (_, firstReply) = manager.analyzeAndRespond("I am happy!", VoiceLanguage.ENGLISH_US)
    val (_, secondReply) = manager.analyzeAndRespond("I am happy!", VoiceLanguage.ENGLISH_US)
    assertNotEquals(firstReply, secondReply)
  }

  @Test
  fun `memory manager enforces sensitive data protection and supports all 4 bangla commands`() {
    val logger = ErrorLogger()
    val memoryManager = MemoryManager(logger)
    memoryManager.initialize(retentionEnabled = true)

    // Blocks sensitive info without explicit permission
    val sensitiveBlocked =
      memoryManager.saveMemory(
        type = MemoryType.CONVERSATION_CONTEXT,
        content = "My password is secret123",
        hasExplicitPermission = false,
      )
    assertFalse(sensitiveBlocked.accepted)

    // 1. "MAX, এটা মনে রাখো"
    val rememberCmd = memoryManager.parseCommand("MAX, এটা মনে রাখো আমার নাম রাকিব")
    assertTrue(rememberCmd is MemoryCommand.Remember)
    val rememberRes = memoryManager.executeCommand(rememberCmd!!)
    assertTrue(rememberRes.accepted)
    assertEquals("রাকিব", memoryManager.getUserName())

    // 2. "MAX, আমার Memory দেখাও"
    val showCmd = memoryManager.parseCommand("MAX, আমার Memory দেখাও")
    assertTrue(showCmd is MemoryCommand.ShowAll)
    val showRes = memoryManager.executeCommand(showCmd!!)
    assertTrue(showRes.affectedItems.isNotEmpty())

    // 3. "MAX, এটা ভুলে যাও"
    val forgetCmd = memoryManager.parseCommand("MAX, এটা ভুলে যাও রাকিব")
    assertTrue(forgetCmd is MemoryCommand.Forget)
    assertTrue(memoryManager.executeCommand(forgetCmd!!).accepted)
    assertEquals(null, memoryManager.getUserName())

    // 4. "MAX, সব Memory মুছে দাও"
    memoryManager.saveMemory(MemoryType.USER_ROUTINE, "Morning tea at 7 AM", hasExplicitPermission = true)
    val clearCmd = memoryManager.parseCommand("MAX, সব Memory মুছে দাও")
    assertTrue(clearCmd is MemoryCommand.ClearAll)
    memoryManager.executeCommand(clearCmd!!)
    assertTrue(memoryManager.memories.value.isEmpty())
    memoryManager.release()
  }

  @Test
  fun `personality manager personalizes replies using remembered user name`() {
    val logger = ErrorLogger()
    val memoryManager = MemoryManager(logger)
    memoryManager.initialize(true)
    memoryManager.saveMemory(MemoryType.USER_NAME, "Rakib", hasExplicitPermission = true)

    val emotionManager = EmotionManager(EmotionDetector(), logger)
    val personality = PersonalityManager(emotionManager, memoryManager)
    val (_, reply) = personality.craftPersonalizedReply("I am happy!", VoiceLanguage.ENGLISH_US)
    assertTrue(reply.startsWith("Rakib"))
    memoryManager.release()
  }

  @Test
  fun `conversation manager integrates emotion, personality, and memory with MAXCore`() {
    val app = ApplicationProvider.getApplicationContext<MAXApplication>()
    val conv = app.maxCore.conversationEngine as ConversationManager
    val (state, reply) = conv.processUserMessage("I need help", VoiceLanguage.ENGLISH_US, speakAloud = false)
    assertEquals(EmotionType.SUPPORTIVE, state.currentEmotion)
    assertTrue(reply.isNotBlank())
    assertTrue(
      app.maxCore.coreState.value == CoreLifecycleState.UNINITIALIZED ||
        app.maxCore.coreState.value == CoreLifecycleState.IDLE_DISARMED ||
        app.maxCore.coreState.value == CoreLifecycleState.STANDBY,
    )
  }

  @Test
  fun `privacy controller blocks automatic camera, gallery, files, gps, and internet access`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val logger = ErrorLogger()
    val permManager = com.example.permissions.PermissionManager(context, logger)
    val privacy = com.example.permissions.PrivacyController({ permManager }, logger)

    val autoCamera =
      privacy.authorizeAction(com.example.permissions.SensitiveAction.OPEN_CAMERA, initiatedByUser = false)
    val autoGallery =
      privacy.authorizeAction(com.example.permissions.SensitiveAction.ACCESS_GALLERY, initiatedByUser = false)
    val autoFiles =
      privacy.authorizeAction(com.example.permissions.SensitiveAction.READ_FILES, initiatedByUser = false)
    val autoGps =
      privacy.authorizeAction(com.example.permissions.SensitiveAction.READ_LOCATION, initiatedByUser = false)
    val autoNet =
      privacy.authorizeAction(com.example.permissions.SensitiveAction.BROWSE_INTERNET, initiatedByUser = false)

    assertTrue(autoCamera is com.example.permissions.PrivacyCheckResult.Denied)
    assertTrue(autoGallery is com.example.permissions.PrivacyCheckResult.Denied)
    assertTrue(autoFiles is com.example.permissions.PrivacyCheckResult.Denied)
    assertTrue(autoGps is com.example.permissions.PrivacyCheckResult.Denied)
    assertTrue(autoNet is com.example.permissions.PrivacyCheckResult.Denied)
  }

  @Test
  fun `permission request handler explains why microphone is needed before prompting`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val logger = ErrorLogger()
    val permManager = com.example.permissions.PermissionManager(context, logger)
    val privacy = com.example.permissions.PrivacyController({ permManager }, logger)
    val handler = com.example.permissions.PermissionRequestHandler(permManager, privacy, logger)

    val resultState =
      handler.requestWhenNeeded(
        com.example.permissions.AppPermission.MICROPHONE_WAKE_WORD,
        preferBangla = true,
      )
    assertTrue(resultState is com.example.permissions.PermissionState.RequiresExplanation)
    val pending = handler.pendingRequest.value
    assertTrue(pending != null && pending.explanationBangla.isNotBlank())
  }

  @Test
  fun `camera manager never opens automatically and closes hardware after photo and qr completion`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val logger = ErrorLogger()
    val permManager = com.example.permissions.PermissionManager(context, logger)
    val privacy = com.example.permissions.PrivacyController({ permManager }, logger)
    val handler = com.example.permissions.PermissionRequestHandler(permManager, privacy, logger)
    val cameraManager =
      com.example.camera.CameraManager(context, permManager, privacy, handler, logger)

    // Blocked when not initiated by user
    assertFalse(cameraManager.openCamera(com.example.camera.CameraMode.PHOTO, initiatedByUser = false))
    assertFalse(cameraManager.isActive.value)
    assertTrue(cameraManager.state.value is com.example.camera.CameraState.BlockedByPrivacyPolicy)

    // Shadow camera hardware + permission in Robolectric
    val app = ApplicationProvider.getApplicationContext<android.app.Application>()
    org.robolectric.Shadows.shadowOf(app).grantPermissions(android.Manifest.permission.CAMERA)
    org.robolectric.Shadows.shadowOf(context.packageManager)
      .setSystemFeature(android.content.pm.PackageManager.FEATURE_CAMERA_ANY, true)
    org.robolectric.Shadows.shadowOf(context.packageManager)
      .setSystemFeature(android.content.pm.PackageManager.FEATURE_CAMERA_FRONT, true)
    privacy.setPermissionAllowed(com.example.permissions.AppPermission.CAMERA, true)

    // Explicit Selfie mode opens front lens, then auto-closes after capture
    assertTrue(cameraManager.openCamera(com.example.camera.CameraMode.SELFIE, initiatedByUser = true))
    assertTrue(cameraManager.isActive.value)
    assertEquals(com.example.camera.CameraLens.FRONT, cameraManager.activeLens.value)

    cameraManager.onPhotoCaptured("content://media/external/images/media/101")
    assertFalse(cameraManager.isActive.value)
    assertTrue(cameraManager.state.value is com.example.camera.CameraState.CaptureCompleted)

    // Explicit QR scan mode detects code and releases camera hardware automatically
    assertTrue(cameraManager.openCamera(com.example.camera.CameraMode.QR_SCAN, initiatedByUser = true))
    assertTrue(cameraManager.isActive.value)
    cameraManager.onQrCodeDetected("https://max-assistant.local/qr", "QR_CODE")
    assertFalse(cameraManager.isActive.value)
    assertEquals("https://max-assistant.local/qr", cameraManager.lastScannedQr.value)
    cameraManager.release()
  }

  @Test
  fun `gallery manager blocks automatic access, supports find photos, ocr prep, and requires confirmation before delete`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val logger = ErrorLogger()
    val permManager = com.example.permissions.PermissionManager(context, logger)
    val privacy = com.example.permissions.PrivacyController({ permManager }, logger)
    val handler = com.example.permissions.PermissionRequestHandler(permManager, privacy, logger)
    val galleryManager =
      com.example.gallery.GalleryManager(context, permManager, privacy, handler, logger)

    // Never accesses gallery automatically
    assertFalse(galleryManager.requestOpenGallery(initiatedByUser = false))
    assertTrue(galleryManager.state.value is com.example.gallery.GalleryState.BlockedByPrivacyPolicy)

    // Requires user policy permission first
    assertFalse(galleryManager.requestOpenGallery(initiatedByUser = true))
    assertTrue(handler.pendingRequest.value != null)
    handler.onSystemPermissionResult(com.example.permissions.AppPermission.GALLERY, granted = true)

    assertTrue(galleryManager.requestOpenGallery(initiatedByUser = true))
    galleryManager.addCapturedMedia(
      uriString = "content://media/1",
      displayName = "Dhaka_Sunset.jpg",
      mimeType = "image/jpeg",
      tags = listOf("sunset", "dhaka"),
    )
    galleryManager.addCapturedMedia(
      uriString = "content://media/2",
      displayName = "Morning_Clip.mp4",
      mimeType = "video/mp4",
      tags = listOf("video"),
    )

    // Find photos + filter
    val search = galleryManager.findPhotos("sunset", com.example.gallery.GalleryFilter.IMAGES)
    assertEquals(1, search.matches.size)
    assertEquals("Dhaka_Sunset.jpg", search.matches.first().displayName)

    // OCR preparation
    val itemId = search.matches.first().id
    val ocr = galleryManager.prepareOcrForSelectedPhoto(itemId)
    assertTrue(ocr.isEngineReady)

    // Never deletes without explicit confirmation
    galleryManager.requestDeleteMedia(itemId)
    assertTrue(galleryManager.pendingDeleteConfirmation.value != null)
    assertEquals(2, galleryManager.selectedItems.value.size)

    galleryManager.confirmDeleteMedia()
    assertEquals(1, galleryManager.selectedItems.value.size)
    galleryManager.release()
  }

  @Test
  fun `internet manager blocks automatic browsing and enforces permission before searching`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val logger = ErrorLogger()
    val permManager = com.example.permissions.PermissionManager(context, logger)
    val privacy = com.example.permissions.PrivacyController({ permManager }, logger)
    val handler = com.example.permissions.PermissionRequestHandler(permManager, privacy, logger)
    val webReq = com.example.internet.WebRequestManager(context, logger)
    val searchMgr = com.example.internet.SearchManager(webReq, logger)
    val browserMgr = com.example.internet.BrowserManager(context, webReq, logger)
    val netMgr =
      com.example.internet.InternetManager(
        permissionController = permManager,
        privacyGuard = privacy,
        permissionRequestHandler = handler,
        webRequestManager = webReq,
        searchManager = searchMgr,
        browserManager = browserMgr,
        errorTracker = logger,
      )

    // Never browse automatically
    assertFalse(netMgr.search("Dhaka news", com.example.internet.SearchCategory.NEWS, initiatedByUser = false))
    assertTrue(netMgr.state.value is com.example.internet.InternetState.BlockedByPrivacyPolicy)

    // Explicit user search with default policy prompts explanation first
    assertFalse(netMgr.search("Bangladesh tech", com.example.internet.SearchCategory.WEB, initiatedByUser = true))
    assertTrue(netMgr.state.value is com.example.internet.InternetState.PermissionRequired)
    assertTrue(handler.pendingRequest.value?.permission == com.example.permissions.AppPermission.INTERNET)

    // Unsafe URLs are blocked even after user authorizes internet
    handler.onSystemPermissionResult(com.example.permissions.AppPermission.INTERNET, granted = true)
    assertFalse(netMgr.openWebsite("javascript:alert(1)", initiatedByUser = true))
    assertTrue(netMgr.state.value is com.example.internet.InternetState.Error)
    netMgr.release()
  }

  @Test
  fun `web request, search, and browser managers validate safe urls, strip trackers, and summarize html`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val logger = ErrorLogger()

    // Shadow active Wi-Fi network capability for Robolectric online test
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
    val activeNetwork = cm.activeNetwork ?: org.robolectric.shadows.ShadowNetwork.newInstance(101)
    val caps =
      org.robolectric.shadows.ShadowNetworkCapabilities.newInstance().apply {
        org.robolectric.Shadows.shadowOf(this)
          .addCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
        org.robolectric.Shadows.shadowOf(this)
          .addCapability(android.net.NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
        org.robolectric.Shadows.shadowOf(this).setLinkDownstreamBandwidthKbps(50_000)
      }
    org.robolectric.Shadows.shadowOf(cm).setNetworkCapabilities(activeNetwork, caps)
    org.robolectric.Shadows.shadowOf(cm).setDefaultNetworkActive(true)

    val fakeTransport =
      object : com.example.internet.SafeHttpTransport {
        override suspend fun executeGet(
          url: String,
          timeoutMs: Long,
          maxBytes: Int,
        ): com.example.internet.WebRequestOutcome {
          return when {
            url.contains("wikipedia.org") ->
              com.example.internet.WebRequestOutcome.Success(
                url = url,
                statusCode = 200,
                contentType = "application/json",
                body = """{"title":"বাংলাদেশ","extract":"বাংলাদেশ দক্ষিণ এশিয়ার একটি স্বাধীন ও সার্বভৌম রাষ্ট্র।"}""",
                byteCount = 128,
              )
            url.contains("open-meteo.com/v1/search") ->
              com.example.internet.WebRequestOutcome.Success(
                url = url,
                statusCode = 200,
                contentType = "application/json",
                body = """{"results":[{"name":"Dhaka","country":"Bangladesh","latitude":23.81,"longitude":90.41}]}""",
                byteCount = 96,
              )
            url.contains("open-meteo.com/v1/forecast") ->
              com.example.internet.WebRequestOutcome.Success(
                url = url,
                statusCode = 200,
                contentType = "application/json",
                body = """{"current":{"temperature_2m":28.5,"relative_humidity_2m":65,"wind_speed_10m":11.2}}""",
                byteCount = 90,
              )
            else ->
              com.example.internet.WebRequestOutcome.Success(
                url = url,
                statusCode = 200,
                contentType = "text/html",
                body =
                  "<html><head><title>MAX Safe Article</title><script>evil();</script></head>" +
                    "<body><p>Android assistants should always protect user privacy cleanly. " +
                    "Calm summarization turns long web pages into clear spoken answers.</p></body></html>",
                byteCount = 240,
              )
          }
        }

        override fun cancelAll() = Unit
      }

    val webReq = com.example.internet.WebRequestManager(context, logger, customTransport = fakeTransport)
    // Verify URL upgrade + tracker removal + private IP block
    val safeCheck = webReq.validateSafeUrl("http://example.com/news?id=5&utm_source=tracker&fbclid=xyz")
    assertTrue(safeCheck.isSafe)
    assertEquals("https://example.com/news?id=5", safeCheck.normalizedUrl)
    assertFalse(webReq.validateSafeUrl("http://127.0.0.1/admin").isSafe)

    val searchMgr = com.example.internet.SearchManager(webReq, logger)
    val browserMgr = com.example.internet.BrowserManager(context, webReq, logger)

    kotlinx.coroutines.runBlocking {
      val (wikiItems, wikiSummary) =
        searchMgr.search("বাংলাদেশ", com.example.internet.SearchCategory.WIKIPEDIA, preferBangla = true)
      assertEquals(1, wikiItems.size)
      assertTrue(wikiSummary.contains("বাংলাদেশ"))

      val (wxItems, wxSummary) =
        searchMgr.search("Dhaka", com.example.internet.SearchCategory.WEATHER, preferBangla = false)
      assertEquals(1, wxItems.size)
      assertTrue(wxSummary.contains("28.5°C"))

      val (ytItems, _) =
        searchMgr.search("Bangla music", com.example.internet.SearchCategory.YOUTUBE, preferBangla = true)
      assertTrue(ytItems.first().url.startsWith("https://www.youtube.com/results"))

      val pageResult = browserMgr.openAndReadWebsite("https://example.com/article", preferBangla = false)
      assertTrue(pageResult.isSuccess)
      val page = pageResult.getOrThrow()
      assertEquals("MAX Safe Article", page.title)
      assertFalse(page.readableText.contains("evil()"))
      assertTrue(page.summary.contains("Android assistants should always protect user privacy cleanly."))
    }
  }

  @Test
  fun `voice quality avoids repeating greetings or farewells and cleans text for speech`() {
    val logger = ErrorLogger()
    val memoryManager = MemoryManager(logger)
    memoryManager.initialize(true)
    val emotionManager = EmotionManager(EmotionDetector(), logger)
    val personality = PersonalityManager(emotionManager, memoryManager)

    val (_, g1) = personality.craftPersonalizedReply("হ্যালো", VoiceLanguage.BANGLA_BD)
    val (_, g2) = personality.craftPersonalizedReply("নমস্কার", VoiceLanguage.BANGLA_BD)
    assertNotEquals(g1, g2)

    val (_, f1) = personality.craftPersonalizedReply("goodbye", VoiceLanguage.ENGLISH_US)
    val (_, f2) = personality.craftPersonalizedReply("take care", VoiceLanguage.ENGLISH_US)
    assertNotEquals(f1, f2)

    // VoiceConfig enforces comfortable natural pace bounds
    val extremeFast = VoiceConfig(speechSpeed = 2.5f, speechStyle = SpeechStyle.CARING)
    assertTrue(extremeFast.effectiveSpeechRate() <= 1.25f)

    // Speech cleanup strips markdown symbols and raw long URLs into readable hostnames
    val clean = TTSManager.prepareForNaturalSpeech("**Breaking** info at https://www.bbc.com/news/world-123?utm_source=x")
    assertFalse(clean.contains("**"))
    assertFalse(clean.contains("https://"))
    assertTrue(clean.contains("bbc.com"))
    memoryManager.release()
  }

  @Test
  fun `phone manager blocks automatic actions and enforces confirmation and permissions`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val logger = ErrorLogger()
    val permManager = com.example.permissions.PermissionManager(context, logger)
    val privacy = com.example.permissions.PrivacyController({ permManager }, logger)
    val handler = com.example.permissions.PermissionRequestHandler(permManager, privacy, logger)

    var torchHardwareOn = false
    val fakeTorch =
      object : com.example.phone.TorchHardwareBackend {
        override fun hasFlashHardware(): Boolean = true
        override fun setTorchEnabled(enabled: Boolean): Result<Boolean> {
          torchHardwareOn = enabled
          return Result.success(enabled)
        }
        override fun releaseCallback() = Unit
      }

    val callMgr = com.example.phone.CallManager(context, logger)
    val smsMgr = com.example.phone.SMSManager(context, logger)
    val contactsMgr = com.example.phone.ContactsManager(context, logger)
    val alarmMgr = com.example.phone.AlarmManager(context, logger)
    val timerMgr = com.example.phone.TimerManager(logger)
    val stopwatchMgr = com.example.phone.StopwatchManager(logger)
    val calcMgr = com.example.phone.CalculatorManager(logger)
    val flashMgr = com.example.phone.FlashlightManager(context, logger, customBackend = fakeTorch)

    val phoneMgr =
      com.example.phone.PhoneManager(
        permissionController = permManager,
        privacyGuard = privacy,
        permissionRequestHandler = handler,
        callManager = callMgr,
        smsManager = smsMgr,
        contactsManager = contactsMgr,
        alarmManager = alarmMgr,
        timerManager = timerMgr,
        stopwatchManager = stopwatchMgr,
        calculatorManager = calcMgr,
        flashlightManager = flashMgr,
        errorTracker = logger,
      )

    // 1. Automatic triggers must always be blocked
    assertFalse(phoneMgr.requestCall("01700000000", initiatedByUser = false))
    assertTrue(phoneMgr.state.value is com.example.phone.PhoneState.BlockedByPrivacyPolicy)
    assertFalse(phoneMgr.requestSms("01700000000", "Hi", initiatedByUser = false))
    assertFalse(phoneMgr.requestAddOrEditContact("Rakib", "01700", initiatedByUser = false))
    assertFalse(phoneMgr.requestSetAlarm(7, 30, "Morning", initiatedByUser = false))
    assertFalse(phoneMgr.requestToggleFlashlight(true, initiatedByUser = false))
    assertFalse(torchHardwareOn)

    // 2. Contacts lookup must ask permission first
    assertFalse(phoneMgr.searchContacts("Rakib", initiatedByUser = true, preferBangla = true))
    assertTrue(phoneMgr.state.value is com.example.phone.PhoneState.PermissionRequired)
    assertEquals(com.example.permissions.AppPermission.CONTACTS, handler.pendingRequest.value?.permission)

    // 3. Explicit Call / SMS / Alarm / Flashlight require confirmation before execution
    assertTrue(phoneMgr.requestCall("+8801711000000", "Rakib", preferDirectCall = false, initiatedByUser = true))
    assertTrue(phoneMgr.state.value is com.example.phone.PhoneState.AwaitingConfirmation)
    phoneMgr.cancelPendingAction()
    assertTrue(phoneMgr.state.value is com.example.phone.PhoneState.Dormant)

    // 4. Flashlight turns on only after confirmation and turns off on resource release
    assertTrue(phoneMgr.requestToggleFlashlight(enable = true, initiatedByUser = true, requireConfirmation = true))
    assertFalse(torchHardwareOn) // Still OFF before Confirm!
    assertTrue(phoneMgr.confirmPendingAction())
    assertTrue(torchHardwareOn)
    phoneMgr.releaseTaskResources()
    assertFalse(torchHardwareOn)

    // 5. Alarm confirms and records safely
    assertTrue(phoneMgr.requestSetAlarm(6, 45, "Fajr Alarm", initiatedByUser = true))
    assertTrue(phoneMgr.scheduledAlarms.value.isEmpty())
    assertTrue(phoneMgr.confirmPendingAction())
    assertEquals("06:45", phoneMgr.scheduledAlarms.value.first().formattedTime)

    phoneMgr.release()
  }

  @Test
  fun `phone calculator, stopwatch, and timer handle bangla math, laps, and release cleanly`() {
    val logger = ErrorLogger()
    val calc = com.example.phone.CalculatorManager(logger)

    val banglaAdd = calc.evaluate("১৫ যোগ ২৫").getOrThrow()
    assertEquals("40", banglaAdd.result)

    val pct = calc.evaluate("25% of 200").getOrThrow()
    assertEquals("50", pct.result)

    assertTrue(calc.evaluate("10 / 0").isFailure)

    var fakeClockMs = 1_000L
    val stopwatch = com.example.phone.StopwatchManager(logger, elapsedRealtimeProvider = { fakeClockMs })
    stopwatch.start()
    fakeClockMs = 2_500L
    stopwatch.lapStopwatchProxyTestFix(stopwatch)
    assertEquals(1_500L, stopwatch.status.value.laps.first())
    stopwatch.pause()
    assertFalse(stopwatch.status.value.isRunning)
    stopwatch.release()

    val timer = com.example.phone.TimerManager(logger, elapsedRealtimeProvider = { fakeClockMs })
    assertTrue(timer.startTimer(90, "Tea Timer"))
    assertEquals(90, timer.status.value.remainingSeconds)
    timer.pauseTimer()
    assertTrue(timer.status.value.isPaused)
    timer.resetTimer()
    assertEquals(0, timer.status.value.totalSeconds)
    timer.release()
  }

  @Test
  fun `app manager blocks background launches, enforces confirmation, and suggests alternatives when missing`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val logger = ErrorLogger()
    val permManager = com.example.permissions.PermissionManager(context, logger)
    val privacy = com.example.permissions.PrivacyController({ permManager }, logger)

    val installedPackages = mutableSetOf("com.whatsapp", "com.google.android.youtube", "com.facebook.mlite")
    val launchedTargets = mutableListOf<String>()
    val fakeBackend =
      object : com.example.apps.AppLaunchBackend {
        override fun isPackageInstalled(packageName: String): Boolean = packageName in installedPackages

        override fun hasIntentHandler(action: String, uriString: String): Boolean =
          uriString.startsWith("https://") || uriString.startsWith("mailto:")

        override fun launchPackage(packageName: String): Result<String> {
          return if (packageName in installedPackages) {
            launchedTargets.add("pkg:$packageName")
            Result.success(packageName)
          } else {
            Result.failure(IllegalStateException("Not installed: $packageName"))
          }
        }

        override fun launchUri(action: String, uriString: String, packageHint: String?): Result<String> {
          launchedTargets.add("uri:$uriString")
          return Result.success(uriString)
        }

        override fun launchEmailComposer(
          recipientEmail: String,
          subject: String,
          body: String,
          packageHint: String?,
        ): Result<String> {
          launchedTargets.add("email:$recipientEmail")
          return Result.success("mailto:$recipientEmail")
        }
      }

    val launcher = com.example.apps.AppLauncher(fakeBackend, logger)
    val appMgr =
      com.example.apps.AppManager(
        permissionController = permManager,
        privacyGuard = privacy,
        appLauncher = launcher,
        whatsAppManager = com.example.apps.WhatsAppManager(launcher, logger),
        messengerManager = com.example.apps.MessengerManager(launcher, logger),
        facebookManager = com.example.apps.FacebookManager(launcher, logger),
        chromeManager = com.example.apps.ChromeManager(launcher, logger),
        youtubeManager = com.example.apps.YouTubeManager(launcher, logger),
        gmailManager = com.example.apps.GmailManager(launcher, logger),
        mapsManager = com.example.apps.MapsManager(launcher, logger),
        errorTracker = logger,
      )

    // 1. Background / non-user requests must ALWAYS be blocked
    assertFalse(appMgr.requestOpenApp(com.example.apps.SupportedApp.WHATSAPP, initiatedByUser = false))
    assertTrue(appMgr.state.value is com.example.apps.AppState.BlockedByPrivacyPolicy)
    assertTrue(launchedTargets.isEmpty())

    // 2. Installed WhatsApp requires explicit confirmation before launching
    assertTrue(appMgr.requestOpenApp(com.example.apps.SupportedApp.WHATSAPP, initiatedByUser = true))
    assertTrue(appMgr.state.value is com.example.apps.AppState.AwaitingConfirmation)
    assertTrue(launchedTargets.isEmpty())
    assertTrue(appMgr.confirmPendingAction())
    assertTrue(appMgr.state.value is com.example.apps.AppState.Launched)
    assertEquals(listOf("pkg:com.whatsapp"), launchedTargets)

    // 3. Uninstalled Messenger suggests Messenger Lite + Web fallback + Play Store
    assertFalse(appMgr.requestOpenApp(com.example.apps.SupportedApp.MESSENGER, initiatedByUser = true))
    val missingState = appMgr.state.value as com.example.apps.AppState.AppNotInstalledWithAlternatives
    val liteAlt =
      missingState.alternatives.first {
        it.kind == com.example.apps.AlternativeKind.INSTALLED_ALTERNATIVE_APP
      }
    assertEquals("com.facebook.mlite", liteAlt.targetValue)
    assertTrue(
      appMgr.requestOpenAlternative(
        com.example.apps.SupportedApp.MESSENGER,
        liteAlt,
        initiatedByUser = true,
      ),
    )
    assertTrue(appMgr.confirmPendingAction())
    assertTrue(launchedTargets.contains("pkg:com.facebook.mlite"))

    // 4. WhatsApp draft & Chrome URL validation enforce confirmation + safe HTTPS schemes
    assertFalse(appMgr.requestChromeUrl("javascript:alert(1)", initiatedByUser = true))
    assertTrue(appMgr.state.value is com.example.apps.AppState.Error)

    assertTrue(appMgr.requestWhatsAppChatDraft("+8801711000000", "কেমন আছেন?", initiatedByUser = true))
    assertTrue(appMgr.pendingConfirmation.value != null)
    appMgr.cancelPendingAction()
    assertTrue(appMgr.pendingConfirmation.value == null)

    appMgr.release()
  }

  @Test
  fun `app control handles bangla open commands cleanly without intercepting youtube search`() {
    val logger = ErrorLogger()
    val context = ApplicationProvider.getApplicationContext<Context>()
    val permManager = com.example.permissions.PermissionManager(context, logger)
    val privacy = com.example.permissions.PrivacyController({ permManager }, logger)

    val installed = setOf("com.whatsapp", "com.google.android.youtube", "com.google.android.apps.maps", "com.android.chrome")
    val fakeBackend =
      object : com.example.apps.AppLaunchBackend {
        override fun isPackageInstalled(packageName: String): Boolean = packageName in installed
        override fun hasIntentHandler(action: String, uriString: String): Boolean = true
        override fun launchPackage(packageName: String): Result<String> = Result.success(packageName)
        override fun launchUri(action: String, uriString: String, packageHint: String?): Result<String> = Result.success(uriString)
        override fun launchEmailComposer(recipientEmail: String, subject: String, body: String, packageHint: String?): Result<String> =
          Result.success("mailto:$recipientEmail")
      }
    val launcher = com.example.apps.AppLauncher(fakeBackend, logger)
    val appMgr =
      com.example.apps.AppManager(
        permissionController = permManager,
        privacyGuard = privacy,
        appLauncher = launcher,
        whatsAppManager = com.example.apps.WhatsAppManager(launcher, logger),
        messengerManager = com.example.apps.MessengerManager(launcher, logger),
        facebookManager = com.example.apps.FacebookManager(launcher, logger),
        chromeManager = com.example.apps.ChromeManager(launcher, logger),
        youtubeManager = com.example.apps.YouTubeManager(launcher, logger),
        gmailManager = com.example.apps.GmailManager(launcher, logger),
        mapsManager = com.example.apps.MapsManager(launcher, logger),
        errorTracker = logger,
      )

    listOf(
      "MAX, WhatsApp খোলো" to com.example.apps.SupportedApp.WHATSAPP,
      "MAX, YouTube খোলো" to com.example.apps.SupportedApp.YOUTUBE,
      "MAX, Maps খোলো" to com.example.apps.SupportedApp.GOOGLE_MAPS,
      "MAX, Chrome খোলো" to com.example.apps.SupportedApp.CHROME,
    ).forEach { (cmd, expectedApp) ->
      val reply = appMgr.handleVoiceCommand(cmd, preferBangla = true, initiatedByUser = true)
      assertTrue(reply != null)
      assertEquals(expectedApp, appMgr.pendingConfirmation.value?.app)
      appMgr.cancelPendingAction()
    }

    // YouTube search command should fall through to Internet SearchManager instead of launching bare app
    val searchFallthrough = appMgr.handleVoiceCommand("MAX, YouTube-এ সার্চ করো Bangla song", preferBangla = true, initiatedByUser = true)
    assertTrue(searchFallthrough == null)
    appMgr.release()
  }

  private fun com.example.phone.StopwatchManager.lapStopwatchProxyTestFix(
    sw: com.example.phone.StopwatchManager,
  ) {
    sw.recordLap()
  }
}
