package dev.pogo.pocket.widget

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.pogo.pocket.Pogo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/** Syncs with Pogo Pad in the background and refreshes the widgets. */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val core = Pogo.core(applicationContext)
        if (core.syncEnabled()) {
            try {
                withContext(Dispatchers.IO) { core.syncNow() }
            } catch (e: Exception) {
                // The error is kept in the sync status for the app to show.
                Log.w("Pogo", "sync failed: ${e.message}")
            }
        }
        Widgets.refreshAll(applicationContext)
        return Result.success()
    }

    companion object {
        private val online = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        /** Every 15 minutes, Android's shortest period for background work. */
        fun schedulePeriodic(context: Context) {
            val req = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES).setConstraints(online).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("sync-periodic", ExistingPeriodicWorkPolicy.KEEP, req)
        }

        /** After a local edit; repeated edits push the sync back. */
        fun syncSoon(context: Context) {
            val req = OneTimeWorkRequestBuilder<SyncWorker>().setInitialDelay(2, TimeUnit.SECONDS).setConstraints(online).build()
            WorkManager.getInstance(context).enqueueUniqueWork("sync-soon", ExistingWorkPolicy.REPLACE, req)
        }

        fun syncNow(context: Context) {
            val req = OneTimeWorkRequestBuilder<SyncWorker>().setConstraints(online).build()
            WorkManager.getInstance(context).enqueueUniqueWork("sync-now", ExistingWorkPolicy.KEEP, req)
        }
    }
}
