package com.vrc.friendtracker.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object TimeUtil {

    private val monthDayFormatter = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
    private val fullFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    private val isoParser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    /** Formats epoch millis as "MM-dd HH:mm" (today shows "HH:mm"). */
    fun formatEpoch(epochMillis: Long?): String {
        if (epochMillis == null || epochMillis <= 0L) return "—"
        val now = System.currentTimeMillis()
        val date = Date(epochMillis)
        return if (now - epochMillis < 24 * 3600 * 1000L) {
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(date)
        } else {
            monthDayFormatter.format(date)
        }
    }

    fun formatFull(epochMillis: Long?): String {
        if (epochMillis == null || epochMillis <= 0L) return "—"
        return fullFormatter.format(Date(epochMillis))
    }

    /** Parses VRChat ISO-8601 strings like "2024-01-01T12:34:56.789Z" into epoch millis. */
    fun parseIsoToEpoch(iso: String?): Long? {
        if (iso.isNullOrBlank()) return null
        val cleaned = iso.replace("Z", "")
        val withMillis = if ('.' in cleaned) cleaned.substringBefore('.') else cleaned
        return try {
            isoParser.parse(withMillis)?.time
        } catch (_: Exception) {
            null
        }
    }

    /** "x分钟前 / x小时前 / x天前" style relative time. */
    fun relative(epochMillis: Long?): String {
        if (epochMillis == null || epochMillis <= 0L) return "—"
        val diff = System.currentTimeMillis() - epochMillis
        val minutes = diff / 60000L
        return when {
            minutes < 1 -> "刚刚"
            minutes < 60 -> "${minutes}分钟前"
            minutes < 1440 -> "${minutes / 60}小时前"
            else -> "${minutes / 1440}天前"
        }
    }
}