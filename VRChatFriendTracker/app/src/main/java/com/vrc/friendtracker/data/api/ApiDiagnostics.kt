package com.vrc.friendtracker.data.api

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * In-memory ring buffer of recent HTTP/auth diagnostics, shown in the
 * Settings screen to help debug session issues.
 */
object ApiDiagnostics {

    private val _lines = MutableStateFlow<List<String>>(emptyList())
    val lines: StateFlow<List<String>> = _lines.asStateFlow()

    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    @Synchronized
    fun log(message: String) {
        val line = "[${timeFormat.format(Date())}] $message"
        _lines.value = (_lines.value + line).takeLast(30)
    }

    fun clear() {
        _lines.value = emptyList()
    }
}