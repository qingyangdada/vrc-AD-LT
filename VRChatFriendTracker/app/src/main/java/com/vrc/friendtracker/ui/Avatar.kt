package com.vrc.friendtracker.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.vrc.friendtracker.VrApp
import java.util.Collections
import java.util.HashMap

/**
 * Circular user avatar. Shows a letter placeholder while loading or when the
 * image cannot be loaded.
 *
 * The VRC+ picture (url) is tried first; on failure the model avatar
 * (fallbackUrl) is retried before falling back to the letter placeholder.
 * A URL is only treated as failed after FAIL_THRESHOLD consecutive errors, so
 * a single transient timeout does not flip every friend sharing the same
 * avatar URL to a letter.
 *
 * NOTE: blocked-host rewriting happens in the network interceptor
 * (VrApp.setupImageLoader), so the UI itself never skips a URL just because
 * the host was once unreachable - the interceptor re-routes IPFS hosts to
 * reachable gateways, and failure counts handle retry spam.
 */
private const val FAIL_THRESHOLD = 2
private val failureCounts: MutableMap<String, Int> = Collections.synchronizedMap(HashMap())
private val loggedUrls: MutableSet<String> = Collections.synchronizedSet(HashSet())

@Composable
fun UserAvatar(
    url: String?,
    name: String,
    modifier: Modifier = Modifier,
    letterSize: TextUnit = MaterialTheme.typography.titleMedium.fontSize,
    fallbackUrl: String? = null,
) {
    val app = VrApp.INSTANCE
    if (url.isNullOrBlank()) {
        LetterAvatar(name, modifier.clip(CircleShape), letterSize)
        return
    }
    val hasFallback = !fallbackUrl.isNullOrBlank() && fallbackUrl != url
    // Try the VRC+ picture first; on failure retry once with the model avatar.
    // The choice is remembered per friend so re-entering the viewport does not
    // re-try the broken VRC+ URL every time.
    var useFallback by remember(url, fallbackUrl) { mutableStateOf(false) }
    val target = if (useFallback && hasFallback) fallbackUrl else url

    var failed by remember(target) { mutableStateOf((failureCounts[target] ?: 0) >= FAIL_THRESHOLD) }
    if (failed) {
        LetterAvatar(name, modifier.clip(CircleShape), letterSize)
        return
    }
    // Painter-based loading avoids per-image subcomposition, the main
    // per-item overhead while scrolling. Letter stays underneath as the
    // placeholder (loading off the UI thread via Coil).
    val painter = rememberAsyncImagePainter(
        model = target,
        onError = { error ->
            if (!useFallback && hasFallback) {
                useFallback = true
            } else {
                val count = (failureCounts[target] ?: 0) + 1
                failureCounts[target] = count
                failed = count >= FAIL_THRESHOLD
                val failedHost = hostOf(target)
                // VRChat-owned hosts are NOT persisted as permanently failed:
                // their URLs work once the request carries the right
                // User-Agent/session cookie, and failures there are usually
                // transient. Only the in-session remember applies to them.
                if (count >= FAIL_THRESHOLD && failedHost != null && !failedHost.isVrchatOwned()) {
                    app.settingsRepo.addFailedImageHost(failedHost)
                }
                runCatching {
                    if (loggedUrls.add(target)) {
                        val detail = error.result.throwable.message?.take(120)?.replace('\n', ' ') ?: "?"
                        app.settingsRepo.logDiagnostic("IMG", "头像加载失败 ($count 次) url=$target [$detail]")
                    }
                }
            }
        },
    )
    Box(modifier.clip(CircleShape)) {
        LetterAvatar(name, Modifier.matchParentSize(), letterSize)
        Image(
            painter = painter,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize(),
        )
    }
}

private fun hostOf(url: String): String? =
    runCatching { android.net.Uri.parse(url).host }.getOrNull()

private fun String.isVrchatOwned(): Boolean =
    this == "api.vrchat.cloud" || this == "files.vrchat.cloud" ||
        endsWith(".vrchat.cloud") || endsWith(".vrchat.com")

@Composable
private fun LetterAvatar(name: String, modifier: Modifier = Modifier, letterSize: TextUnit) {
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name.firstOrNull()?.uppercase() ?: "?",
            style = MaterialTheme.typography.titleMedium.copy(fontSize = letterSize),
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            fontWeight = FontWeight.Medium,
        )
    }
}