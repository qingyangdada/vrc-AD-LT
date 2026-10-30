package com.vrc.friendtracker.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.vrc.friendtracker.data.api.dto.WorldDto
import com.vrc.friendtracker.data.db.WorldEntity

/** Normalised world data shared by the 收藏 grid, the friend-detail room card
 *  and anything else that wants to open a world detail sheet. */
data class WorldDetailInfo(
    val id: String,
    val name: String,
    val authorName: String? = null,
    val imageUrl: String? = null,
    val thumbnailUrl: String? = null,
    val capacity: Int? = null,
    val releaseStatus: String? = null,
    val description: String? = null,
    val tags: List<String>? = null,
)

fun WorldDto.toDetailInfo(): WorldDetailInfo = WorldDetailInfo(
    id = id,
    name = name.ifBlank { id },
    authorName = authorName,
    imageUrl = imageUrl,
    thumbnailUrl = thumbnailImageUrl,
    capacity = capacity,
    releaseStatus = releaseStatus,
    description = description,
    tags = tags,
)

fun WorldEntity.toDetailInfo(): WorldDetailInfo = WorldDetailInfo(
    id = id,
    name = name.ifBlank { id },
    authorName = authorName,
    imageUrl = imageUrl,
    thumbnailUrl = thumbnailUrl,
    capacity = capacity,
    releaseStatus = releaseStatus,
    description = description,
    tags = tags?.split('\u0001')?.filter { it.isNotBlank() },
)

private fun releaseStatusLabel(status: String?): String? = when (status) {
    "public" -> "公开"
    "private" -> "私有"
    "hidden" -> "隐藏"
    else -> null
}

/** Copies the map name; used by the sheet title and the room card. */
fun copyWorldName(context: android.content.Context, name: String) {
    if (name.isBlank()) return
    val cm = context.getSystemService(ClipboardManager::class.java) ?: return
    cm.setPrimaryClip(ClipData.newPlainText("worldName", name))
    Toast.makeText(context, "已复制地图名称", Toast.LENGTH_SHORT).show()
}

/** Detailed world info. Tap 复制ID / 在VRChat中打开 at the bottom; long-press the
 *  title to copy the world name. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun WorldDetailSheet(world: WorldDetailInfo, onDismiss: () -> Unit) {
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
                model = world.imageUrl ?: world.thumbnailUrl,
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
            Box(
                Modifier.combinedClickable(
                    onClick = { copyWorldName(context, world.name) },
                    onLongClick = { copyWorldName(context, world.name) },
                )
            ) {
                Text(
                    world.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                "长按地图名称可复制",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                releaseStatusLabel(world.releaseStatus)?.let { "状态：$it" },
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
                Text(world.description, style = MaterialTheme.typography.bodyMedium)
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
            Row {
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
                        val intent = android.content.Intent(
                            android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse("https://vrchat.com/home/world/" + world.id),
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
