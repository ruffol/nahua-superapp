package com.nahuanotoca.superapp.core.miniapp

import android.content.Context
import org.json.JSONObject

/**
 * Loads mini-app manifests from assets/miniapps/index.json. The file is the contract between the
 * app shell and the mini-app bundles, so third-party teams can ship bundles without touching
 * Kotlin code.
 */
class MiniAppRegistry(private val context: Context) {

    private val lock = Any()
    private var cached: List<MiniAppManifest>? = null

    val manifests: List<MiniAppManifest>
        get() = synchronized(lock) {
            cached ?: load().also { cached = it }
        }

    fun find(id: String): MiniAppManifest? = manifests.firstOrNull { it.id == id }

    private fun load(): List<MiniAppManifest> {
        val raw = runCatching {
            context.assets.open(INDEX_PATH).bufferedReader().use { it.readText() }
        }.getOrNull() ?: return emptyList()

        return runCatching {
            val apps = JSONObject(raw).getJSONArray("apps")
            buildList {
                for (index in 0 until apps.length()) {
                    val item = apps.getJSONObject(index)
                    add(
                        MiniAppManifest(
                            id = item.getString("id"),
                            name = item.getString("name"),
                            tagline = item.optString("tagline"),
                            version = item.optString("version", "1.0.0"),
                            entry = item.getString("entry"),
                            accent = item.optString("accent", "#0E7C66"),
                            permissions = item.optJSONArray("permissions")?.let { array ->
                                buildList {
                                    for (permissionIndex in 0 until array.length()) {
                                        add(array.getString(permissionIndex))
                                    }
                                }
                            } ?: emptyList(),
                            display = MiniAppDisplay.from(item.optString("display")),
                            orientation = MiniAppOrientation.from(item.optString("orientation")),
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private companion object {
        const val INDEX_PATH = "miniapps/index.json"
    }
}
