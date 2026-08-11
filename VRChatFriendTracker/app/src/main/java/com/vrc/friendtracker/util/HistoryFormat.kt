package com.vrc.friendtracker.util

import com.vrc.friendtracker.data.db.HistoryEventEntity
import com.vrc.friendtracker.data.db.HistoryType
import com.vrc.friendtracker.data.db.WorldEntity

/** Human-readable text for a history event, used by the live feed and the records screen. */
fun formatChange(event: HistoryEventEntity, worlds: Map<String, WorldEntity>): String {
    val old = readableValue(event.type, event.oldValue, worlds)
    val new = readableValue(event.type, event.newValue, worlds)
    return when (event.type) {
        HistoryType.BIO -> "简介已更新"
        HistoryType.RANK -> if (old.isBlank()) "Trust rank → $new" else "Trust rank $old → $new"
        HistoryType.STATUS -> if (new == "在线") "上线了" else "下线了"
        HistoryType.ROOM -> when {
            old.isBlank() -> "进入「$new」"
            new.isBlank() -> "离开「$old」"
            else -> "「$old」→「$new」"
        }
        else -> if (old.isBlank()) new else "$old → $new"
    }
}

fun readableValue(type: String, value: String?, worlds: Map<String, WorldEntity>): String {
    if (value.isNullOrBlank()) return ""
    return when (type) {
        HistoryType.RANK -> runCatching { TrustRank.valueOf(value) }.getOrNull()?.en ?: value
        HistoryType.ROOM -> worlds[value]?.name ?: value
        else -> value
    }
}