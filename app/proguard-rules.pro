# The mini-app runtime exposes a @JavascriptInterface bridge to WebView content.
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keepattributes JavascriptInterface
-keepclassmembers class com.nahuanotoca.superapp.core.miniapp.** {
    public *;
}

# pdfbox-android referencia opcionalmente un decodificador JPEG-2000 (com.gemalto.jp2) que no
# viene en el artefacto de Android. Sin esta regla, R8 aborta la compilación de release.
-dontwarn com.gemalto.jp2.**

# Kotlin metadata is used by reflection-free libraries; keep it for readable crash reports.
-keepattributes *Annotation*, InnerClasses, Signature, Exceptions
