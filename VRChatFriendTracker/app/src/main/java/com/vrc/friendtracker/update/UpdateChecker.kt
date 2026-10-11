package com.vrc.friendtracker.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** A newer build published on GitHub. */
data class UpdateInfo(
    val versionName: String,
    val versionCode: Long?,
    val notes: String?,
    val downloadUrl: String,
    val pageUrl: String,
)

/** Outcome of one update check. */
sealed interface CheckResult {
    data class Newer(val info: UpdateInfo) : CheckResult
    data object UpToDate : CheckResult
    data class Error(val message: String) : CheckResult
}

/**
 * Looks for a newer build on GitHub. The primary source is a tiny update.json
 * in the repo (no API rate limit); the Releases API is the fallback. Release
 * tags are expected to be the plain version name (e.g. "1.7.7" or "v1.7.7").
 */
object UpdateChecker {

    private const val OWNER_REPO = "qingyangdada/vrc-AD-LT"
    private const val UPDATE_JSON =
        "https://raw.githubusercontent.com/$OWNER_REPO/main/update.json"
    private const val RELEASES_API =
        "https://api.github.com/repos/$OWNER_REPO/releases/latest"
    private const val FALLBACK_PAGE =
        "https://github.com/$OWNER_REPO/releases"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private class HttpResponse(val code: Int, val body: String?)

    private sealed interface Fetch {
        data class Ok(val info: UpdateInfo) : Fetch
        data object NotFound : Fetch
        data class Failed(val message: String) : Fetch
    }

    /** Checks both sources and reports whether a newer build exists. */
    suspend fun check(currentName: String, currentCode: Long): CheckResult =
        withContext(Dispatchers.IO) {
            val fromJson = fetchUpdateJson()
            if (fromJson is Fetch.Ok) return@withContext verdict(fromJson.info, currentName, currentCode)
            val fromRelease = fetchLatestRelease()
            if (fromRelease is Fetch.Ok) return@withContext verdict(fromRelease.info, currentName, currentCode)
            val error = (fromJson as? Fetch.Failed)?.message
                ?: (fromRelease as? Fetch.Failed)?.message
            if (error != null) CheckResult.Error(error) else CheckResult.UpToDate
        }

    private fun verdict(info: UpdateInfo, currentName: String, currentCode: Long): CheckResult =
        if (isNewer(info, currentName, currentCode)) CheckResult.Newer(info) else CheckResult.UpToDate

    /** update.json: { "versionName": "1.7.8", "versionCode": 72, "apkUrl": "…", "notes": "…" } */
    private fun fetchUpdateJson(): Fetch {
        val response = try {
            get(UPDATE_JSON)
        } catch (e: Exception) {
            return Fetch.Failed(e.message ?: "网络错误")
        }
        if (response.code == 404) return Fetch.NotFound
        if (response.code !in 200..299) return Fetch.Failed("HTTP ${response.code}")
        val body = response.body ?: return Fetch.NotFound
        return try {
            val json = JSONObject(body)
            val name = json.optString("versionName").trim()
            if (name.isEmpty()) {
                Fetch.NotFound
            } else {
                val code = json.optLong("versionCode", 0L)
                Fetch.Ok(
                    UpdateInfo(
                        versionName = name,
                        versionCode = code.takeIf { it > 0L },
                        notes = json.optString("notes").takeIf { it.isNotBlank() },
                        downloadUrl = json.optString("apkUrl").takeIf { it.isNotBlank() } ?: FALLBACK_PAGE,
                        pageUrl = json.optString("pageUrl").takeIf { it.isNotBlank() } ?: FALLBACK_PAGE,
                    )
                )
            }
        } catch (_: Exception) {
            Fetch.NotFound
        }
    }

    /** Releases API fallback: tag_name = version, first .apk asset = download. */
    private fun fetchLatestRelease(): Fetch {
        val response = try {
            get(RELEASES_API)
        } catch (e: Exception) {
            return Fetch.Failed(e.message ?: "网络错误")
        }
        if (response.code == 404) return Fetch.NotFound
        if (response.code !in 200..299) return Fetch.Failed("HTTP ${response.code}")
        val body = response.body ?: return Fetch.NotFound
        return try {
            val json = JSONObject(body)
            val tag = json.optString("tag_name").trim().removePrefix("v").removePrefix("V")
            if (tag.isEmpty()) {
                Fetch.NotFound
            } else {
                var apkUrl: String? = null
                val assets = json.optJSONArray("assets")
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val asset = assets.optJSONObject(i) ?: continue
                        if (asset.optString("name").endsWith(".apk", ignoreCase = true)) {
                            apkUrl = asset.optString("browser_download_url").takeIf { it.isNotBlank() }
                            break
                        }
                    }
                }
                val page = json.optString("html_url").takeIf { it.isNotBlank() } ?: FALLBACK_PAGE
                Fetch.Ok(
                    UpdateInfo(
                        versionName = tag,
                        versionCode = null,
                        notes = json.optString("body").takeIf { it.isNotBlank() },
                        downloadUrl = apkUrl ?: page,
                        pageUrl = page,
                    )
                )
            }
        } catch (_: Exception) {
            Fetch.NotFound
        }
    }

    /** versionCode wins when the remote provides one; else compare dotted numbers. */
    fun isNewer(remote: UpdateInfo, currentName: String, currentCode: Long): Boolean {
        val remoteCode = remote.versionCode
        if (remoteCode != null && currentCode > 0L) return remoteCode > currentCode
        return compareVersionNames(remote.versionName, currentName) > 0
    }

    /** Numeric comparison so 1.7.10 correctly counts as newer than 1.7.9. */
    fun compareVersionNames(a: String, b: String): Int {
        val left = a.split('.', '-', '+')
        val right = b.split('.', '-', '+')
        for (i in 0 until maxOf(left.size, right.size)) {
            val x = left.getOrNull(i)?.takeWhile { it.isDigit() }?.toIntOrNull() ?: 0
            val y = right.getOrNull(i)?.takeWhile { it.isDigit() }?.toIntOrNull() ?: 0
            if (x != y) return x - y
        }
        return 0
    }

    private fun get(url: String): HttpResponse {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "VRCFD")
            .header("Accept", "application/vnd.github+json")
            .build()
        client.newCall(request).execute().use { response ->
            return HttpResponse(response.code, response.body?.string())
        }
    }
}
