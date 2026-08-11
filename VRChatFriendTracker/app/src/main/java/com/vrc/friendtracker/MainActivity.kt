package com.vrc.friendtracker

import android.content.Context
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.vrc.friendtracker.ui.AppRoot
import com.vrc.friendtracker.ui.theme.AppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppTheme {
                AppRoot()
            }
        }
        capDisplayRefreshRate(90f)
    }

    /**
     * Requests a 90 Hz display mode for this window. High-refresh panels
     * (120 Hz) can push weaker GPUs past their frame budget while scrolling;
     * fixing the rate at 90 Hz keeps frame pacing stable. If the panel has no
     * 90 Hz mode the request is ignored and the default mode is kept.
     */
    private fun capDisplayRefreshRate(targetHz: Float) {
        @Suppress("DEPRECATION")
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        @Suppress("DEPRECATION")
        val display = wm.defaultDisplay ?: return
        @Suppress("DEPRECATION")
        val mode = display.supportedModes
            .filter { it.refreshRate in (targetHz - 0.5f)..(targetHz + 0.5f) }
            .minByOrNull { kotlin.math.abs(it.refreshRate - targetHz) }
            ?: return
        val lp = window.attributes
        lp.preferredDisplayModeId = mode.modeId
        window.attributes = lp
    }
}