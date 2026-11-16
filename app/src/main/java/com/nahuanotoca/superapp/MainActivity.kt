package com.nahuanotoca.superapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nahuanotoca.superapp.core.appContainer
import com.nahuanotoca.superapp.core.data.AppPreferences
import com.nahuanotoca.superapp.core.data.ThemeMode
import com.nahuanotoca.superapp.core.design.NahuaTheme
import com.nahuanotoca.superapp.ui.NahuaApp

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = applicationContext.appContainer

        setContent {
            val preferences by container.preferences.preferences
                .collectAsStateWithLifecycle(initialValue = AppPreferences())
            val darkTheme = when (preferences.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }

            NahuaTheme(darkTheme = darkTheme) {
                NahuaApp(container = container)
            }
        }
    }
}