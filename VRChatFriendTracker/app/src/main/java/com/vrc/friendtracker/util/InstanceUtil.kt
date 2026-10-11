package com.vrc.friendtracker.util

/** Helpers to interpret the location / instance id strings returned by the API. */
object InstanceUtil {

    data class LocationParts(
        val worldId: String?,
        val instanceId: String?,
        val isOnline: Boolean,
        val kind: Kind,
    ) {
        enum class Kind { ONLINE_WORLD, OFFLINE, PRIVATE, TRAVELING, LOCAL, UNKNOWN }
    }

    fun parseLocation(location: String?): LocationParts {
        if (location.isNullOrBlank()) return LocationParts(null, null, false, LocationParts.Kind.UNKNOWN)
        if (location.lowercase().startsWith("local")) {
            return LocationParts(null, null, true, LocationParts.Kind.LOCAL)
        }
        return when (location) {
            "offline", "offline:offline", "offline:" -> LocationParts(null, null, false, LocationParts.Kind.OFFLINE)
            "private", "private:private" -> LocationParts(null, null, true, LocationParts.Kind.PRIVATE)
            "traveling", "traveling:traveling" -> LocationParts(null, null, true, LocationParts.Kind.TRAVELING)
            "local", "local:local" -> LocationParts(null, null, true, LocationParts.Kind.LOCAL)
            else -> {
                val colon = location.indexOf(':')
                if (colon > 0) {
                    val worldId = location.substring(0, colon)
                    val instanceId = location.substring(colon + 1).ifBlank { null }
                    LocationParts(worldId, instanceId, true, LocationParts.Kind.ONLINE_WORLD)
                } else {
                    LocationParts(null, null, true, LocationParts.Kind.UNKNOWN)
                }
            }
        }
    }

    /** Group key for clustering friends in the same room; null when not in a room. */
    fun roomGroupKey(isOnline: Boolean, worldId: String?, instanceId: String?): String? {
        if (!isOnline || worldId.isNullOrBlank()) return null
        return "room:$worldId:${instanceId ?: ""}"
    }

    /** Key that groups room mates together; everyone else is its own group. */
    fun friendGroupKey(id: String, isOnline: Boolean, worldId: String?, instanceId: String?): String =
        roomGroupKey(isOnline, worldId, instanceId) ?: "solo:$id"

    /**
     * Access tags VRChat appends to the instance id. A **public** instance has
     * none of them: its id is just "<instanceNumber>~region(xx)", so the first
     * segment is a number and NOT the word "public". Only these tags make an
     * instance non-public (older joins used the bare "<tag>(...)" form).
     */
    private val NON_PUBLIC_TAGS = listOf(
        "~private(", "~friends(", "~hidden(", "~group(", "~grouppublic(",
        "~invite(", "~inviteplus(", "~canrequestinvite",
    )
    private val LEGACY_NON_PUBLIC_PREFIXES = listOf(
        "private(", "friends(", "hidden(", "group(", "grouppublic(",
        "invite(", "inviteplus(", "canrequestinvite",
    )

    /** True when the instance is not public (private, friends-only, hidden,
     *  group, invite+ or ask-to-join). */
    fun isPrivateAccess(instanceId: String?): Boolean {
        if (instanceId.isNullOrBlank()) return false
        val id = instanceId.lowercase()
        if (NON_PUBLIC_TAGS.any { id.contains(it) }) return true
        return LEGACY_NON_PUBLIC_PREFIXES.any { id.startsWith(it) }
    }

    /** Human readable instance access type parsed from the instance id string. */
    fun instanceTypeLabel(instanceId: String?): String {
        if (instanceId.isNullOrBlank()) return "未知"
        val id = instanceId.lowercase()
        // Official tag meanings (same mapping VRCX uses):
        //   ~hidden()  = Friends+      ~friends() = Friends
        //   ~private() = Invite        ~private()~canRequestInvite = Invite+
        //   ~group() + ~groupAccessType(public|plus|members) = Group variants
        val isPrivate = "~private(" in id || "~invite(" in id || "~inviteplus(" in id ||
            id.startsWith("private(") || id.startsWith("invite(") || id.startsWith("inviteplus(")
        val asksToJoin = "~canrequestinvite" in id || id.startsWith("canrequestinvite")
        val isGroup = "~group(" in id || "~grouppublic(" in id ||
            id.startsWith("group(") || id.startsWith("grouppublic(")
        return when {
            isPrivate && asksToJoin -> "私密+"
            isPrivate -> "私密"
            "~friends(" in id || id.startsWith("friends(") -> "好友"
            "~hidden(" in id || id.startsWith("hidden(") -> "好友+"
            isGroup -> when {
                "~groupaccesstype(public)" in id || "~grouppublic(" in id ||
                    id.startsWith("grouppublic(") -> "群组公开"
                "~groupaccesstype(plus)" in id -> "群组+"
                else -> "群组"
            }
            asksToJoin -> "可请求加入"
            else -> "公开"
        }
    }

    fun regionLabel(region: String?): String? {
        if (region.isNullOrBlank()) return null
        return when (region) {
            "us" -> "美西"
            "use" -> "美东"
            "eu" -> "欧洲"
            "jp" -> "日本"
            "kr" -> "韩国"
            else -> region
        }
    }
}
