package com.g3ck0.seriestracker

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.g3ck0.seriestracker.data.backup.AutoBackupManager
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import javax.inject.Inject

/**
 * Artwork comes from the backend's `/img` route, which sits behind the same token as the
 * rest of it. Handing Coil the app's own OkHttp client is what puts `X-Backend-Token` on
 * image requests too; without it search and details work while every poster is a 403.
 *
 * It is also where WorkManager is configured. The default initializer is switched off in
 * the manifest, so WorkManager starts on demand with the factory below — which is what
 * lets [com.g3ck0.seriestracker.data.backup.BackupWorker] be injected at all.
 */
@HiltAndroidApp
class SeriesTrackerApp : Application(), ImageLoaderFactory, Configuration.Provider {

    @Inject lateinit var okHttpClient: OkHttpClient

    @Inject lateinit var workerFactory: HiltWorkerFactory

    @Inject lateinit var autoBackup: AutoBackupManager

    /** Outlives every screen, like the application itself; nothing here is cancelled. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        createEpisodesChannel()
        // The schedule is re-applied on every start rather than once: work enqueued by an
        // earlier install does not survive "clear data", and reading the setting is a
        // file read, so it cannot happen on the main thread here.
        scope.launch { autoBackup.syncSchedule() }
    }

    /**
     * Channels exist from API 26, which is this app's `minSdk`, so there is nothing to
     * guard here — and a notification posted without its channel is dropped silently.
     * Creating one that already exists is a no-op, so doing it on every start is the
     * documented way to keep it in place.
     */
    private fun createEpisodesChannel() {
        val channel = NotificationChannel(
            EPISODES_CHANNEL_ID,
            "Новые серии",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Сообщения о вышедших сериях отслеживаемых сериалов"
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .okHttpClient { okHttpClient }
            .crossfade(true)
            .build()

    companion object {
        /**
         * The one channel the app has. The worker that posts "new episode" notifications
         * will address it by this id, so it is a constant rather than a literal.
         */
        const val EPISODES_CHANNEL_ID = "episodes"
    }
}
