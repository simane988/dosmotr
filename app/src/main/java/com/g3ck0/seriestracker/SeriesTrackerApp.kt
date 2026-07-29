package com.g3ck0.seriestracker

import android.app.Application
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

    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .okHttpClient { okHttpClient }
            .crossfade(true)
            .build()
}
