package com.vrc.friendtracker.ui.own

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import com.vrc.friendtracker.data.api.dto.UpdateProfileBody
import com.vrc.friendtracker.data.api.dto.UpdateUserBody
import com.vrc.friendtracker.data.api.dto.UserDto
import com.vrc.friendtracker.data.api.VrChatApiService
import com.vrc.friendtracker.ui.UserAvatar
import com.vrc.friendtracker.ui.appFrom
import com.vrc.friendtracker.util.Status
import com.vrc.friendtracker.util.TimeUtil
import com.vrc.friendtracker.util.TrustRank
import com.vrc.friendtracker.util.trustNameColor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private sealed interface OwnProfileState {
    data object Loading : OwnProfileState
    data class Loaded(val user: UserDto) : OwnProfileState
    data class Error(val message: String) : OwnProfileState
}

/** Status lights the logged-in user can switch to (offline is not settable). */
private val STATUS_OPTIONS = listOf(Status.ACTIVE, Status.JOIN_ME, Status.ASK_ME, Status.BUSY)

/**
 * `/auth/user` answers with a reduced copy of the current user: it has no bio
 * field at all (the bio only lives on `/profile/{id}`) and its presence can
 * stay stuck on "offline" while the game is running. The full user object and
 * the public profile are layered on top of it.
 */
private suspend fun fetchFullSelf(api: VrChatApiService): Pair<UserDto, String> {
    val auth = api.getCurrentUser()
    val full = runCatching { api.getUser(auth.id) }.getOrNull()
    val profile = runCatching { api.getProfile(auth.id, asSelf = true) }.getOrNull()
    if (full == null && profile == null) return auth to "auth/user"
    val sources = buildList {
        if (full != null) add("users/{id}")
        if (profile != null) add("profile/{id}")
    }.joinToString("+")
    val merged = auth.copy(
        bio = profile?.bio ?: auth.bio,
        bioLinks = profile?.bioLinks?.takeIf { it.isNotEmpty() } ?: auth.bioLinks,
        pronouns = profile?.pronouns ?: auth.pronouns,
        tags = full?.tags?.takeIf { it.isNotEmpty() }
            ?: profile?.trustTags?.takeIf { it.isNotEmpty() }
            ?: auth.tags,
        // Prefer whichever side reports a real (not "offline") presence.
        state = listOfNotNull(auth.state, full?.state).firstOrNull { it != Status.OFFLINE }
            ?: full?.state ?: auth.state,
        location = listOfNotNull(auth.location, full?.location)
            .firstOrNull { it.isNotBlank() && it != Status.OFFLINE }
            ?: full?.location ?: auth.location,
        status = full?.status ?: auth.status,
        statusDescription = full?.statusDescription ?: auth.statusDescription,
        lastLogin = full?.lastLogin ?: auth.lastLogin,
        lastActivity = full?.lastActivity ?: auth.lastActivity,
        lastPlatform = full?.lastPlatform ?: auth.lastPlatform,
    )
    return merged to sources
}

