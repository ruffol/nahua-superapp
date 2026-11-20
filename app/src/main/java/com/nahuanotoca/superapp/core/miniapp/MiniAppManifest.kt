package com.nahuanotoca.superapp.core.miniapp

/**
 * Declarative description of a mini-app. The entry points to a bundled HTML bundle inside
 * assets/miniapps; the host serves it through WebViewAssetLoader on a https origin.
 */
data class MiniAppManifest(
    val id: String,
    val name: String,
    val tagline: String,
    val version: String,
    val entry: String,
    val accent: String,
    val permissions: List<String> = emptyList(),
    /**
     * "chrome" keeps the native Nahua frame (header, wallet footer, payment sheet). "immersive"
     * drops it: the bundle owns the whole screen, like the GBA emulator, which rotates to
     * landscape and hides the system bars.
     */
    val display: MiniAppDisplay = MiniAppDisplay.CHROME,
    /**
     * "landscape" asks the host to rotate the screen while the mini-app is open. "auto" leaves
     * the sensor alone.
     */
    val orientation: MiniAppOrientation = MiniAppOrientation.AUTO,
) {
    val entryUrl: String get() = ASSET_ORIGIN + "assets/" + entry

    companion object {
        const val ASSET_ORIGIN: String = "https://appassets.androidplatform.net/"
    }
}

enum class MiniAppDisplay {
    CHROME,
    IMMERSIVE;

    companion object {
        /** Tolerant parse: an unknown value in index.json keeps the safe default. */
        fun from(raw: String?): MiniAppDisplay = when (raw?.lowercase()) {
            "immersive" -> IMMERSIVE
            else -> CHROME
        }
    }
}

enum class MiniAppOrientation {
    AUTO,
    LANDSCAPE,
    PORTRAIT;

    companion object {
        fun from(raw: String?): MiniAppOrientation = when (raw?.lowercase()) {
            "landscape" -> LANDSCAPE
            "portrait" -> PORTRAIT
            else -> AUTO
        }
    }
}
