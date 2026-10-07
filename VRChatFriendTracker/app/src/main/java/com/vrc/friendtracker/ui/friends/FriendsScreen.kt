package com.vrc.friendtracker.ui.friends

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.vrc.friendtracker.data.api.dto.AvatarDto
import com.vrc.friendtracker.data.api.dto.PrintDto
import com.vrc.friendtracker.data.api.dto.WorldDto
import com.vrc.friendtracker.data.db.FriendEntity
import com.vrc.friendtracker.data.db.WorldEntity
import com.vrc.friendtracker.ui.AppViewModelFactory
import com.vrc.friendtracker.ui.UserAvatar
import com.vrc.friendtracker.ui.appFrom
import com.vrc.friendtracker.VrApp
import com.vrc.friendtracker.util.InstanceUtil
import com.vrc.friendtracker.util.Status
import com.vrc.friendtracker.util.TimeUtil
import com.vrc.friendtracker.util.TrustRank
import com.vrc.friendtracker.util.trustNameColor
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Main screen tabs reachable from the left drawer. */
private enum class MainTab(val title: String) {
    FRIENDS("VRC好友状态"),
    FAVORITES("收藏"),
    AVATARS("个人模型"),
    PHOTOS("拍立得"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendsScreen(
    onOpenFriend: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenOwnProfile: () -> Unit,
) {
    val app = appFrom(LocalContext.current)
    val vm: FriendsViewModel = viewModel(factory = AppViewModelFactory(app))
    val friends by vm.friends.collectAsState()
    val worlds by vm.worlds.collectAsState()
    val recentChanges by vm.recentChanges.collectAsState()
    val ownDisplayName by vm.ownDisplayName.collectAsState()
    val nightMode by app.settingsRepo.darkMode.collectAsState()
    val isDark = nightMode || isSystemInDarkTheme()

    val ownAvatarUrl by vm.ownAvatarUrl.collectAsState()
    val ownAvatars by vm.ownAvatars.collectAsState()
    val worldFavorites by vm.worldFavorites.collectAsState()
    val prints by vm.prints.collectAsState()
    val uploading by vm.uploading.collectAsState()

    var tabName by rememberSaveable { mutableStateOf(MainTab.FRIENDS.name) }
    val mainTab = remember(tabName) { MainTab.valueOf(tabName) }

    // Left half-screen drawer: the main content slides right together with it.
    val drawerWidthDp = LocalConfiguration.current.screenWidthDp.dp / 2
    val density = LocalDensity.current
    // Never zero: a zero-width panel would slide nothing while the content
    // still moves, which the user sees as a blank screen.
    val drawerWidthPx = with(density) { drawerWidthDp.toPx() }.coerceAtLeast(1f)
    val drawerOffset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    // The drawer has a single source of truth: this flag. The offset is always
    // animated towards it by the effect below, so a cancelled or restarted
    // animation can no longer park the panel off-screen (the old code animated
    // the offset from several call sites and an interrupted animation could
    // leave the page blank).
    var drawerOpen by remember { mutableStateOf(false) }
    // Bumped on every drag release so the settle animation runs even when the
    // resulting open/closed value did not change.
    var drawerSettle by remember { mutableIntStateOf(0) }
    // Opening the page always starts from a fully closed drawer, so no stale
    // offset from a previous visit can leave the content shifted (or blank).
    LaunchedEffect(Unit) {
        drawerOpen = false
        drawerOffset.snapTo(0f)
        // Breadcrumb: tells us (from a copied log) whether this page was
        // really recomposed when the user came back from another screen.
        app.settingsRepo.logDiagnostic(
            "UI",
            "好友页: 进入 (标签=$tabName, 宽=${drawerWidthPx.roundToInt()}px)",
        )
    }
    LaunchedEffect(drawerOpen, drawerSettle, drawerWidthPx) {
        drawerOffset.animateTo(if (drawerOpen) drawerWidthPx else 0f, tween(180))
    }
    // Runs after the last drag snap (same scope, so it observes the final
    // offset) and snaps the drawer to the nearer edge.
    val settleDrawer: () -> Unit = {
        scope.launch {
            drawerOpen = drawerOffset.value > drawerWidthPx / 2f
            drawerSettle++
        }
    }
    // The friend card whose pin action strip is currently revealed; used to
    // keep right-swipes on it from opening the drawer until it is dismissed.
    var revealedFriendId by remember { mutableStateOf<String?>(null) }
    val closeDrawer: () -> Unit = {
        revealedFriendId = null
        drawerOpen = false
        drawerSettle++
    }
    val selectTab: (MainTab) -> Unit = { target ->
        revealedFriendId = null
        tabName = target.name
        closeDrawer()
    }
    LaunchedEffect(mainTab) {
        if (mainTab == MainTab.AVATARS) vm.loadOwnAvatars()
        if (mainTab == MainTab.FAVORITES) vm.loadWorldFavorites()
        if (mainTab == MainTab.PHOTOS) vm.loadPrints()
    }
    // Print tapped in the album grid; shown as a full-screen overlay (not a
    // Dialog: dialog windows size to their content and clip the photo and
    // bottom action bar on many devices).
    var printViewerPrint by remember { mutableStateOf<PrintDto?>(null) }
    BackHandler(enabled = drawerOpen) { closeDrawer() }
    BackHandler(enabled = printViewerPrint != null) { printViewerPrint = null }

    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    // Upload: pick an image, read its bytes off the main thread, then upload.
    val uploadLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            scope.launch {
                val bytes = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    }.getOrNull()
                }
                val mime = runCatching { context.contentResolver.getType(uri) }.getOrNull()
                if (bytes == null || bytes.isEmpty()) {
                    Toast.makeText(context, "读取图片失败", Toast.LENGTH_SHORT).show()
                } else {
                    vm.uploadPrint(bytes, mime ?: "image/png")
                }
            }
        }
    }
    var pendingDownload by remember { mutableStateOf<PrintDto?>(null) }
    val downloadLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("image/png"),
    ) { uri ->
        val print = pendingDownload
        pendingDownload = null
        if (uri != null && print != null) vm.downloadPrint(print, uri)
    }
    // Upload/download/delete/rename result toasts.
    LaunchedEffect(vm) {
        vm.printMessages.collect { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }

    // Friend-list search (local filter, matches display name).
    var query by rememberSaveable { mutableStateOf("") }
    val queryTrimmed = query.trim()
    val filteredFriends = remember(friends, queryTrimmed) {
        if (queryTrimmed.isEmpty()) friends
        else friends.filter { f ->
            f.displayName.contains(queryTrimmed, ignoreCase = true) ||
                f.note?.contains(queryTrimmed, ignoreCase = true) == true
        }
    }

    // Online friends render as clusters; offline friends are collapsed into a
    // toggleable section at the bottom (pinned offline friends stay visible so
    // the pin feature keeps working as expected).
    val onlineFriends = remember(filteredFriends) {
        filteredFriends.filter { it.isOnline || it.pinned }
    }
    val offlineFriends = remember(filteredFriends) {
        filteredFriends.filter { !it.isOnline && !it.pinned }
    }
    var offlineExpanded by rememberSaveable { mutableStateOf(false) }

    // Room group sizes (2+ members get the left color bar and extra spacing).
    val groupSizes = remember(onlineFriends) {
        onlineFriends.groupingBy { groupKeyOf(it) }.eachCount()
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

    Box(Modifier.fillMaxSize()) {
        // Left half-screen drawer; slides in together with the main content.
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(drawerWidthDp)
                // Clamped: the offset can never overshoot the panel width, so
                // the panel and the content always cover the whole window.
                .offset {
                    IntOffset(
                        drawerOffset.value.coerceIn(0f, drawerWidthPx).roundToInt() -
                            drawerWidthPx.roundToInt(),
                        0,
                    )
                }
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .pointerInput(drawerWidthPx) {
                    detectHorizontalDragGestures(
                        onDragEnd = { settleDrawer() },
                        onDragCancel = { settleDrawer() },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            val next = (drawerOffset.value + dragAmount).coerceIn(0f, drawerWidthPx)
                            scope.launch { drawerOffset.snapTo(next) }
                        },
                    )
                },
        ) {
            DrawerPanel(
                ownAvatarUrl = ownAvatarUrl,
                ownDisplayName = ownDisplayName,
                selected = mainTab,
                onSelect = selectTab,
                onOpenOwnProfile = { closeDrawer(); onOpenOwnProfile() },
                isDark = isDark,
            )
        }
        // Main content: follows the drawer and opens it on right-swipes.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(drawerOffset.value.coerceIn(0f, drawerWidthPx).roundToInt(), 0) }
                // Opaque themed background: the (white) activity window
                // background can then never show through the content area.
                .background(MaterialTheme.colorScheme.background)
                .pointerInput(drawerWidthPx) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var totalX = 0f
                        var totalY = 0f
                        var dragging = false
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (change.changedToUpIgnoreConsumed()) break
                            val delta = change.positionChange()
                            totalX += delta.x
                            totalY += delta.y
                            if (!dragging) {
                                val slop = viewConfiguration.touchSlop
                                if (abs(totalX) <= slop && abs(totalY) <= slop) continue
                                // Vertical drags belong to the list's own scroller.
                                if (abs(totalX) <= abs(totalY)) break
                                // Leftward drags on a closed drawer must reach the card's
                                // SwipeReveal so the pin gesture keeps working.
                                if (drawerOffset.value <= 0f && totalX < 0f) break
                                // A revealed pin action owns rightward drags so it can be
                                // dismissed without accidentally opening the drawer.
                                if (drawerOffset.value <= 0f && revealedFriendId != null) break
                                dragging = true
                            }
                            if (drawerOffset.value <= 0f && delta.x < 0f && totalX < 0f) {
                                dragging = false
                                break
                            }
                            change.consume()
                            val next = (drawerOffset.value + delta.x).coerceIn(0f, drawerWidthPx)
                            scope.launch { drawerOffset.snapTo(next) }
                        }
                        if (dragging) {
                            settleDrawer()
                        }
                    }
                },
        ) {
            Scaffold(
                snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = {
                        app.settingsRepo.logDiagnostic(
                            "UI",
                            "菜单: 打开 (宽=${drawerWidthPx.roundToInt()}px, 偏移=${drawerOffset.value.roundToInt()}px)",
                        )
                        drawerOpen = true
                        drawerSettle++
                    }) {
                        Icon(Icons.Default.Menu, contentDescription = "打开菜单")
                    }
                },
                title = {
                    Column {
                        Text(mainTab.title, fontWeight = FontWeight.SemiBold)
                        if (mainTab == MainTab.FRIENDS) {
                            Text(
                                text = listOfNotNull(
                                    ownDisplayName,
                                    "在线 ${friends.count { it.isOnline }}/${friends.size}",
                                ).joinToString(" · "),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                actions = {
                    if (mainTab == MainTab.PHOTOS) {
                        IconButton(
                            onClick = { uploadLauncher.launch("image/*") },
                            enabled = !uploading,
                        ) {
                            Icon(Icons.Default.FileUpload, contentDescription = "上传照片")
                        }
                    }
                    SyncSpinner(vm.showSpinner)
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
        when (mainTab) {
            MainTab.FRIENDS -> Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            MonitorStatusBar(app)
            SyncErrorBanner(vm.lastSyncError)
            if (friends.isEmpty()) {
                EmptyFriends(vm)
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
                    val listState = rememberLazyListState()
                    // Scrolling dismisses any open pin action so right-swipes
                    // go back to opening the drawer immediately.
                    LaunchedEffect(listState) {
                        snapshotFlow { listState.isScrollInProgress }
                            .collect { scrolling -> if (scrolling) revealedFriendId = null }
                    }
                    LazyColumn(
                        state = listState,
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
                        // Searching shows every match (offline included); the
                        // normal view renders online friends and hides offline
                        // ones behind the collapsible section at the bottom.
                        val visibleFriends = if (queryTrimmed.isNotEmpty()) filteredFriends else onlineFriends
                        friendItems(
                            friends = visibleFriends,
                            groupSizes = groupSizes,
                            worlds = worlds,
                            isDark = isDark,
                            revealedFriendId = revealedFriendId,
                            onRevealChanged = { id, revealed ->
                                if (revealed) revealedFriendId = id
                                else if (revealedFriendId == id) revealedFriendId = null
                            },
                            onOpenFriend = onOpenFriend,
                            onSetPinned = { id, pinned -> vm.setPinned(id, pinned) },
                        )
                        if (query.isBlank() && offlineFriends.isNotEmpty()) {
                            item(key = "offline-header") {
                                OfflineHeader(
                                    count = offlineFriends.size,
                                    expanded = offlineExpanded,
                                    onClick = { offlineExpanded = !offlineExpanded },
                                )
                            }
                            if (offlineExpanded) {
                                friendItems(
                                    friends = offlineFriends,
                                    groupSizes = groupSizes,
                                    worlds = worlds,
                                    isDark = isDark,
                                    revealedFriendId = revealedFriendId,
                                    onRevealChanged = { id, revealed ->
                                        if (revealed) revealedFriendId = id
                                        else if (revealedFriendId == id) revealedFriendId = null
                                    },
                                    onOpenFriend = onOpenFriend,
                                    onSetPinned = { id, pinned -> vm.setPinned(id, pinned) },
                                )
                            }
                        }
                        if (query.isBlank()) {
                            item { LastSyncFooter(vm.lastSyncAt) }
                        }
                    }
                }
            }
        }
            MainTab.FAVORITES -> FavoritesTab(
                padding = padding,
                state = worldFavorites,
                onRetry = { vm.loadWorldFavorites(force = true) },
            )
            MainTab.AVATARS -> AvatarsTab(
                padding = padding,
                state = ownAvatars,
                isDark = isDark,
                onRetry = { vm.loadOwnAvatars() },
                onRename = vm::renameAvatar,
            )
            MainTab.PHOTOS -> PrintsTab(
                padding = padding,
                state = prints,
                uploading = uploading,
                onRetry = { vm.loadPrints(force = true) },
                onOpenPrint = { printViewerPrint = it },
                onDelete = vm::deletePrint,
                onDownload = { print ->
                    pendingDownload = print
                    downloadLauncher.launch("print_" + print.id.take(8) + ".png")
                },
            )
        }
    }
    }
        // Full-screen print viewer overlay: drawn last so it covers the whole
        // app (including the top bar) and is never clipped by dialog sizing.
        val currentPrint = printViewerPrint
        if (currentPrint != null) {
            PrintViewer(
                print = currentPrint,
                onDismiss = { printViewerPrint = null },
                onDelete = {
                    printViewerPrint = null
                    vm.deletePrint(currentPrint.id)
                },
                onDownload = {
                    pendingDownload = currentPrint
                    downloadLauncher.launch("print_" + currentPrint.id.take(8) + ".png")
                },
            )
        }
    }
}