/** Own profile page, opened from the drawer avatar. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OwnProfileScreen(onBack: () -> Unit) {
    val app = appFrom(LocalContext.current)
    val nightMode by app.settingsRepo.darkMode.collectAsState()
    val isDark = nightMode || isSystemInDarkTheme()

    var state by remember { mutableStateOf<OwnProfileState>(OwnProfileState.Loading) }
    val scope = rememberCoroutineScope()
    val logOwnStatus: (UserDto, String) -> Unit = { u, source ->
        app.settingsRepo.logDiagnostic(
            "ME",
            "自己: state=${u.state ?: "无"}, location=${u.location ?: "无"}, status=${u.status ?: "无"}, " +
                "简介=${if (u.bio.isNullOrBlank()) "空" else "${u.bio.length}字"}, 来源=$source",
        )
    }
    val loadSelf: suspend () -> UserDto = {
        val (merged, source) = fetchFullSelf(app.api)
        logOwnStatus(merged, source)
        merged
    }
    val load: () -> Unit = {
        scope.launch {
            state = OwnProfileState.Loading
            state = try {
                OwnProfileState.Loaded(loadSelf())
            } catch (e: Exception) {
                OwnProfileState.Error("获取失败：" + (e.message ?: "网络错误"))
            }
        }
    }
    LaunchedEffect(Unit) { load() }
    // Presence changes at any moment, so keep polling while the page is open.
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            runCatching { loadSelf() }.onSuccess { me ->
                if (state is OwnProfileState.Loaded) state = OwnProfileState.Loaded(me)
            }
        }
    }

    val context = LocalContext.current

    /**
     * `PUT /users/{id}` answers with the CurrentUser object, which carries no
     * bio and routinely reports presence as offline. Assigning that response
     * straight into the screen state is what wiped the bio and flipped the page
     * to offline, so mutations only update the local copy and then re-read the
     * merged self instead of trusting the response body.
     */
    val pullSelf: suspend () -> Unit = {
        runCatching { loadSelf() }.onSuccess { me ->
            if (state is OwnProfileState.Loaded) state = OwnProfileState.Loaded(me)
        }
    }
    val setStatus: (String) -> Unit = { status ->
        scope.launch {
            val current = state as? OwnProfileState.Loaded ?: return@launch
            state = OwnProfileState.Loaded(current.user.copy(status = status))
            try {
                app.api.updateUser(current.user.id, UpdateUserBody(status = status))
                pullSelf()
            } catch (e: Exception) {
                state = OwnProfileState.Loaded(current.user)
                Toast.makeText(context, "设置状态失败：" + (e.message ?: "网络错误"), Toast.LENGTH_SHORT).show()
            }
        }
    }
    val setStatusDescription: (String) -> Unit = { desc ->
        scope.launch {
            val current = state as? OwnProfileState.Loaded ?: return@launch
            state = OwnProfileState.Loaded(current.user.copy(statusDescription = desc))
            try {
                app.api.updateUser(current.user.id, UpdateUserBody(statusDescription = desc))
                pullSelf()
            } catch (e: Exception) {
                state = OwnProfileState.Loaded(current.user)
                Toast.makeText(context, "修改状态文字失败：" + (e.message ?: "网络错误"), Toast.LENGTH_SHORT).show()
            }
        }
    }
    val setBio: (String) -> Unit = { bio ->
        scope.launch {
            val current = state as? OwnProfileState.Loaded ?: return@launch
            state = OwnProfileState.Loaded(current.user.copy(bio = bio))
            try {
                app.api.updateProfile(current.user.id, UpdateProfileBody(bio = bio))
                pullSelf()
            } catch (e: Exception) {
                state = OwnProfileState.Loaded(current.user)
                Toast.makeText(context, "修改简介失败：" + (e.message ?: "网络错误"), Toast.LENGTH_SHORT).show()
            }
        }
    }
    val setPronouns: (String) -> Unit = { pronouns ->
        scope.launch {
            val current = state as? OwnProfileState.Loaded ?: return@launch
            state = OwnProfileState.Loaded(current.user.copy(pronouns = pronouns))
            try {
                // Pronouns are written through the generic user endpoint.
                app.api.updateUser(current.user.id, UpdateUserBody(pronouns = pronouns))
                pullSelf()
            } catch (e: Exception) {
                state = OwnProfileState.Loaded(current.user)
                Toast.makeText(context, "修改人称代词失败：" + (e.message ?: "网络错误"), Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("我的资料") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = load) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新")
                    }
                },
            )
        },
    ) { padding ->
        when (val s = state) {
            OwnProfileState.Loading -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            is OwnProfileState.Error -> Column(
                Modifier.fillMaxSize().padding(padding).padding(16.dp),
            ) {
                Text(
                    s.message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = load) { Text("重试") }
            }
            is OwnProfileState.Loaded -> OwnProfileContent(
                s.user, isDark, padding,
                onSetStatus = setStatus,
                onSetDescription = setStatusDescription,
                onSetBio = setBio,
                onSetPronouns = setPronouns,
            )
        }
    }
}

