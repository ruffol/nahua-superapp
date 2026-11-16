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
) {
    val entryUrl: String get() = ASSET_ORIGIN + "assets/" + entry

    companion object {
        const val ASSET_ORIGIN: String = "https://appassets.androidplatform.net/"
    }
}