/** Renders friend cards with room-cluster gaps and the swipe-to-pin row. */
private fun LazyListScope.friendItems(
    friends: List<FriendEntity>,
    groupSizes: Map<String, Int>,
    worlds: Map<String, WorldEntity>,
    isDark: Boolean,
    revealedFriendId: String?,
    onRevealChanged: (String, Boolean) -> Unit,
    onOpenFriend: (String) -> Unit,
    onSetPinned: (String, Boolean) -> Unit,
) {
    var lastGroupKey: String? = null
    var lastGroupSize = 0
    friends.forEach { friend ->
        val groupKey = groupKeyOf(friend)
        val groupSize = groupSizes[groupKey] ?: 1
        // Only groups with 2+ members get the separator spacing; single
        // friends flow together without extra gaps.
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
            val worldName = friend.worldId?.let { worlds[it]?.name }
            val onAction = remember(friend) { { onSetPinned(friend.id, !friend.pinned) } }
            val onCardRevealChanged = remember(friend.id) {
                { revealed: Boolean -> onRevealChanged(friend.id, revealed) }
            }
            val actionContent = remember(friend.pinned) {
                @Composable {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.PushPin,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            if (friend.pinned) "取消置顶" else "置顶",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            }
            val cardContent = remember(friend, worldName, barColor, isDark) {
                @Composable {
                    FriendCard(
                        friend = friend,
                        worldName = worldName,
                        roomColor = barColor,
                        isDark = isDark,
                        onClick = onClick,
                    )
                }
            }
            SwipeReveal(
                actionWidth = 88.dp,
                onAction = onAction,
                onRevealChanged = onCardRevealChanged,
                actionContent = actionContent,
            ) {
                cardContent()
            }
        }
    }
}

