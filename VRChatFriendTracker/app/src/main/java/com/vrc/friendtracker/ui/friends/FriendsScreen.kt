package com.vrc.friendtracker.ui.friends

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vrc.friendtracker.data.db.FriendEntity
import com.vrc.friendtracker.data.db.WorldEntity
import com.vrc.friendtracker.ui.AppViewModelFactory
import com.vrc.friendtracker.ui.UserAvatar
import com.vrc.friendtracker.ui.appFrom
import com.vrc.friendtracker.util.InstanceUtil
import com.vrc.friendtracker.util.Status
import com.vrc.friendtracker.util.TimeUtil
import com.vrc.friendtracker.util.TrustRank

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendsScreen(
    onOpenFriend: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenHistory: () -> Unit,
) {
    val app = appFrom(LocalContext.current)
    val vm: FriendsViewModel = viewModel(factory = AppViewModelFactory(app))
    val friends by vm.friends.collectAsState()
    val worlds by vm.worlds.collectAsState()
    val recentChanges by vm.recentChanges.collectAsState()
    val syncing by vm.syncing.collectAsState()
    val lastSyncAt by vm.lastSyncAt.collectAsState()
    val lastSyncError by vm.lastSyncError.collectAsState()
    val ownDisplayName by vm.ownDisplayName.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    // Friend-list search (local filter, matches display name).
    var query by rememberSaveable { mutableStateOf("") }
    val queryTrimmed = query.trim()
    val filteredFriends = remember(friends, queryTrimmed) {
        if (queryTrimmed.isEmpty()) friends
        else friends.filter { it.displayName.contains(queryTrimmed, ignoreCase = true) }
    }

    // Room group sizes (2+ members get the left color bar and extra spacing).
    val groupSizes = remember(filteredFriends) {
        filteredFriends.groupingBy { groupKeyOf(it) }.eachCount()
    }

    // Real-time change notices ("张三 进入 世界A").
    LaunchedEffect(vm) {
        vm.notices.collect { notice ->
            snackbarHostState.showSnackbar(
                message = "${notice.friendName} ${notice.text}",
                duration = SnackbarDuration.Short,
            )
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("VRC好友状态", fontWeight = FontWeight.SemiBold)
                        Text(
                            text = listOfNotNull(
                                ownDisplayName,
                                "在线 ${friends.count { it.isOnline }}/${friends.size}",
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    if (syncing) {
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
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "设置")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (lastSyncError != null) {
                Text(
                    text = lastSyncError.orEmpty(),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }
            if (friends.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (syncing) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(16.dp))
                            Text("正在同步好友列表…")
                        } else {
                            Text("还没有好友数据，点击右上角刷新")
                        }
                    }
                }
            } else {
                if (filteredFriends.isEmpty()) {
                    // Keep the search bar visible when there are no results so
                    // the query can still be edited/cleared.
                    Column {
                        CompactSearchBar(
                            value = query,
                            onValueChange = { query = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                            placeholder = { Text("搜索好友昵称…") },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            trailingIcon = {
                                if (query.isNotEmpty()) {
                                    IconButton(onClick = { query = "" }, modifier = Modifier.size(28.dp)) {
                                        Icon(Icons.Default.Close, contentDescription = "清除", modifier = Modifier.size(14.dp))
                                    }
                                }
                            },
                            singleLine = true,
                        )
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                "没有找到匹配的好友",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        // Search bar is part of the list so it auto-hides when
                        // the user scrolls down.
                        item {
                            CompactSearchBar(
                                value = query,
                                onValueChange = { query = it },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text("搜索好友昵称…") },
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                trailingIcon = {
                                    if (query.isNotEmpty()) {
                                        IconButton(onClick = { query = "" }, modifier = Modifier.size(28.dp)) {
                                            Icon(Icons.Default.Close, contentDescription = "清除", modifier = Modifier.size(14.dp))
                                        }
                                    }
                                },
                                singleLine = true,
                            )
                        }
                        if (query.isBlank()) {
                            item {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onOpenHistory() }
                                        .padding(top = 4.dp),
                                ) {
                                    Text(
                                        "实时动态",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Text(
                                        "查看全部 ›",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                            if (recentChanges.isEmpty()) {
                                item {
                                    Text(
                                        "等待变化…（好友上线/下线/换房间会显示在这里）",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            } else {
                                items(recentChanges.take(1), key = { it.id }) { change ->
                                    RecentChangeRow(change)
                                }
                            }
                            item {
                                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                            }
                        }
                        // Room mates are emitted as one cluster; a small spacer
                        // between clusters keeps the groups easy to tell apart.
                        // Only groups with 2+ members get the separator spacing;
                        // single friends flow together without extra gaps.
                        var lastGroupKey: String? = null
                        var lastGroupSize = 0
                        filteredFriends.forEach { friend ->
                            val groupKey = groupKeyOf(friend)
                            val groupSize = groupSizes[groupKey] ?: 1
                            if (lastGroupKey != null && groupKey != lastGroupKey &&
                                (lastGroupSize >= 2 || groupSize >= 2)
                            ) {
                                item(key = "gap:${friend.id}") {
                                    Spacer(Modifier.height(4.dp))
                                }
                            }
                            lastGroupKey = groupKey
                            lastGroupSize = groupSize
                            item(key = friend.id, contentType = "friend") {
                                // Remember the click lambda per friend so unchanged cards
                                // can be skipped entirely on list re-emissions.
                                val onClick = remember(friend.id) { { onOpenFriend(friend.id) } }
                                val barColor = remember(groupSizes, friend.id) {
                                    if ((groupSizes[groupKeyOf(friend)] ?: 0) >= 2 &&
                                        friend.isOnline && !friend.worldId.isNullOrBlank()
                                    ) roomColorFor(friend.worldId!!) else null
                                }
                                FriendCard(
                                    friend = friend,
                                    worldName = friend.worldId?.let { worlds[it]?.name },
                                    roomColor = barColor,
                                    onClick = onClick,
                                )
                            }
                        }
                        if (query.isBlank()) {
                            item {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = if (lastSyncAt > 0L) "上次同步：${TimeUtil.formatFull(lastSyncAt)}" else "",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentChangeRow(change: FriendsViewModel.RecentChange) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        UserAvatar(change.avatarUrl, change.friendName, Modifier.size(24.dp), fallbackUrl = change.avatarFallbackUrl)
        Spacer(Modifier.width(8.dp))
        Text(
            text = "${change.friendName} ${change.text}",
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = TimeUtil.relative(change.timestamp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}



@Composable
private fun FriendCard(
    friend: FriendEntity,
    worldName: String?,
    roomColor: Color?,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        // No shadow: per-card shadow rendering is a major scroll-jank source
        // on lower-end GPUs; the container color keeps cards visually distinct.
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Left strip: only rooms with 2+ members get a color bar, so a
            // lone friend in a room stays blank.
            if (roomColor != null) {
                Box(
                    modifier = Modifier
                        .padding(end = 10.dp)
                        .width(4.dp)
                        .height(32.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(roomColor),
                )
            }
            UserAvatar(
                friend.avatarUrl,
                friend.displayName,
                Modifier.size(48.dp),
                fallbackUrl = friend.avatarFallbackUrl,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        friend.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.width(6.dp))
                    RankChip(friend)
                }
                Spacer(Modifier.height(2.dp))
                val status = remember(friend, worldName) { statusLine(friend, worldName) }
                Text(
                    text = status,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (friend.isOnline) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!friend.bio.isNullOrBlank()) {
                    Text(
                        text = friend.bio,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Box(
                modifier = Modifier
                    .padding(start = 8.dp)
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(Status.color(friend.status, friend.isOnline)),
            )
        }
    }
}



@Composable
internal fun RankChip(friend: FriendEntity) {
    val rank = remember(friend.trustRank) {
        runCatching { TrustRank.valueOf(friend.trustRank) }.getOrDefault(TrustRank.VISITOR)
    }
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Text(
            text = rank.en,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
        )
    }
}

/** High-contrast palette: hues spread far apart so rooms are easy to tell
 *  apart. Avoids the VRChat status light hues (green/blue/yellow/red/grey). */
private val ROOM_PALETTE = listOf(
    Color(0xFFFB8C00), // orange
    Color(0xFFE65100), // deep orange
    Color(0xFF00ACC1), // cyan
    Color(0xFF00897B), // teal
    Color(0xFF8E24AA), // purple
    Color(0xFF7B1FA2), // deep purple
    Color(0xFFD81B60), // pink
    Color(0xFF6D4C41), // brown
)

/** Cluster key: room mates share a key; anyone not in a room is its own solo group. */
private fun groupKeyOf(friend: FriendEntity): String =
    InstanceUtil.roomGroupKey(friend.isOnline, friend.worldId, friend.instanceId)
        ?: "solo:${friend.id}"
/** Stable color per world id, so friends in the same room share a bar color. */
private fun roomColorFor(worldId: String): Color {
    val index = Math.floorMod(worldId.hashCode(), ROOM_PALETTE.size)
    return ROOM_PALETTE[index]
}

private fun statusLine(friend: FriendEntity, worldName: String?): String {
    if (!friend.isOnline) {
        val lastSeen = friend.lastActivity?.let(TimeUtil::parseIsoToEpoch)
            ?: friend.lastLogin?.let(TimeUtil::parseIsoToEpoch)
        return if (lastSeen != null) "离线 · ${TimeUtil.relative(lastSeen)}" else "离线"
    }
    val prefix = Status.label(friend.status, true)
    val parts = InstanceUtil.parseLocation(friend.location)
    return when (parts.kind) {
        InstanceUtil.LocationParts.Kind.ONLINE_WORLD ->
            "$prefix · ${worldName ?: friend.worldId ?: "未知世界"}"
        InstanceUtil.LocationParts.Kind.PRIVATE -> "$prefix · 私密房间"
        InstanceUtil.LocationParts.Kind.TRAVELING -> "$prefix · 传送中"
        InstanceUtil.LocationParts.Kind.LOCAL -> "$prefix · 本地"
        else -> prefix
    }
}

internal fun worldDisplayName(id: String?, worlds: Map<String, WorldEntity>): String =
    if (id.isNullOrBlank()) "未知世界" else worlds[id]?.name ?: id

/** Compact, rounded search field used inside the friend list. */
@Composable
private fun CompactSearchBar(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: @Composable () -> Unit = {},
    leadingIcon: @Composable () -> Unit = {},
    trailingIcon: @Composable () -> Unit = {},
    singleLine: Boolean = true,
) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = modifier
            .height(36.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(contentAlignment = Alignment.Center) { leadingIcon() }
        Spacer(Modifier.width(6.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            singleLine = singleLine,
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.onSurface,
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) placeholder()
                    inner()
                }
            },
        )
        if (value.isNotEmpty()) {
            Spacer(Modifier.width(4.dp))
            Box(contentAlignment = Alignment.Center) { trailingIcon() }
        }
    }
}