# Keep annotations, generic signatures, and line numbers for readable crash reports
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,Exceptions,SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Kotlin Metadata
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.Metadata

# Kotlinx Serialization
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.osuchat.app.**$$serializer { *; }
-keepclassmembers class com.osuchat.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.osuchat.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Retrofit
-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations,AnnotationDefault
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-if interface * { @retrofit2.http.* <methods>; }
-keep,allowobfuscation interface <1>
-if interface * { @retrofit2.http.* <methods>; }
-keep,allowobfuscation interface * extends <1>
-if interface * { @retrofit2.http.* public *** *(...); }
-keep,allowoptimization,allowshrinking,allowobfuscation class <3>
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-dontwarn javax.annotation.**
-dontwarn kotlin.Unit
-dontwarn retrofit2.Platform
-dontwarn retrofit2.Platform$Java8

# OkHttp & Okio
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# Cronet & Cronet OkHttp Transport
-keep class org.chromium.net.** { *; }
-keep class com.google.net.cronet.** { *; }
-keep class com.google.android.gms.net.** { *; }
-dontwarn org.chromium.net.**
-dontwarn com.google.net.cronet.**
-dontwarn android.util.StatsEvent
-dontwarn android.util.StatsEvent$*
-dontwarn android.util.StatsLog

# Gson & Network DTOs
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken { *; }
-keep,allowobfuscation @com.google.gson.annotations.JsonAdapter class *
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-if class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keepclassmembers,allowobfuscation,allowoptimization class <1> {
    <init>();
}
-keep class com.osuchat.app.data.network.** { *; }
-keep class com.osuchat.app.domain.model.** { *; }
-keep class com.osuchat.app.data.repository.OsekaiMedalDto { *; }
-keep class com.osuchat.app.data.repository.MedalsCatalogRepository$** { *; }
-keep class com.osuchat.app.data.repository.OsuTrackStatEntryDto { *; }
-keep class com.osuchat.app.data.repository.OsuTrackPeakDto { *; }
-keep class com.osuchat.app.data.repository.OsuTrackRepository$** { *; }
-keep class com.osuchat.app.data.repository.GitHubReleaseDto { *; }
-keep class com.osuchat.app.data.repository.GitHubAssetDto { *; }
-keep class com.osuchat.app.data.repository.AppUpdateRepository$** { *; }
-dontwarn sun.misc.Unsafe

# Room Entities, DAOs, and Database
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Database class * { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-keep @androidx.room.Dao class * { *; }
-keep class * extends androidx.room.migration.Migration { *; }
-keep class com.osuchat.app.data.local.** { *; }
-dontwarn androidx.room.paging.**

# Hilt (@HiltAndroidApp, @AndroidEntryPoint, @HiltViewModel, @HiltWorker, @EntryPoint)
-keep @dagger.hilt.android.HiltAndroidApp class * { *; }
-keep @dagger.hilt.android.AndroidEntryPoint class * { *; }
-keep @dagger.hilt.android.lifecycle.HiltViewModel class * { *; }
-keep @androidx.hilt.work.HiltWorker class * { *; }
-keep @dagger.hilt.EntryPoint interface * { *; }
-keep @dagger.hilt.EntryPoint class * { *; }
-keep @dagger.hilt.android.EarlyEntryPoint interface * { *; }
-keep @dagger.hilt.android.EarlyEntryPoint class * { *; }
-keep @dagger.hilt.internal.ComponentEntryPoint interface * { *; }
-keep @dagger.hilt.internal.GeneratedEntryPoint interface * { *; }
-keep class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}
-keep class * extends androidx.work.ListenableWorker {
    public <init>(...);
}
-dontwarn androidx.hilt.work.**

# Jetpack Compose
-keep class androidx.compose.runtime.internal.ComposableLambda { *; }
-keepclassmembers,allowshrinking,allowobfuscation class * {
    @androidx.compose.runtime.Composable <methods>;
}
-dontwarn androidx.compose.**

# RosuPpEngine & WebView @JavascriptInterface bridges
-keepattributes JavascriptInterface
-keepclasseswithmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keep class com.osuchat.app.util.RosuPpEngine { *; }
-keep class com.osuchat.app.util.RosuPpEngine$RawRosuResult { *; }
-keep class com.osuchat.app.util.RosuPpEngine$RosuJsBridge { *; }
-keep class com.osuchat.app.util.RosuPpEngine$** { *; }
-keepclassmembers class android.webkit.WebView {
    public *;
}
-keepclassmembers class android.webkit.WebViewClient {
    public *;
}
-dontwarn android.webkit.**

# Quick Settings TileService, Shortcuts, ScoreCardRenderer & FileProvider
-keep class com.osuchat.app.service.BanchoQuickSettingsTileService { *; }
-keep class com.osuchat.app.util.AppShortcutManager { *; }
-keep class com.osuchat.app.util.ScoreCardRenderer { *; }
-keep class com.osuchat.app.util.ScoreCardRenderer$** { *; }
-keep class androidx.core.content.FileProvider { *; }
