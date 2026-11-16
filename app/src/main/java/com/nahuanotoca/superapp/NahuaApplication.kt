package com.nahuanotoca.superapp

import android.app.Application
import com.nahuanotoca.superapp.core.AppContainer

/**
 * Application entry point. Dependencies are wired manually through [AppContainer]; the
 * container is intentionally small so it can be replaced by Hilt without touching features.
 */
class NahuaApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
