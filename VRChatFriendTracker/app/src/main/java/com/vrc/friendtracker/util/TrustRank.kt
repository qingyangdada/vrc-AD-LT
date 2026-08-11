package com.vrc.friendtracker.util

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