@Composable
private fun OwnProfileContent(
    user: UserDto,
    isDark: Boolean,
    padding: PaddingValues,
    onSetStatus: (String) -> Unit,
    onSetDescription: (String) -> Unit,
    onSetBio: (String) -> Unit,
    onSetPronouns: (String) -> Unit,
) {
    val context = LocalContext.current
    var showDescDialog by remember { mutableStateOf(false) }
    var showBioDialog by remember { mutableStateOf(false) }
    var showPronounsDialog by remember { mutableStateOf(false) }
    val rank = remember(user.tags) { TrustRank.fromTags(user.tags) }
    val nameColor = trustNameColor(rank, isDark)
    val isOnline = Status.isOnline(user.location, user.state)
    val avatarUrl = user.profilePicOverrideThumbnail?.takeIf { it.isNotBlank() }
        ?: user.profilePicOverride?.takeIf { it.isNotBlank() }
        ?: user.currentAvatarThumbnailImageUrl?.takeIf { it.isNotBlank() }
        ?: user.currentAvatarImageUrl?.takeIf { it.isNotBlank() }
    val fallbackUrl = if (!user.profilePicOverrideThumbnail.isNullOrBlank() || !user.profilePicOverride.isNullOrBlank())
        user.currentAvatarThumbnailImageUrl?.takeIf { it.isNotBlank() }
            ?: user.currentAvatarImageUrl?.takeIf { it.isNotBlank() }
    else null

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Card(shape = RoundedCornerShape(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    UserAvatar(avatarUrl, user.displayName, Modifier.size(76.dp), fallbackUrl = fallbackUrl)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            user.displayName,
                            color = nameColor,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(Status.color(user.status, isOnline)),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                Status.label(user.status, isOnline),
                                style = MaterialTheme.typography.bodyMedium,
                                color = Status.color(user.status, isOnline),
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        val pronouns = user.pronouns?.takeIf { it.isNotBlank() }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                pronouns ?: "未设置人称代词",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (pronouns != null) MaterialTheme.colorScheme.onSurfaceVariant
                                else MaterialTheme.colorScheme.outline,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.width(6.dp))
                            EditChip("修改") { showPronounsDialog = true }
                        }
                    }
                }
            }
            if (showPronounsDialog) {
                var draft by remember(user.pronouns) { mutableStateOf(user.pronouns.orEmpty()) }
                AlertDialog(
                    onDismissRequest = { showPronounsDialog = false },
                    title = { Text("修改人称代词") },
                    text = {
                        OutlinedTextField(
                            value = draft,
                            onValueChange = { draft = it.take(32) },
                            placeholder = { Text("例如：他/她、they/them") },
                            singleLine = true,
                            supportingText = { Text("最多 32 个字符，留空即清除") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            showPronounsDialog = false
                            onSetPronouns(draft.trim())
                        }) { Text("保存") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showPronounsDialog = false }) { Text("取消") }
                    },
                )
            }
        }
        item {
            Card(shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.fillMaxWidth().padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "状态",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                        )
                        EditChip("状态文字") { showDescDialog = true }
                    }
                    Spacer(Modifier.height(14.dp))
                    StatusSlider(
                        selected = user.status,
                        onSelect = onSetStatus,
                    )
                    if (!user.statusDescription.isNullOrBlank()) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            user.statusDescription,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (showDescDialog) {
                var descDraft by remember(user.statusDescription) { mutableStateOf(user.statusDescription.orEmpty()) }
                AlertDialog(
                    onDismissRequest = { showDescDialog = false },
                    title = { Text("修改状态文字") },
                    text = {
                        OutlinedTextField(
                            value = descDraft,
                            onValueChange = { descDraft = it },
                            placeholder = { Text("例如：正在摸鱼") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            showDescDialog = false
                            onSetDescription(descDraft.trim())
                        }) { Text("保存") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDescDialog = false }) { Text("取消") }
                    },
                )
            }
        }
        item {
            Card(shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.fillMaxWidth().padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "简介",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                        )
                        EditChip("修改") { showBioDialog = true }
                    }
                    Spacer(Modifier.height(10.dp))
                    if (!user.bio.isNullOrBlank()) {
                        Text(
                            user.bio,
                            style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    } else {
                        Text(
                            "暂无简介",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                }
            }
            if (showBioDialog) {
                var bioDraft by remember(user.bio) { mutableStateOf(user.bio.orEmpty()) }
                AlertDialog(
                    onDismissRequest = { showBioDialog = false },
                    title = { Text("修改简介") },
                    text = {
                        OutlinedTextField(
                            value = bioDraft,
                            onValueChange = { bioDraft = it },
                            placeholder = { Text("介绍一下自己吧") },
                            minLines = 3,
                            maxLines = 6,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            showBioDialog = false
                            onSetBio(bioDraft.trim())
                        }) { Text("保存") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showBioDialog = false }) { Text("取消") }
                    },
                )
            }
        }
        item {
            Card(shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.fillMaxWidth().padding(18.dp)) {
                    Text(
                        "账号信息",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(10.dp))
                    InfoRow(
                        Icons.Default.Schedule,
                        "最近上线",
                        TimeUtil.formatFull(TimeUtil.parseIsoToEpoch(user.lastLogin)),
                    )
                    InfoRow(
                        Icons.Default.Bolt,
                        "最近活跃",
                        TimeUtil.formatFull(TimeUtil.parseIsoToEpoch(user.lastActivity)),
                    )
                    if (!user.lastPlatform.isNullOrBlank()) {
                        InfoRow(Icons.Default.Devices, "平台", user.lastPlatform)
                    }
                }
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        val cm = context.getSystemService(ClipboardManager::class.java)
                        cm.setPrimaryClip(ClipData.newPlainText("userId", user.id))
                        Toast.makeText(context, "用户ID已复制", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("复制ID")
                }
                Button(
                    onClick = {
                        runCatching {
                            val intent = Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("https://vrchat.com/home/user/" + user.id),
                            )
                            context.startActivity(intent)
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("VRChat主页")
                }
            }
        }
    }
}

