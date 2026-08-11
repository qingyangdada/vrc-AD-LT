package com.vrc.friendtracker.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vrc.friendtracker.data.api.ApiDiagnostics
import com.vrc.friendtracker.ui.AppViewModelFactory
import com.vrc.friendtracker.ui.appFrom
import com.vrc.friendtracker.util.TimeUtil

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = appFrom(context)
    val vm: SettingsViewModel = viewModel(factory = AppViewModelFactory(app))
    val displayName by vm.displayName.collectAsState()
    val darkMode by vm.darkMode.collectAsState()
    val lastSyncAt by vm.lastSyncAt.collectAsState()
    val diagnostics by vm.diagnostics.collectAsState()
    val httpDiagnostics by ApiDiagnostics.lines.collectAsState()
    var showLogoutDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Card {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("账号", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    Text("显示名：${displayName ?: "—"}", style = MaterialTheme.typography.bodyMedium)
                    Text("登录账号：${vm.username ?: "—"}", style = MaterialTheme.typography.bodyMedium)
                }
            }

            Card {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("外观", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            "夜间模式",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Switch(
                            checked = darkMode,
                            onCheckedChange = { vm.setDarkMode(it) },
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "开启后强制使用深色主题",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
Card {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("同步状态", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (lastSyncAt > 0L) "上次同步：${TimeUtil.formatFull(lastSyncAt)}"
                        else "尚未同步",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            Card {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    val allDiag = (httpDiagnostics + diagnostics).distinct()
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            "诊断信息（登录/同步调试）",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = {
                            val text = allDiag.joinToString("\n")
                            val clipboard =
                                context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("诊断信息", text))
                            Toast.makeText(context, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
                        }) { Text("复制") }
                        TextButton(onClick = {
                            ApiDiagnostics.clear()
                            vm.clearDiagnostics()
                        }) { Text("清空") }
                    }
                    Spacer(Modifier.height(8.dp))
                    if (allDiag.isEmpty()) {
                        Text("暂无记录", style = MaterialTheme.typography.bodySmall)
                    } else {
                        Text(
                            allDiag.joinToString("\n"),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    TextButton(onClick = { vm.clearFailedImageHosts() }) {
                        Text("清除头像失败记录（重新尝试被跳过的域名）")
                    }
                }
            }

            Card {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("说明", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "本应用使用 VRChat 非官方 API，需要登录你自己的账号。\n" +
                            "· 信任等级由系统标签推断，仅供参考\n" +
                            "· 数据仅保存在本机，后台每 15 分钟自动同步一次\n" +
                            "· 作者：清阳Remmm~\n" +
                            "· 使用第三方 API 违反 VRChat 服务条款，请自用并承担风险",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            TextButton(
                onClick = { showLogoutDialog = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("退出登录", color = MaterialTheme.colorScheme.error)
            }
        }
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("退出登录") },
            text = { Text("将清除本地保存的登录令牌和同步数据，确定退出？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutDialog = false
                        vm.logout(onDone = {})
                    },
                ) {
                    Text("退出", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) { Text("取消") }
            },
        )
    }
}

