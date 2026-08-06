package com.g3ck0.seriestracker

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import coil.ImageLoader
import coil.ImageLoaderFactory
import dagger.hilt.android.HiltAndroidApp
import okhttp3.OkHttpClient
import javax.inject.Inject

/**
 * Artwork comes from the backend's `/img` route, which sits behind the same token as the
 * rest of it. Handing Coil the app's own OkHttp client is what puts `X-Backend-Token` on
 * image requests too; without it search and details work while every poster is a 403.
 */
@HiltAndroidApp
class SeriesTrackerApp : Application(), ImageLoaderFactory {

    @Inject lateinit var okHttpClient: OkHttpClient

    override fun onCreate() {
        super.onCreate()
        createEpisodesChannel()
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
