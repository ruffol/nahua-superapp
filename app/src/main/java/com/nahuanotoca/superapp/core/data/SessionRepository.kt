package com.nahuanotoca.superapp.core.data

import com.nahuanotoca.superapp.core.model.UserProfile

/**
 * Demo session. Replace with OAuth/OIDC + secure token storage before shipping; the mini-app
 * bridge exposes only the fields returned here.
 */
class SessionRepository {

    val profile = UserProfile()

    fun asJson(): String = buildString {
        append("{")
        append("\"name\":\"").append(profile.name).append("\",")
        append("\"handle\":\"").append(profile.handle).append("\",")
        append("\"phone\":\"").append(profile.phone).append("\",")
        append("\"tier\":\"").append(profile.tier).append("\",")
        append("\"memberSince\":\"").append(profile.memberSince).append("\",")
        append("\"initials\":\"").append(profile.avatarInitials).append("\"")
        append("}")
    }
}
