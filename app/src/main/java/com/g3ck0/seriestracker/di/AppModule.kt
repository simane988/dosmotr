package com.g3ck0.seriestracker.di

import android.content.Context
import androidx.room.Room
import com.g3ck0.seriestracker.BuildConfig
import com.g3ck0.seriestracker.data.backup.AndroidBackupStorage
import com.g3ck0.seriestracker.data.backup.AutoBackupScheduler
import com.g3ck0.seriestracker.data.backup.AutoBackupStorage
import com.g3ck0.seriestracker.data.backup.BackupRepository
import com.g3ck0.seriestracker.data.backup.BackupSource
import com.g3ck0.seriestracker.data.backup.WorkManagerAutoBackupScheduler
import com.g3ck0.seriestracker.data.local.AppDatabase
import com.g3ck0.seriestracker.data.local.TrackerDao
import com.g3ck0.seriestracker.data.remote.CatalogApi
import com.g3ck0.seriestracker.data.settings.DataStoreSettingsStore
import com.g3ck0.seriestracker.data.settings.SettingsStore
import com.g3ck0.seriestracker.data.settings.settingsDataStore
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

    /**
     * Deliberately without `fallbackToDestructiveMigration()`: that recreates the database
     * on any schema change, which for a shipped build means deleting the library of
     * everyone who updates. Every version bump writes a real migration instead.
     */
    // Room's addMigrations is vararg, so the spread is the only way to pass the array; it
    // copies a handful of entries once per process.
    @Suppress("SpreadOperator")
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME)
            .addMigrations(*AppDatabase.MIGRATIONS)
            .build()

    @Provides
    fun provideDao(db: AppDatabase): TrackerDao = db.trackerDao()

    /**
     * The DataStore itself is the delegate's, one per process and per file; this only
     * wraps it in the interface the rest of the app talks to.
     */
    @Provides
    @Singleton
    fun provideSettingsStore(@ApplicationContext context: Context): SettingsStore =
        DataStoreSettingsStore(context.settingsDataStore)

    /**
     * The automatic backup talks to three interfaces rather than to their implementations,
     * so its rules can be driven by fakes on the JVM — the storage and the scheduler are
     * where Android lives, and the repository needs a database.
     */
    @Provides
    @Singleton
    fun provideBackupSource(repository: BackupRepository): BackupSource = repository

    @Provides
    @Singleton
    fun provideAutoBackupStorage(storage: AndroidBackupStorage): AutoBackupStorage = storage

    @Provides
    @Singleton
    fun provideAutoBackupScheduler(
        scheduler: WorkManagerAutoBackupScheduler,
    ): AutoBackupScheduler = scheduler

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
