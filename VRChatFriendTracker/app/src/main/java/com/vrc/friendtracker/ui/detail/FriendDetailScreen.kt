package com.vrc.friendtracker.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.vrc.friendtracker.data.api.dto.InstanceDto
import com.vrc.friendtracker.data.db.FriendEntity
import com.vrc.friendtracker.data.db.HistoryEventEntity
import com.vrc.friendtracker.data.db.HistoryType
import com.vrc.friendtracker.data.db.WorldEntity
import com.vrc.friendtracker.ui.AppViewModelFactory
import com.vrc.friendtracker.ui.UserAvatar
import com.vrc.friendtracker.ui.appFrom
import com.vrc.friendtracker.ui.friends.RankChip
import com.vrc.friendtracker.ui.friends.worldDisplayName
import com.vrc.friendtracker.util.InstanceUtil
import com.vrc.friendtracker.util.Status
import com.vrc.friendtracker.util.TimeUtil
import com.vrc.friendtracker.util.TrustRank

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendDetailScreen(friendId: String, onBack: () -> Unit) {
    val app = appFrom(LocalContext.current)
    val vm: FriendDetailViewModel = viewModel(
        key = "friend_$friendId",
        factory = AppViewModelFactory(app, friendId),
    )
    val friend by vm.friend.collectAsState()
    val history by vm.history.collectAsState()
    val worlds by vm.worlds.collectAsState()
    val instance by vm.instance.collectAsState()
    val refreshing by vm.refreshing.collectAsState()

    LaunchedEffect(Unit) { vm.refresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(friend?.displayName ?: "好友详情") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (refreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .size(20.dp),
                            strokeWidth = 2.dp,
                        )
                    }
                    IconButton(onClick = vm::refresh) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新")
                    }
                },
            )
        },
    ) { padding ->
        val current = friend
        if (current == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { HeaderCard(current) }
                item { BioCard(current) }
                item { RoomCard(current, worlds, instance) }
                item {
                    Text(
                        "变化记录 (${history.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                if (history.isEmpty()) {
                    item {
                        Text(
                            "暂无记录：首次同步建立基线，之后的变化会显示在这里。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    items(history, key = { it.id }) { event ->
                        HistoryRow(event, worlds)
                    }
                }
            }
        }
    }
}

@Composable
private fun HeaderCard(friend: FriendEntity) {
    Card {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            UserAvatar(
                friend.avatarUrl,
                friend.displayName,
                Modifier.size(64.dp),
                fallbackUrl = friend.avatarFallbackUrl,
            )
            Spacer(Modifier.width(16.dp))
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        friend.displayName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.width(8.dp))
                    RankChip(friend)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    Status.label(friend.status, friend.isOnline),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Status.color(friend.status, friend.isOnline),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    detailMeta(friend),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun detailMeta(friend: FriendEntity): String {
    val lastLogin = friend.lastLogin?.let(TimeUtil::parseIsoToEpoch)
    val lastActivity = friend.lastActivity?.let(TimeUtil::parseIsoToEpoch)
    val lastSeen = lastActivity ?: lastLogin
    val parts = mutableListOf<String>()
    if (lastSeen != null) parts.add("最近活跃 ${TimeUtil.formatEpoch(lastSeen)}")
    if (!friend.lastPlatform.isNullOrBlank()) parts.add("平台 ${friend.lastPlatform}")
    val flags = mutableListOf<String>()
    if (friend.isModerator) flags.add("工作人员")
    if (friend.isTroll) flags.add("疑似恶意用户")
    if (flags.isNotEmpty()) parts.add(flags.joinToString(" "))
    return parts.joinToString(" · ").ifBlank { "—" }
}

@Composable
private fun BioCard(friend: FriendEntity) {
    Card {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text("简介", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            if (friend.bio.isNullOrBlank()) {
                Text("（无简介）", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text(friend.bio, style = MaterialTheme.typography.bodyMedium)
            }
            val links = friend.bioLinksJson?.split('\u0001')?.filter { it.isNotBlank() }
            if (!links.isNullOrEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "链接：\n${links.joinToString("\n")}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun RoomCard(friend: FriendEntity, worlds: Map<String, WorldEntity>, instance: InstanceDto?) {
    Card {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text("当前房间", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            if (!friend.isOnline) {
                Text("离线", color = MaterialTheme.colorScheme.onSurfaceVariant)
                return@Column
            }
            val parts = InstanceUtil.parseLocation(friend.location)
            when (parts.kind) {
                InstanceUtil.LocationParts.Kind.ONLINE_WORLD -> {
                    val world = parts.worldId?.let { worlds[it] }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (world?.thumbnailUrl != null) {
                            AsyncImage(
                                model = world.thumbnailUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                            )
                            Spacer(Modifier.width(12.dp))
                        }
                        Column {
                            Text(
                                world?.name ?: worldDisplayName(parts.worldId, worlds),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                roomDetailLine(friend, parts, instance),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                InstanceUtil.LocationParts.Kind.PRIVATE -> Text("好友正在私密房间中")
                InstanceUtil.LocationParts.Kind.TRAVELING -> Text("好友正在传送中")
                InstanceUtil.LocationParts.Kind.LOCAL -> Text("好友在本地场景")
                else -> Text("未知状态")
            }
        }
    }
}

private fun roomDetailLine(
    friend: FriendEntity,
    parts: InstanceUtil.LocationParts,
    instance: InstanceDto?,
): String {
    val bits = mutableListOf<String>()
    bits.add(InstanceUtil.instanceTypeLabel(parts.instanceId))
    val region = instance?.region?.let(InstanceUtil::regionLabel)
    if (region != null) bits.add("区域 $region")
    val users = instance?.nUsers ?: instance?.userCount
    if (users != null) bits.add("$users 人")
    if (!friend.instanceId.isNullOrBlank()) bits.add("实例 ${friend.instanceId.take(24)}")
    return bits.joinToString(" · ")
}

@Composable
private fun HistoryRow(event: HistoryEventEntity, worlds: Map<String, WorldEntity>) {
    val (label, color) = when (event.type) {
        HistoryType.BIO -> "简介" to MaterialTheme.colorScheme.tertiary
        HistoryType.RANK -> "等级" to MaterialTheme.colorScheme.primary
        HistoryType.STATUS -> "状态" to MaterialTheme.colorScheme.secondary
        HistoryType.ROOM -> "房间" to MaterialTheme.colorScheme.error
        else -> "记录" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Card {
        Row(Modifier.fillMaxWidth().padding(12.dp)) {
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = color.copy(alpha = 0.15f),
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = color,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    changeText(event, worlds),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    TimeUtil.formatFull(event.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun changeText(event: HistoryEventEntity, worlds: Map<String, WorldEntity>): String {
    val old = displayValue(event.type, event.oldValue, worlds)
    val new = displayValue(event.type, event.newValue, worlds)
    if (event.type == HistoryType.STATUS) return "$old → $new"
    if (event.type == HistoryType.ROOM) {
        return if (new.isBlank()) "离开了房间（$old）" else "$old → $new"
    }
    return if (old.isBlank()) "设置为：$new" else "$old → $new"
}

private fun displayValue(type: String, value: String?, worlds: Map<String, WorldEntity>): String {
    if (value.isNullOrBlank()) return ""
    return when (type) {
        HistoryType.RANK -> runCatching { TrustRank.valueOf(value) }.getOrNull()?.en ?: value
        HistoryType.ROOM -> worldDisplayName(value, worlds)
        else -> value
    }
}