/** Collapsible section header for the offline friends group. */
@Composable
private fun OfflineHeader(count: Int, expanded: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(
            "离线好友 ($count)",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        Icon(
            if (expanded) Icons.Default.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = if (expanded) "收起" else "展开",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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



/** Swipe-left-to-reveal row: dragging the card left exposes a right-side
 *  action strip (used for pinning). The row snaps back or springs open
 *  depending on how far it was dragged. */
@Composable
private fun SwipeReveal(
    actionWidth: Dp,
    onAction: () -> Unit,
    onRevealChanged: (Boolean) -> Unit = {},
    actionContent: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val actionPx = with(density) { actionWidth.toPx() }
    val offsetX = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    // Reveal state is reported directly from the drag/tap handlers; a single
    // list-scroll listener clears it when the card scrolls out of reach.
    Box {
        // Action strip sits behind the card, only tappable while revealed.
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(RoundedCornerShape(12.dp)),
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .width(actionWidth)
                    .fillMaxHeight()
                    // Transparent while closed: the strip sits fully behind the
                    // card, so its fill would otherwise be pure overdraw every
                    // frame for the whole list.
                    .background(
                        if (offsetX.value < 0f) MaterialTheme.colorScheme.primaryContainer
                        else Color.Transparent
                    )
                    .clickable(enabled = offsetX.value < 0f) {
                        scope.launch { offsetX.snapTo(0f) }
                        onRevealChanged(false)
                        onAction()
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (offsetX.value < 0f) {
                    actionContent()
                }
            }
        }
        // Draggable card on top of the action strip.
        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            scope.launch {
                                offsetX.animateTo(
                                    if (offsetX.value < -actionPx / 2f) -actionPx else 0f,
                                    tween(160),
                                )
                                onRevealChanged(offsetX.value < 0f)
                            }
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            val next = (offsetX.value + dragAmount).coerceIn(-actionPx, 0f)
                            scope.launch { offsetX.snapTo(next) }
                        },
                    )
                },
        ) {
            content()
        }
    }
}
    

