# Proguard rules para Airsoft Tracker

# Kotlin serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.airsoft.tracker.**$$serializer { *; }
-keepclassmembers class com.airsoft.tracker.** {
    *** Companion;
}
-keepclasseswithmembers class com.airsoft.tracker.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Socket.IO
-keep class io.socket.** { *; }
-dontwarn io.socket.**

# OkHttp / Retrofit
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }

# Google Play Services (Location)
-keep class com.google.android.gms.location.** { *; }

# MapLibre (mapa offline/tiles)
-dontwarn org.maplibre.**
-keep class org.maplibre.** { *; }