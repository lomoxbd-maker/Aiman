# ProGuard / R8 Production Rules for MAX Android AI Assistant

# Preserve source line numbers for clean crash stack traces without exposing secrets
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Strip verbose debug/info logcat calls in release builds
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

# Preserve MAX core domain models, state sealed hierarchies, and enum values
-keep class com.example.core.** { *; }
-keep class com.example.brain.** { *; }
-keep class com.example.automation.** { *; }
-keep class com.example.floating.** { *; }
-keep class com.example.apps.** { *; }
-keep class com.example.files.** { *; }
-keep class com.example.smart.** { *; }
-keep class com.example.update.** { *; }
-keep class com.example.settings.** { *; }
-keep class com.example.memory.** { *; }
-keep class com.example.permissions.** { *; }

# Keep Jetpack Compose & Lifecycle ViewModel classes
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}

# Keep Coroutines service loader & FastServiceLoader rules clean
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-dontwarn kotlinx.coroutines.**

# CameraX & Camera2 interop rules
-keep class androidx.camera.** { *; }
-dontwarn androidx.camera.**

# OkHttp & ZXing QR core rules
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class com.google.zxing.** { *; }

# Keep parcelables & custom DataStore keys intact
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}
