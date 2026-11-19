package com.nahuanotoca.superapp.feature.miniapp

import android.content.Context
import androidx.webkit.WebViewAssetLoader
import com.nahuanotoca.superapp.core.miniapp.RomStore

/**
 * Asset loader compartido por todas las mini-apps. Sirve dos origenes sobre
 * https://appassets.androidplatform.net:
 *
 *  - /assets/  el bundle HTML/CSS/JS empaquetado en el APK.
 *  - /rom/     las ROMs que el usuario importo a filesDir/roms (emulador). El directorio vive
 *              bajo filesDir, que es exactamente lo que InternalStoragePathHandler permite;
 *              la pagina nunca ve file:// ni el resto del almacenamiento interno (databases/,
 *              shared_prefs/ y amigos estan vetados por el propio handler).
 *
 * Nota sobre WebAssembly: no hace falta reescribir el MIME de .wasm ni de los .data del nucleo.
 * EmulatorJS los descarga como arraybuffer y los instancia desde un Blob con type
 * "application/wasm"; el mime del disco nunca participa.
 */
object MiniAppAssetLoader {

    /** Origen unico del que puede salir todo el contenido de una mini-app. */
    const val HOST: String = "appassets.androidplatform.net"

    fun build(context: Context): WebViewAssetLoader =
        WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
            .addPathHandler(
                "/rom/",
                WebViewAssetLoader.InternalStoragePathHandler(context, RomStore.directory(context)),
            )
            .build()
}
