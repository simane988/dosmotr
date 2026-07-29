package com.g3ck0.seriestracker.di

import android.content.Context
import androidx.room.Room
import com.g3ck0.seriestracker.BuildConfig
import com.g3ck0.seriestracker.data.local.AppDatabase
import com.g3ck0.seriestracker.data.local.TrackerDao
import com.g3ck0.seriestracker.data.remote.CatalogApi
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME)
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideDao(db: AppDatabase): TrackerDao = db.trackerDao()

    /**
     * Blank when the build was made without a backend configured: no search, no refresh,
     * manual entry only. That is what the search screen's empty state checks.
     */
    @Provides
    @BackendToken
    fun provideBackendToken(): String = BuildConfig.BACKEND_TOKEN

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
    }

    @Provides
    @Singleton
    fun provideOkHttp(): OkHttpClient {
        // The only credential the app carries. Whatever catalogue the backend reads from,
        // and whatever key that needs, stays on the backend.
        val auth = okhttp3.Interceptor { chain ->
            val request = chain.request().newBuilder()
                .header("X-Backend-Token", BuildConfig.BACKEND_TOKEN)
                .build()
            chain.proceed(request)
        }
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC
            else HttpLoggingInterceptor.Level.NONE
        }
        return OkHttpClient.Builder()
            .addInterceptor(auth)
            .addInterceptor(logging)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideCatalogApi(client: OkHttpClient, json: Json): CatalogApi =
        Retrofit.Builder()
            .baseUrl(BuildConfig.BACKEND_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(CatalogApi::class.java)
}
