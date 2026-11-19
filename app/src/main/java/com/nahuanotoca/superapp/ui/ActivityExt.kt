package com.nahuanotoca.superapp.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

/**
 * Resuelve la Activity contenedor desde cualquier Context de Compose (LocalContext, receiver de
 * permisos, etc). La usan la app (iconos del sistema) y la pantalla de mini-apps inmersivas
 * (rotacion a horizontal y barras del sistema).
 */
fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
