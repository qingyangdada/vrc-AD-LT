package com.vrc.friendtracker.util

import androidx.compose.ui.graphics.Color

/**
 * VRChat trust rank. The API does not expose it directly; it is derived from
 * system tags on the full user profile (the friends-list endpoint always returns
 * empty tags). Mapping follows the official English rank names used by VRChat:
 *   system_trust_veteran -> Trusted User
 *   system_trust_trusted -> Known User
 *   system_trust_known   -> User
 *   system_trust_basic   -> New User
 *   none                 -> Visitor
 */
enum class TrustRank(val en: String, val sortOrder: Int) {
    VISITOR("Visitor", 0),
    NEW_USER("New User", 1),
    USER("User", 2),
    KNOWN_USER("Known User", 3),
    TRUSTED_USER("Trusted User", 4);

    companion object {
        fun fromTags(tags: List<String>?): TrustRank {
            if (tags.isNullOrEmpty()) return VISITOR
            return when {
                "system_trust_veteran" in tags -> TRUSTED_USER
                "system_trust_trusted" in tags -> KNOWN_USER
                "system_trust_known" in tags -> USER
                "system_trust_basic" in tags -> NEW_USER
                else -> VISITOR
            }
        }

        fun isTroll(tags: List<String>?): Boolean =
            !tags.isNullOrEmpty() &&
                ("system_troll" in tags || "system_probable_troll" in tags)

        fun isModerator(tags: List<String>?, developerType: String?): Boolean =
            !tags.isNullOrEmpty() && "admin_moderator" in tags ||
                developerType != null && developerType != "none"
    }
}

/** Name color per trust rank: User -> green, Known User -> yellow,
 *  Trusted User -> purple; everyone else uses the default text color.
 *  Dark theme uses bright tints; light theme uses darker shades for contrast. */
fun trustNameColor(rank: TrustRank, isDark: Boolean): Color = when (rank) {
    // User = green, Known User = yellow, Trusted User = purple; all other
    // ranks fall back to the theme default text color (white in dark mode).
    TrustRank.USER -> if (isDark) Color(0xFF66BB6A) else Color(0xFF2E7D32)
    TrustRank.KNOWN_USER -> if (isDark) Color(0xFFFFD54F) else Color(0xFFF9A825)
    TrustRank.TRUSTED_USER -> if (isDark) Color(0xFFCE93D8) else Color(0xFF7B1FA2)
    else -> Color.Unspecified
}