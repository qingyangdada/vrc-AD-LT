package com.vrc.friendtracker.ui.settings

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vrc.friendtracker.data.api.ApiDiagnostics
import com.vrc.friendtracker.ui.AppViewModelFactory
import com.vrc.friendtracker.ui.appFrom
import com.vrc.friendtracker.update.UpdateCenter
import com.vrc.friendtracker.util.TimeUtil

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = appFrom(context)
    val vm: SettingsViewModel = viewModel(factory = AppViewModelFactory(app))
    val displayName by vm.displayName.collectAsState()
    val darkMode by vm.darkMode.collectAsState()
    val monitorEnabled by vm.monitorEnabled.collectAsState()
    val monitorRunning by app.monitorRunning.collectAsState()
    val watchedFriendCount by vm.watchedFriendCount.collectAsState()
    val lastSyncAt by vm.lastSyncAt.collectAsState()
    val diagnostics by vm.diagnostics.collectAsState()
    val httpDiagnostics by ApiDiagnostics.lines.collectAsState()
    val updateState by app.updateCenter.state.collectAsState()
    var showLogoutDialog by remember { mutableStateOf(false) }
    var diagExpanded by remember { mutableStateOf(false) }
    var pendingMonitorOn by remember { mutableStateOf(false) }
    fun enableMonitor() {
        val started = vm.setMonitorEnabled(true)
        if (started) {
            Toast.makeText(
                context, "实时监控已开启：通知栏将常驻「实时监控运行中」", Toast.LENGTH_SHORT
            ).show()
        } else {
            Toast.makeText(
                context, "实时监控开启失败：请先在系统设置中允许本应用后台运行/自启动后重试", Toast.LENGTH_LONG
            ).show()
        }
    }
    val notifPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted && pendingMonitorOn) {
            enableMonitor()
        } else if (pendingMonitorOn) {
            Toast.makeText(context, "未授予通知权限，无法开启实时监控", Toast.LENGTH_SHORT).show()
        }
        pendingMonitorOn = false
    }
    fun toggleMonitor(on: Boolean) {
        if (!on) {
            vm.setMonitorEnabled(false)
            return
        }
        val needPermission = Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        if (needPermission) {
            pendingMonitorOn = true
            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            enableMonitor()
        }
    }
    val powerManager = remember {
        context.getSystemService(Context.POWER_SERVICE) as PowerManager
    }
    val ignoresBatteryOptimization = remember {
        powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }
    val versionName = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "?"
    }

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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "实时监控",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                "像微信一样在后台实时提醒",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = monitorEnabled,
                            onCheckedChange = { toggleMonitor(it) },
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "开启后应用常驻后台（通知栏显示「实时监控运行中」），好友上线、下线、换房间会在几秒内提醒；只提醒在好友详情页点亮过铃铛的好友。\n" +
                            "请勿在最近任务中划掉本应用；建议允许忽略电池优化。开启后息屏也会持续运行以保持实时，耗电会比平时略高。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (monitorEnabled) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            if (monitorRunning) "状态：运行中（后台可提醒）"
                            else "状态：未运行（建议关闭开关后再重新开启一次）",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (monitorRunning) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.error,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            if (watchedFriendCount > 0) "已点亮铃铛的好友：$watchedFriendCount 位（只提醒这些人）"
                            else "还没有好友点亮铃铛：进入好友详情页点右上角铃铛开启后，才会推送",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (watchedFriendCount > 0) MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.error,
                        )
                    }
                    if (monitorEnabled && !ignoresBatteryOptimization) {
                        Spacer(Modifier.height(4.dp))
                        TextButton(onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(
                                        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                        Uri.parse("package:" + context.packageName),
                                    )
                                )
                            }.onFailure {
                                Toast.makeText(context, "请在系统设置中手动允许忽略电池优化", Toast.LENGTH_SHORT).show()
                            }
                        }) {
                            Text("去关闭电池优化")
                        }
                    }
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { diagExpanded = !diagExpanded },
                    ) {
                        Text(
                            "诊断信息（登录/同步调试）",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(
                            if (diagExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (diagExpanded) "折叠日志" else "展开日志",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    } else if (diagExpanded) {
                        Text(
                            allDiag.joinToString("\n"),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Text(
                            "共 ${allDiag.size} 条记录 · 点击上方标题展开日志",
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
                    Text("版本更新", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    Text("当前版本：$versionName", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        updateStatusText(updateState, versionName),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    TextButton(
                        onClick = { app.updateCenter.checkManually() },
                        enabled = updateState !is UpdateCenter.State.Checking,
                    ) {
                        Text(if (updateState is UpdateCenter.State.Checking) "正在检查…" else "检查更新")
                    }
                }
            }

            Card {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("说明", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "当前版本：${versionName}\n" +
                            "本应用使用 VRChat 非官方 API，需要登录你自己的账号。\n" +
                            "· 作者：清阳Remmm~\n" +
                            "· 此软件免费\n" +
                            "· 联系方式：QQ 867477247\n" +
                            "· 信任等级由系统标签推断，仅供参考\n" +
                            "· 数据仅保存在本机，每 5 秒同步一次，手动刷新才获取完整资料\n" +
                            "· 使用第三方 API 违反 VRChat 服务条款，请自用并承担风险",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                runCatching {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/qingyangdada/vrc-AD-LT/"))
                                    )
                                }.onFailure {
                                    Toast.makeText(context, "无法打开浏览器", Toast.LENGTH_SHORT).show()
                                }
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "软件更新地址：https://github.com/qingyangdada/vrc-AD-LT/",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
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

/** One-line status shown under the settings "版本更新" row. */
private fun updateStatusText(state: UpdateCenter.State, currentVersion: String): String = when (state) {
    is UpdateCenter.State.Idle -> "点击下方按钮检查 GitHub 上是否有新版本"
    is UpdateCenter.State.Checking -> "正在检查…"
    is UpdateCenter.State.UpToDate -> "已是最新版本（$currentVersion）"
    is UpdateCenter.State.Available -> "发现新版本 ${state.info.versionName}，点「检查更新」可再次查看"
    is UpdateCenter.State.Failed -> "检查失败：${state.message}"
}