@Composable
private fun FriendCard(
    friend: FriendEntity,
    worldName: String?,
    roomColor: Color?,
    isDark: Boolean,
    onClick: () -> Unit,
) {
    val rank = remember(friend.trustRank) {
        runCatching { TrustRank.valueOf(friend.trustRank) }.getOrDefault(TrustRank.VISITOR)
    }
    val nameColor = remember(rank, isDark) { trustNameColor(rank, isDark) }
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
                        color = nameColor,
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
                // The friend's note (备注) takes this line (with a label) and hides the bio.
                val noteText = friend.note?.trim()?.takeIf { it.isNotEmpty() }
                if (noteText != null) {
                    Text(
                        text = "备注：$noteText",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                } else if (!friend.bio.isNullOrBlank()) {
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

/** Left half-screen drawer: own avatar and name on top, then the menu items.
 *  Selecting a tab switches the main content and closes the drawer. */
@Composable
private fun DrawerPanel(
    ownAvatarUrl: String?,
    ownDisplayName: String?,
    selected: MainTab,
    onSelect: (MainTab) -> Unit,
    onOpenOwnProfile: () -> Unit,
    isDark: Boolean,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
            .padding(top = LocalConfiguration.current.screenHeightDp.dp * 0.2f),
    ) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .clickable { onOpenOwnProfile() }
                .padding(6.dp),
        ) {
            UserAvatar(ownAvatarUrl, ownDisplayName ?: "", Modifier.size(64.dp))
            Spacer(Modifier.height(10.dp))
            Text(
                ownDisplayName ?: "未登录",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (isDark) Color(0xFFF0F0F0) else MaterialTheme.colorScheme.onSurface,
            )
        }
        Spacer(Modifier.height(28.dp))
        DrawerItem(Icons.Default.People, "好友状态", selected == MainTab.FRIENDS) { onSelect(MainTab.FRIENDS) }
        DrawerItem(Icons.Default.Star, "收藏", selected == MainTab.FAVORITES) { onSelect(MainTab.FAVORITES) }
        DrawerItem(Icons.Default.Face, "个人模型", selected == MainTab.AVATARS) { onSelect(MainTab.AVATARS) }
        DrawerItem(Icons.Default.PhotoCamera, "拍立得", selected == MainTab.PHOTOS) { onSelect(MainTab.PHOTOS) }
        Spacer(Modifier.weight(1f))
        Text(
            "向左滑动或按返回键关闭",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DrawerItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(12.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** Favorited worlds (地图收藏): a category selector plus square/list views. */
@Composable
private fun FavoritesTab(
    padding: PaddingValues,
    state: FriendsViewModel.WorldFavoritesState,
    onRetry: () -> Unit,
) {
    var selected by remember { mutableStateOf<WorldDto?>(null) }
    var selectedGroupKey by rememberSaveable { mutableStateOf(ALL_FAVORITE_GROUPS) }
    var listMode by rememberSaveable { mutableStateOf(false) }
    when (state) {
        FriendsViewModel.WorldFavoritesState.Loading -> {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        is FriendsViewModel.WorldFavoritesState.Error -> {
            Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
                Text(
                    state.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                TextButton(onClick = onRetry) { Text("重试") }
            }
        }
        is FriendsViewModel.WorldFavoritesState.Loaded -> {
            if (state.groups.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text(
                        "还没有收藏的地图",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                // A stale selection (category removed) falls back to showing all.
                val visibleGroups = if (selectedGroupKey == ALL_FAVORITE_GROUPS) {
                    state.groups
                } else {
                    state.groups.filter { it.key == selectedGroupKey }.ifEmpty { state.groups }
                }
                val showHeaders = selectedGroupKey == ALL_FAVORITE_GROUPS
                Column(Modifier.fillMaxSize().padding(padding)) {
                    FavoritesToolbar(
                        groups = state.groups,
                        selectedGroupKey = selectedGroupKey,
                        onSelectGroup = { selectedGroupKey = it },
                        listMode = listMode,
                        onToggleMode = { listMode = !listMode },
                    )
                    if (listMode) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            visibleGroups.forEach { group ->
                                if (showHeaders) {
                                    item(key = "group-${group.key}", contentType = "groupHeader") {
                                        FavoriteGroupHeader(group)
                                    }
                                }
                                items(group.worlds, key = { it.id }, contentType = { "world" }) { world ->
                                    WorldFavoriteRow(world, onClick = { selected = world })
                                }
                            }
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            visibleGroups.forEach { group ->
                                if (showHeaders) {
                                    item(
                                        key = "group-${group.key}",
                                        contentType = "groupHeader",
                                        span = { GridItemSpan(maxLineSpan) },
                                    ) {
                                        FavoriteGroupHeader(group)
                                    }
                                }
                                items(group.worlds, key = { it.id }, contentType = { "world" }) { world ->
                                    WorldFavoriteCell(world, onClick = { selected = world })
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    val current = selected
    if (current != null) {
        WorldDetailSheet(world = current, onDismiss = { selected = null })
    }
}

/** Key for "show every favorite folder" in the 收藏 category selector. */
private const val ALL_FAVORITE_GROUPS = "__all__"

/** Category dropdown + 方格/列表 switch above the 收藏 content. */
@Composable
private fun FavoritesToolbar(
    groups: List<FriendsViewModel.WorldFavoriteGroup>,
    selectedGroupKey: String,
    onSelectGroup: (String) -> Unit,
    listMode: Boolean,
    onToggleMode: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val currentName = groups.firstOrNull { it.key == selectedGroupKey }?.name ?: "全部"
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            TextButton(onClick = { menuOpen = true }) {
                Text("分类：$currentName")
                Icon(
                    Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("全部（${groups.sumOf { it.worlds.size }}）") },
                    onClick = {
                        onSelectGroup(ALL_FAVORITE_GROUPS)
                        menuOpen = false
                    },
                )
                groups.forEach { group ->
                    DropdownMenuItem(
                        text = { Text("${group.name}（${group.worlds.size}）") },
                        onClick = {
                            onSelectGroup(group.key)
                            menuOpen = false
                        },
                    )
                }
            }
        }
        Spacer(Modifier.weight(1f))
        FavoritesModeButton("方格", active = !listMode) { if (listMode) onToggleMode() }
        FavoritesModeButton("列表", active = listMode) { if (!listMode) onToggleMode() }
    }
}

@Composable
private fun FavoritesModeButton(label: String, active: Boolean, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Text(
            label,
            color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

/** One world as a list row (thumbnail + name + author) for 列表 view. */
@Composable
private fun WorldFavoriteRow(world: WorldDto, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = world.thumbnailImageUrl ?: world.imageUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)),
            placeholder = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
            error = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                world.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val author = world.authorName?.takeIf { it.isNotBlank() }
            if (author != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    "作者：$author",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Folder title above each favorite category in the 收藏 grid. */
@Composable
private fun FavoriteGroupHeader(group: FriendsViewModel.WorldFavoriteGroup) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            group.name,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            "${group.worlds.size} 个",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** One square grid cell: world thumbnail with the name overlaid at the bottom. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorldFavoriteCell(world: WorldDto, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(10.dp)),
        ) {
            AsyncImage(
                model = world.thumbnailImageUrl ?: world.imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                placeholder = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
                error = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            ) {
                Text(
                    world.name,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Own avatars shown in the 个人模型 tab. */
@Composable
private fun AvatarsTab(
    padding: PaddingValues,
    state: FriendsViewModel.OwnAvatarsState,
    isDark: Boolean,
    onRetry: () -> Unit,
    onRename: (String, String) -> Unit,
) {
    var selected by remember { mutableStateOf<AvatarDto?>(null) }
    when (state) {
        FriendsViewModel.OwnAvatarsState.Loading -> {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        is FriendsViewModel.OwnAvatarsState.Error -> {
            Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
                Text(
                    state.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                TextButton(onClick = onRetry) { Text("重试") }
            }
        }
        is FriendsViewModel.OwnAvatarsState.Loaded -> {
            if (state.avatars.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text(
                        "还没有自己的模型",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(state.avatars, key = { it.id }, contentType = { "avatar" }) { avatar ->
                        AvatarRow(avatar, onClick = { selected = avatar })
                    }
                }
            }
        }
    }
    val current = selected
    if (current != null) {
        AvatarDetailSheet(
            avatar = current,
            onDismiss = { selected = null },
            onRename = { newName ->
                onRename(current.id, newName)
                selected = null
            },
        )
    }
}

@Composable
private fun AvatarRow(avatar: AvatarDto, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            UserAvatar(avatar.thumbnailImageUrl ?: avatar.imageUrl, avatar.name, Modifier.size(44.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    avatar.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    listOfNotNull(avatar.authorName, releaseStatusText(avatar.releaseStatus)).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Avatar detail bottom sheet: image, name, author, description, id, rename. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AvatarDetailSheet(
    avatar: AvatarDto,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit,
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showRename by remember { mutableStateOf(false) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
        ) {
            AsyncImage(
                model = avatar.imageUrl ?: avatar.thumbnailImageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(12.dp)),
                placeholder = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
                error = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
            )
            Spacer(Modifier.height(14.dp))
            Text(
                avatar.name,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            val meta = listOfNotNull(
                avatar.authorName,
                releaseStatusText(avatar.releaseStatus),
            ).joinToString(" · ")
            if (meta.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    meta,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!avatar.description.isNullOrBlank()) {
                Spacer(Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(Modifier.height(12.dp))
                Text(
                    "简介",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    avatar.description,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))
            Text(
                avatar.id,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = {
                    val cm = context.getSystemService(ClipboardManager::class.java)
                    cm.setPrimaryClip(ClipData.newPlainText("avatarId", avatar.id))
                    Toast.makeText(context, "模型ID已复制", Toast.LENGTH_SHORT).show()
                }) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("复制ID")
                }
                Spacer(Modifier.width(10.dp))
                Button(onClick = { showRename = true }) {
                    Icon(Icons.Default.Face, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("重命名")
                }
            }
        }
    }
    if (showRename) {
        var name by remember(avatar.id) { mutableStateOf(avatar.name) }
        AlertDialog(
            onDismissRequest = { showRename = false },
            title = { Text("修改模型名称") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showRename = false
                    onRename(name)
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showRename = false }) { Text("取消") }
            },
        )
    }
}

/** 拍立得相册 tab: two-column grid of the logged-in user's prints. */
@Composable
private fun PrintsTab(
    padding: PaddingValues,
    state: FriendsViewModel.PrintsState,
    uploading: Boolean,
    onRetry: () -> Unit,
    onOpenPrint: (PrintDto) -> Unit,
    onDelete: (String) -> Unit,
    onDownload: (PrintDto) -> Unit,
) {
    when (state) {
        FriendsViewModel.PrintsState.Loading -> {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        is FriendsViewModel.PrintsState.Error -> {
            Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
                Text(
                    state.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                TextButton(onClick = onRetry) { Text("重试") }
            }
        }
        is FriendsViewModel.PrintsState.Loaded -> {
            if (state.prints.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "还没有拍立得照片，点击右上角上传",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (uploading) {
                            Spacer(Modifier.height(12.dp))
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        }
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.prints, key = { it.id }, contentType = { "print" }) { print ->
                        PrintCell(print, onClick = { onOpenPrint(print) })
                    }
                    if (uploading) {
                        item(key = "uploading") { UploadingCell() }
                    }
                }
            }
        }
    }
}

@Composable
private fun PrintCell(print: PrintDto, onClick: () -> Unit) {
    val url = print.files?.image
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(10.dp)),
        ) {
            if (url.isNullOrBlank()) {
                Box(
                    Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "无图片",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                var failed by remember(url) { mutableStateOf(false) }
                if (failed) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { failed = false },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "加载失败，点击重试",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    AsyncImage(
                        model = url,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                        placeholder = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
                        error = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
                        onError = { failed = true },
                    )
                }
            }
        }
    }
}

@Composable
private fun UploadingCell() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(strokeWidth = 2.dp)
    }
}

/** Full-screen print viewer overlay with download and delete actions. */
@Composable
private fun PrintViewer(
    print: PrintDto,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onDownload: () -> Unit,
) {
    // A plain overlay, not a Dialog: it fills the real screen bounds so the
    // photo and the bottom action bar are never clipped by dialog sizing.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onDismiss() },
    ) {
        AsyncImage(
            model = print.files?.image,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
            placeholder = ColorPainter(Color(0xFF1A1A1A)),
            error = ColorPainter(Color(0xFF1A1A1A)),
        )
        IconButton(
            onClick = onDismiss,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(8.dp)
                .background(Color.Black.copy(alpha = 0.4f), CircleShape),
        ) {
            Icon(Icons.Default.Close, contentDescription = "关闭", tint = Color.White)
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .background(Color.Black.copy(alpha = 0.65f))
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            val meta = listOfNotNull(
                print.worldName?.takeIf { it.isNotBlank() },
                print.note?.takeIf { it.isNotBlank() },
            ).joinToString(" · ")
            if (meta.isNotBlank()) {
                Text(
                    meta,
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
            }
            val created = print.createdAt?.let(TimeUtil::parseIsoToEpoch)
            if (created != null) {
                Text(
                    TimeUtil.formatFull(created),
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.labelMedium,
                )
                Spacer(Modifier.height(10.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onDownload) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                    Spacer(Modifier.width(6.dp))
                    Text("下载", color = Color.White)
                }
                Button(
                    onClick = onDelete,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFB3261E),
                        contentColor = Color.White,
                    ),
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("删除")
                }
            }
        }
    }
}
private fun releaseStatusText(status: String?): String? = when (status) {
    "public" -> "公开"
    "private" -> "私有"
    "hidden" -> "隐藏"
    else -> null
}

@Composable
private fun SyncSpinner(flow: StateFlow<Boolean>) {
    val syncing by flow.collectAsState()
    if (syncing) {
        CircularProgressIndicator(
            modifier = Modifier
                .padding(end = 8.dp)
                .size(20.dp),
            strokeWidth = 2.dp,
        )
    }
}

/** Sync error banner: collects its own state for the same reason. */
@Composable
private fun SyncErrorBanner(flow: StateFlow<String?>) {
    val lastSyncError by flow.collectAsState()
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
}

/** Empty-state placeholder; the spinner only spins during the initial load. */
@Composable
private fun EmptyFriends(vm: FriendsViewModel) {
    val syncing by vm.syncing.collectAsState()
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
}

/** "上次同步" footer, self-collecting so it never recomposes the list. */
@Composable
private fun LastSyncFooter(flow: StateFlow<Long>) {
    val lastSyncAt by flow.collectAsState()
    if (lastSyncAt <= 0L) return
    Spacer(Modifier.height(4.dp))
    Text(
        text = "上次同步：" + TimeUtil.formatFull(lastSyncAt),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** World detail shown when a favorite cell is tapped. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorldDetailSheet(world: WorldDto, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
        ) {
            AsyncImage(
                model = world.imageUrl ?: world.thumbnailImageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(12.dp)),
                placeholder = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
                error = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
            )
            Spacer(Modifier.height(14.dp))
            Text(
                world.name,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            if (!world.authorName.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "作者：" + world.authorName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val meta = listOfNotNull(
                world.capacity?.let { "容量：$it" },
                releaseStatusText(world.releaseStatus)?.let { "状态：$it" },
            )
            if (meta.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    meta.joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!world.description.isNullOrBlank()) {
                Spacer(Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(Modifier.height(12.dp))
                Text(
                    "简介",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    world.description,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (!world.tags.isNullOrEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "标签：" + world.tags.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))
            Text(
                world.id,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = {
                    val cm = context.getSystemService(ClipboardManager::class.java)
                    cm.setPrimaryClip(ClipData.newPlainText("worldId", world.id))
                    Toast.makeText(context, "世界ID已复制", Toast.LENGTH_SHORT).show()
                }) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("复制ID")
                }
                Spacer(Modifier.width(10.dp))
                Button(onClick = {
                    runCatching {
                        val intent = Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("https://vrchat.com/home/world/" + world.id),
                        )
                        context.startActivity(intent)
                    }
                }) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("在VRChat中打开")
                }
            }
        }
    }
}


/** Slim status bar shown on the friends tab while the real-time monitor
 *  master switch is on: lets the user see at a glance whether the background
 *  service is actually running. */
@Composable
private fun MonitorStatusBar(app: VrApp) {
    val monitorEnabled by app.settingsRepo.monitorEnabled.collectAsState()
    val monitorRunning by app.monitorRunning.collectAsState()
    if (!monitorEnabled) return
    val running = monitorRunning
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (running) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.errorContainer
            )
            .padding(horizontal = 12.dp, vertical = 5.dp),
    ) {
        Icon(
            if (running) Icons.Default.CheckCircle else Icons.Default.Warning,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = if (running) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onErrorContainer,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            if (running) "实时监控运行中 · 退到后台也能提醒（勿在最近任务划掉本应用）"
            else "实时监控未运行：退到后台无法提醒，请到 设置 → 实时监控 重新开启",
            style = MaterialTheme.typography.labelSmall,
            color = if (running) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}
