package com.vrc.friendtracker.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.remember
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
import com.vrc.friendtracker.ui.settings.SettingsScreen

object Screen {
    const val LOGIN = "login"
    const val FRIENDS = "friends"
    const val SETTINGS = "settings"
    const val HISTORY = "history"
    fun friend(id: String) = "friend/$id"
}

@Composable
fun AppRoot() {
    val context = LocalContext.current
    val app = context.applicationContext as VrApp
    val navController = rememberNavController()
    val sessionActive by app.sessionActive.collectAsState()

    // Any session loss (logout / token expiry) returns to the login screen.
    LaunchedEffect(sessionActive) {
        if (!sessionActive) {
            navController.navigate(Screen.LOGIN) {
                popUpTo(0) { inclusive = true }
            }
        }
    }

    CrashNoticeDialog()

    NavHost(
        navController = navController,
        startDestination = if (sessionActive) Screen.FRIENDS else Screen.LOGIN,
    ) {
        composable(Screen.LOGIN) {
            LoginScreen(
                onLoggedIn = {
                    navController.navigate(Screen.FRIENDS) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }
        composable(Screen.FRIENDS) {
            FriendsScreen(
                onOpenFriend = { id -> navController.navigate(Screen.friend(id)) },
                onOpenSettings = { navController.navigate(Screen.SETTINGS) },
                onOpenHistory = { navController.navigate(Screen.HISTORY) },
            )
        }
        composable("friend/{friendId}") { entry ->
            val friendId = entry.arguments?.getString("friendId").orEmpty()
            FriendDetailScreen(
                friendId = friendId,
                onBack = { navController.popBackStack() },
            )
        }
        composable(Screen.SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
        composable(Screen.HISTORY) {
            HistoryScreen(onBack = { navController.popBackStack() })
        }
    }
}

internal fun appFrom(context: Context): VrApp = context.applicationContext as VrApp

/** Shows the stack trace saved by the global crash handler, once, with a
 *  copy button so the user can send it back for debugging. */
@Composable
private fun CrashNoticeDialog() {
    val context = LocalContext.current
    val app = context.applicationContext as VrApp
    val crash = remember { app.settingsRepo.getCrashInfo() }
    if (crash.isNullOrBlank()) return
    AlertDialog(
        onDismissRequest = { app.settingsRepo.clearCrashInfo() },
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
            TextButton(onClick = { app.settingsRepo.clearCrashInfo() }) { Text("知道了") }
        },
    )
}