# Xibo Player ProGuard rules
# Keep XMDS client and model classes
-keep class org.xiboplayer.player.model.** { *; }
-keep class org.xiboplayer.player.api.** { *; }

# Keep OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# Keep ExoPlayer
-dontwarn androidx.media3.**
-keep class androidx.media3.** { *; }

# Keep Coil
-dontwarn coil.**

# Keep Gson
-keep class com.google.gson.** { *; }
-keepattributes Signature
-keepattributes *Annotation*

# Keep WebView
-keepclassmembers class * extends android.webkit.WebView {
    public *;
}
