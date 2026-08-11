package com.vrc.friendtracker

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.work.Constraints
import coil.Coil
import coil.ImageLoader
import coil.disk.DiskCache
import okhttp3.OkHttpClient
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.vrc.friendtracker.data.api.Network
import com.vrc.friendtracker.data.api.VrChatApiService
import com.vrc.friendtracker.data.db.AppDatabase
import com.vrc.friendtracker.data.repo.AuthRepository
import com.vrc.friendtracker.data.repo.SettingsRepo
import com.vrc.friendtracker.data.security.TokenStore
import com.vrc.friendtracker.sync.SyncManager
import com.vrc.friendtracker.sync.SyncWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.TimeUnit

class VrApp : Application() {

    lateinit var tokenStore: TokenStore
        private set
    lateinit var settingsRepo: SettingsRepo
        private set
    lateinit var api: VrChatApiService
        private set
    lateinit var database: AppDatabase
        private set
    lateinit var authRepository: AuthRepository
        private set
    lateinit var syncManager: SyncManager
        private set

    private val _sessionActive = MutableStateFlow(false)
    val sessionActive: StateFlow<Boolean> = _sessionActive.asStateFlow()

    override fun onCreate() {
        super.onCreate()
        INSTANCE = this
        installCrashHandler()
        tokenStore = TokenStore(this)
        settingsRepo = SettingsRepo(this)
        api = Network.createApi(tokenStore)
        database = AppDatabase.get(this)
        authRepository = AuthRepository(api, tokenStore, settingsRepo)
        syncManager = SyncManager(
            api,
            database.friendDao(),
            database.historyDao(),
            database.worldDao(),
            settingsRepo,
            tokenStore,
        )
        setupImageLoader()
        _sessionActive.value = tokenStore.authToken != null
        if (_sessionActive.value) schedulePeriodicSync()
    }

    /**
     * Configured once so avatar images reuse a disk cache and work around
     * blocked IPFS gateway hosts (e.g. cloudflare-ipfs.com is SNI-blocked on
     * some networks; dweb.link serves the same IPFS content and is reachable).
     */
    private fun setupImageLoader() {
        val client = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .addNetworkInterceptor { chain ->
                // Network interceptors run for every request incl. redirects.
                val originalUrl = chain.request().url
                val authCookie = tokenStore.authToken ?: tokenStore.pendingAuthToken
                val host = originalUrl.host
                val base = chain.request().newBuilder()
                    // The file CDN (files.vrchat.cloud) rejects non-browser
                    // User-Agents with HTTP 403, so image requests use a
                    // normal Android Chrome UA instead of the API UA.
                    .header("User-Agent", IMAGE_USER_AGENT)
                    .apply {
                        // VRChat-owned hosts (api.vrchat.cloud and
                        // files.vrchat.cloud) need the session cookie for
                        // private media; never send it to redirect targets
                        // or third-party IPFS gateways.
                        if (authCookie != null && host.endsWith(".vrchat.cloud")) {
                            header("Cookie", "auth=$authCookie")
                        }
                    }
                    .build()
                val blockedHosts = BLOCKED_IMAGE_HOSTS + settingsRepo.failedImageHosts.value
                // Only IPFS paths can be mirrored by the fallback gateways.
                if (originalUrl.host in blockedHosts && originalUrl.encodedPath.startsWith("/ipfs/")) {
                    val primary = base.newBuilder().url(originalUrl.newBuilder().host("dweb.link").build()).build()
                    try {
                        chain.proceed(primary)
                    } catch (e: java.io.IOException) {
                        val backup = base.newBuilder().url(originalUrl.newBuilder().host("nftstorage.link").build()).build()
                        chain.proceed(backup)
                    }
                } else {
                    chain.proceed(base)
                }
            }
            .build()
        val loader = ImageLoader.Builder(this)
            .okHttpClient(client)
            .crossfade(false)
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(128L * 1024 * 1024)
                    .build()
            }
            .build()
        Coil.setImageLoader(loader)
    }

    /**
     * Saves the crash stack trace to SharedPreferences so the next launch can
     * show it, then delegates to the previous handler (normal crash behavior).
     */
    private fun installCrashHandler() {
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                val trace = Log.getStackTraceString(throwable)
                getSharedPreferences("vrc_settings", Context.MODE_PRIVATE)
                    .edit().putString(KEY_CRASH_INFO, trace.take(4000)).commit()
            }
            prev?.uncaughtException(thread, throwable) ?: Runtime.getRuntime().halt(1)
        }
    }
    fun onLoginSuccess() {
        _sessionActive.value = true
        schedulePeriodicSync()
    }

    fun onLogout() {
        _sessionActive.value = false
        cancelPeriodicSync()
    }

    fun schedulePeriodicSync() {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(this)
            .enqueueUniquePeriodicWork(SYNC_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    fun cancelPeriodicSync() {
        WorkManager.getInstance(this).cancelUniqueWork(SYNC_WORK_NAME)
    }

    companion object {
        const val SYNC_WORK_NAME = "vrc_periodic_sync"
        const val KEY_CRASH_INFO = "crash_info"

        /** Browser-like UA for image/CDN requests (some VRChat CDN rules 403 non-browser UAs). */
        private const val IMAGE_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Mobile Safari/537.36"

        /** IPFS gateway hosts that are DNS-poisoned / SNI-blocked on some networks. */
        private val BLOCKED_IMAGE_HOSTS = setOf(
            "cloudflare-ipfs.com", "cf-ipfs.com", "ipfs.io", "gateway.ipfs.io",
        )

        @Volatile
        lateinit var INSTANCE: VrApp
            private set
    }
}