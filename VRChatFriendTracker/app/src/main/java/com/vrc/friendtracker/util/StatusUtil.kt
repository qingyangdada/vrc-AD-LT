package com.vrc.friendtracker.util

import androidx.compose.ui.graphics.Color

/**
 * VRChat status lights (same colors/labels as the official site):
 *  - active   -> green  (online)
 *  - join me  -> blue
 *  - ask me   -> yellow
 *  - busy     -> red
 *  - offline  -> grey
 */
object Status {
    const val ACTIVE = "active"
    const val JOIN_ME = "join me"
    const val ASK_ME = "ask me"
    const val BUSY = "busy"
    const val OFFLINE = "offline"

    fun color(status: String?, isOnline: Boolean): Color = when {
        !isOnline -> Color(0xFF9E9E9E)
        status == JOIN_ME -> Color(0xFF3498DB)
        status == ASK_ME -> Color(0xFFF1C40F)
        status == BUSY -> Color(0xFFE74C3C)
        else -> Color(0xFF2ECC71)
    }

    fun label(status: String?, isOnline: Boolean): String = when {
        !isOnline -> "离线"
        status == JOIN_ME -> "Join Me"
        status == ASK_ME -> "Ask Me"
        status == BUSY -> "Busy"
        else -> "在线"
    }

    /**
     * Sort weight for the friend list, top to bottom:
     * Join Me (blue) -> Online / Active (green) -> Ask Me (yellow) -> Busy (red).
     * Offline friends always come last.
     */
    fun priority(status: String?, isOnline: Boolean): Int = when {
        !isOnline -> 90
        status == JOIN_ME -> 0
        status == ASK_ME -> 2
        status == BUSY -> 3
        else -> 1
    }

    /**
     * Presence of a user object. VRChat reports your own `location` as
     * "offline" whenever the game client is not reporting presence, so the
     * `state` field ("online" / "active") is treated as an equally valid
     * "is this account online" signal instead of trusting location alone.
     */
    fun isOnline(location: String?, state: String?): Boolean {
        if (state == "online" || state == "active") return true
        return !location.isNullOrBlank() && location != OFFLINE
    }
}