@Composable
private fun EditChip(text: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Default.Edit,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(13.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/** One "icon · label · value" line inside the account card. */
@Composable
private fun InfoRow(icon: ImageVector, label: String, value: String?) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(72.dp),
        )
        Text(
            value ?: "—",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun StatusSlider(
    selected: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val options = STATUS_OPTIONS
    val selectedIndex = options.indexOf(selected).takeIf { it >= 0 } ?: 0
    val density = LocalDensity.current
    var trackWidthPx by remember { mutableStateOf(0f) }
    // Non-null while the user is sliding: the thumb follows the finger.
    var slideProgress by remember { mutableStateOf<Float?>(null) }

    // The account confirmed the value we slid to (or it changed elsewhere):
    // hand control back to the server value.
    LaunchedEffect(selected) { slideProgress = null }
    // If the update never lands (network error), fall back to the real value.
    LaunchedEffect(slideProgress) {
        if (slideProgress != null) {
            delay(6_000)
            slideProgress = null
        }
    }

    val progress = slideProgress ?: selectedIndex.toFloat()
    val activeIndex = progress.roundToInt().coerceIn(0, options.lastIndex)
    val thumbColor by animateColorAsState(
        targetValue = Status.color(options[activeIndex], true),
        label = "statusThumbColor",
    )
    val segmentPx = if (options.isEmpty()) 0f else trackWidthPx / options.size

    fun progressAt(x: Float): Float =
        ((x - segmentPx / 2f) / segmentPx).coerceIn(0f, options.lastIndex.toFloat())

    fun commit(index: Int) {
        val safe = index.coerceIn(0, options.lastIndex)
        slideProgress = safe.toFloat()
        if (options[safe] != selected) onSelect(options[safe])
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(42.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .padding(4.dp)
                .onSizeChanged { trackWidthPx = it.width.toFloat() }
                .pointerInput(options.size, segmentPx) {
                    if (segmentPx > 0f) {
                        detectDragGestures(
                            onDragStart = { offset -> slideProgress = progressAt(offset.x) },
                            onDrag = { change, _ ->
                                change.consume()
                                slideProgress = progressAt(change.position.x)
                            },
                            onDragEnd = {
                                commit((slideProgress ?: progress).roundToInt())
                            },
                            onDragCancel = {
                                commit((slideProgress ?: progress).roundToInt())
                            },
                        )
                    }
                }
                .pointerInput(options.size, segmentPx) {
                    if (segmentPx > 0f) {
                        detectTapGestures { offset ->
                            commit((offset.x / segmentPx).toInt())
                        }
                    }
                },
        ) {
            if (segmentPx > 0f) {
                Box(
                    Modifier
                        .offset(x = with(density) { (progress * segmentPx).toDp() })
                        .width(with(density) { segmentPx.toDp() })
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(9.dp))
                        .background(thumbColor),
                )
            }
            val onThumb = if (thumbColor.luminance() > 0.5f) Color.Black else Color.White
            Row(Modifier.fillMaxSize()) {
                options.forEachIndexed { index, option ->
                    val active = index == activeIndex
                    Text(
                        Status.label(option, true),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (active) onThumb else MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        modifier = Modifier
                            .weight(1f)
                            .align(Alignment.CenterVertically),
                    )
                }
            }
        }
    }
}
