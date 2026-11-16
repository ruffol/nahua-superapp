package com.nahuanotoca.superapp.core.navigation

object Routes {
    const val HOME = "home"
    const val SERVICES = "services"
    const val WALLET = "wallet"
    const val PROFILE = "profile"
    const val SCAN = "scan"
    const val INBOX = "inbox"
    const val SEARCH = "search"
    const val SETTINGS = "settings"

    const val MINI_APP_PATTERN = "miniapp/{appId}"
    const val MINI_APP_ARG = "appId"

    fun miniApp(appId: String): String = "miniapp/" + appId
}