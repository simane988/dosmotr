package com.g3ck0.seriestracker.di

import com.g3ck0.seriestracker.data.telemetry.NoopTelemetry
import com.g3ck0.seriestracker.data.telemetry.Telemetry
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * The `direct` half of the binding. Its twin lives in `app/src/store` and binds the
 * Firebase implementation; exactly one of the two is ever compiled, which is why the
 * binding cannot live in `AppModule`.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class TelemetryModule {

    @Binds
    @Singleton
    abstract fun bindTelemetry(telemetry: NoopTelemetry): Telemetry
}
