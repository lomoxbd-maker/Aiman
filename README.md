# MAX — Personal Bangla & English AI Assistant for Android

**MAX** is a privacy-first, modular Kotlin & Jetpack Compose AI Assistant designed for Android 8.0 (API 26) and above (`minSdk = 24`, `targetSdk = 36`).

## Core Principles
- **Privacy First**: Never opens the camera, reads gallery/files/contacts/calendar, enables GPS, or browses the internet automatically. Every sensitive capability runs strictly on explicit user command and confirmation.
- **Natural Bangla & English Voice**: Warm, calm speech prosody (`0.90f` default comfortable rate), natural sentence pauses, emotional tone adaptation, and varied human-like responses.
- **Sanju Wake-Word Loop**: Listens locally in low-power standby for `"MAX"` -> starts speech recognition -> reasons via **MAX AI Brain** (`IntentAnalyzer`, `ContextManager`, `DecisionEngine`, `ResponsePlanner`) -> executes task -> releases hardware -> asks `"সানজু, আপনাকে কি আর কোনোভাবে সাহায্য করতে পারি?"` -> returns calmly to Wake Mode if idle.

## Modular Architecture (`com.example.*`)
| Package | Module | Responsibility |
| --- | --- | --- |
| `core/` | `MAXCore` | Lifecycle orchestrator, background cleanup, and subsystem routing |
| `brain/` | `AIBrainManager`, `IntentAnalyzer`, `ContextManager`, `DecisionEngine`, `ResponsePlanner` | Offline-first reasoning, active session context, clarification questions, and internet consent gating |
| `wakeword/` | `WakeWordManager` | Privacy-safe `"MAX"` keyword standby with RAM-only frame buffering |
| `speech/` | `SpeechManager` | Post-wake speech recognition via `android.speech.SpeechRecognizer` |
| `tts/` | `TTSManager` | Calm Bangla & English Text-to-Speech with pause cadence and fallback |
| `conversation/` | `ConversationManager` | Multi-turn dialogue connected with Brain, Emotion, Memory & TTS |
| `emotion/` | `EmotionManager`, `PersonalityManager` | 7-state emotional adaptation & respectful Sanju greetings |
| `memory/` | `MemoryManager` | Bounded local user memory ring buffer with privacy switch |
| `permissions/` | `PermissionManager`, `PrivacyController`, `PermissionRequestHandler` | Zero-trust runtime permission & privacy policy enforcement |
| `camera/` & `gallery/` | `CameraManager`, `GalleryManager` | User-triggered CameraX photo/selfie/video/QR & Photo Picker + OCR |
| `internet/` | `InternetManager`, `SearchManager`, `BrowserManager`, `WebRequestManager` | User-initiated HTTPS Web, News, Weather, Wikipedia & YouTube search |
| `phone/` | `PhoneManager` + utilities | Confirmation-locked Calls, SMS, Contacts, Alarm, Timer, Stopwatch, Calculator, Flashlight |
| `apps/` | `AppManager` + app controllers | Confirmation-locked WhatsApp, Messenger, Facebook, Chrome, YouTube, Gmail, Maps |
| `files/` & `smart/` | `FileManager`, `SmartFeatureManager` | SAF file reading (PDF/Docs/Images/OCR), Translation, Reminders, Notes, Calendar, Voice Notes & AI Suggestions |
| `update/` | `AppUpdateManager` | Play-compliant HTTPS update manifest checker (zero dynamic code loading) |

## Build & Testing
```bash
# Run JVM & Robolectric unit tests across all subsystems
gradle :app:testDebugUnitTest

# Build debug APK
gradle :app:assembleDebug

# Build minified R8 release APK (provide signing credentials via environment variables)
KEYSTORE_PATH=/path/to/upload.jks STORE_PASSWORD=... KEY_PASSWORD=... gradle :app:assembleRelease
```
