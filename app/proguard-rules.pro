# Project specific ProGuard rules

# Jetpack Compose
-keepclassmembers class androidx.compose.ui.platform.AndroidComposeView {
    void *;
}
-keep class androidx.compose.ui.platform.** { *; }
-keep interface androidx.compose.ui.platform.** { *; }

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# Coil
-keep class coil.** { *; }
-keep interface coil.** { *; }

# Serialization (if any data classes are used for JSON/AI)
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.ungker.ungkeh.data.** { *; }
-keep class com.ungker.ungkeh.Verse { *; }
-keep class com.ungker.ungkeh.ChapterMeta { *; }
-keep class com.ungker.ungkeh.PrayerTimes { *; }

# Material3
-keep class androidx.compose.material3.** { *; }

# Build Config
-keep class com.ungker.ungkeh.BuildConfig { *; }
