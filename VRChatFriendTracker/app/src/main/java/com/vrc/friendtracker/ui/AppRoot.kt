package com.vrc.friendtracker.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontFamily
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.vrc.friendtracker.VrApp
import com.vrc.friendtracker.ui.detail.FriendDetailScreen
import com.vrc.friendtracker.ui.friends.FriendsScreen
import com.vrc.friendtracker.ui.history.HistoryScreen
import com.vrc.friendtracker.ui.login.LoginScreen
import com.vrc.friendtracker.ui.own.OwnProfileScreen
import com.vrc.friendtracker.ui.settings.SettingsScreen

object Screen {
    const val LOGIN = "login"
    const val FRIENDS = "friends"
    const val SETTINGS = "settings"
    const val HISTORY = "history"
    const val ME = "me"
    fun friend(id: String) = "friend/$id"
}

@Composable
fun AppRoot() {
    val context = LocalContext.current
    val app = context.applicationContext as VrApp
    val navController = rememberNavController()
    val sessionActive by app.sessionActive.collectAsState()
    // The graph start destination is captured once: passing a value that can
    // flip would rebuild the graph (and reset the back stack) whenever the
    // session state changed.
    val startDestination = remember { if (sessionActive) Screen.FRIENDS else Screen.LOGIN }
    // Never pop the last back stack entry. An empty back stack leaves the
    // NavHost with nothing to draw, which shows up as a blank white window.
    val goBack: () -> Unit = { navController.navigateUp() }

    // Any session loss (logout / token expiry) returns to the login screen.
    LaunchedEffect(sessionActive) {
        if (!sessionActive) {
            navController.navigate(Screen.LOGIN) {
                popUpTo(0) { inclusive = true }
            }
        } else {
            // Restart the real-time monitor once the UI is actually visible
            // (avoids starting a foreground service from Application.onCreate,
            // which Android can reject while the process is still cold).
            app.ensureMonitorRunning()
            // Silent GitHub update check (at most once per hour).
            app.updateCenter.checkSilently()
        }
    }

    CrashNoticeDialog()
    UpdateNoticeDialog()

    NavHost(
        navController = navController,
        startDestination = startDestination,
        // Opaque themed background: without it the activity's (white) window
        // background shows through whenever a destination is not drawn yet.
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        // Transitions are disabled on purpose. navigation-compose 2.8 animates
        // destination changes, and an interrupted transition can leave the new
        // screen half-composed (a blank window, no crash logged). Instant
        // switches remove that whole failure mode.
        enterTransition = { EnterTransition.None },
        exitTransition = { ExitTransition.None },
        popEnterTransition = { EnterTransition.None },
        popExitTransition = { ExitTransition.None },
    ) {
        composable(Screen.LOGIN) {
            LoginScreen(
                onLoggedIn = {
                    navController.navigate(Screen.FRIENDS) {
                        launchSingleTop = true
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }
        composable(Screen.FRIENDS) {
            FriendsScreen(
                onOpenFriend = { id -> navController.navigate(Screen.friend(id)) { launchSingleTop = true } },
                onOpenSettings = { navController.navigate(Screen.SETTINGS) { launchSingleTop = true } },
                onOpenHistory = { navController.navigate(Screen.HISTORY) { launchSingleTop = true } },
                onOpenOwnProfile = { navController.navigate(Screen.ME) { launchSingleTop = true } },
            )
        }
        composable("friend/{friendId}") { entry ->
            val friendId = entry.arguments?.getString("friendId").orEmpty()
            FriendDetailScreen(
                friendId = friendId,
                onBack = goBack,
                onOpenFriend = { id -> navController.navigate(Screen.friend(id)) { launchSingleTop = true } },
            )
        }
        composable(Screen.SETTINGS) {
            SettingsScreen(onBack = goBack)
        }
        composable(Screen.HISTORY) {
            HistoryScreen(onBack = goBack)
        }
        composable(Screen.ME) {
            OwnProfileScreen(onBack = goBack)
        }
    }
}

internal fun appFrom(context: Context): VrApp = context.applicationContext as VrApp

/** Prompts to download a newer build found on GitHub (silent or manual check). */
@Composable
private fun UpdateNoticeDialog() {
    val context = LocalContext.current
    val app = context.applicationContext as VrApp
    val info by app.updateCenter.prompt.collectAsState()
    val update = info ?: return
    AlertDialog(
        onDismissRequest = { app.updateCenter.dismissPrompt() },
        title = { Text("发现新版本 ${update.versionName}") },
        text = {
            Column {
                Text(
                    "当前版本：${app.updateCenter.currentVersionName} → 最新版本：${update.versionName}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (!update.notes.isNullOrBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        update.notes.trim().take(800),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .heightIn(max = 240.dp)
                            .verticalScroll(rememberScrollState()),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                app.updateCenter.openDownload(update)
                app.updateCenter.dismissPrompt()
            }) { Text("前往下载") }
        },
        dismissButton = {
            TextButton(onClick = { app.updateCenter.dismissPrompt() }) { Text("稍后") }
        },
    )
}

/** Shows the stack trace saved by the global crash handler, once, with a
 *  copy button so the user can send it back for debugging. */
@Composable
private fun CrashNoticeDialog() {
    val context = LocalContext.current
    val app = context.applicationContext as VrApp
    val crash = remember { app.settingsRepo.getCrashInfo() }
    var show by remember { mutableStateOf(!crash.isNullOrBlank()) }
    fun dismiss() {
        show = false
        app.settingsRepo.clearCrashInfo()
    }
    if (!show || crash.isNullOrBlank()) return
    AlertDialog(
        onDismissRequest = { dismiss() },
        title = { Text("上次启动崩溃") },
        text = {
            Column {
                Text(
                    "An unexpected error happened on the previous launch. Copy it and send it to the author.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    crash,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState()),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("crash", crash))
                Toast.makeText(context, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
            }) { Text("复制") }
        },
        dismissButton = {
            TextButton(onClick = { dismiss() }) { Text("知道了") }
        },
    )
}
