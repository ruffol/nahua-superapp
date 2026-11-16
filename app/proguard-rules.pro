# The mini-app runtime exposes a @JavascriptInterface bridge to WebView content.
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keepattributes JavascriptInterface
-keepclassmembers class com.nahuanotoca.superapp.core.miniapp.** {
    public *;
}

# Kotlin metadata is used by reflection-free libraries; keep it for readable crash reports.
-keepattributes *Annotation*, InnerClasses, Signature, Exceptions
