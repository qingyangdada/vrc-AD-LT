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
        return when (location) {
            "offline" -> LocationParts(null, null, false, LocationParts.Kind.OFFLINE)
            "private" -> LocationParts(null, null, true, LocationParts.Kind.PRIVATE)
            "traveling" -> LocationParts(null, null, true, LocationParts.Kind.TRAVELING)
            "local" -> LocationParts(null, null, true, LocationParts.Kind.LOCAL)
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
    /** True when the instance is not a public one (private, hidden, friends,
     *  group, ask-to-join, local and any future non-public access type).
     *  Instance ids look like "<access>~<region>~..." or "<access>(...)". */
    fun isPrivateAccess(instanceId: String?): Boolean {
        if (instanceId.isNullOrBlank()) return false
        val head = instanceId.substringBefore('~').substringBefore('(').trim().lowercase()
        return head.isNotEmpty() && head != "public"
    }
    /** Human readable instance access type parsed from the instance id string. */
    fun instanceTypeLabel(instanceId: String?): String {
        if (instanceId.isNullOrBlank()) return "未知"
        return when {
            instanceId.startsWith("private") || instanceId.startsWith("hidden") ||
                "~private(" in instanceId || "~hidden(" in instanceId -> "私密"
            instanceId.startsWith("friends") || "~friends(" in instanceId -> "好友+"
            instanceId.startsWith("group") || instanceId.startsWith("grp_") ||
                "~group(" in instanceId -> "群组"
            instanceId.startsWith("canRequestInvite") || "~canRequestInvite(" in instanceId -> "可请求加入"
            isPrivateAccess(instanceId) -> "私密"
            else -> "公开"
        }
    }

    fun regionLabel(region: String?): String? {
        if (region.isNullOrBlank()) return null
        return when (region) {
            "us" -> "美国"
            "eu" -> "欧洲"
            "jp" -> "日本"
            "kr" -> "韩国"
            else -> region
        }
    }
}