package com.nahuanotoca.superapp.core.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

private const val WALLET_STORE = "nahua_wallet"
private const val PREFERENCES_STORE = "nahua_preferences"

val Context.walletStore: DataStore<Preferences> by preferencesDataStore(name = WALLET_STORE)

val Context.appPreferencesStore: DataStore<Preferences> by preferencesDataStore(name = PREFERENCES_STORE)
