package com.vrc.friendtracker.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/** Periodic background sync so history stays fresh even when the app is closed. */
class SyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = com.vrc.friendtracker.VrApp.INSTANCE
        return when (app.syncManager.syncOnce()) {
            is SyncOutcome.Done -> Result.success()
            SyncOutcome.AuthExpired -> Result.success()
            is SyncOutcome.Failed -> Result.retry()
        }
    }
}