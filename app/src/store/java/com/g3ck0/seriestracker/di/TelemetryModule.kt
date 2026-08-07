package com.g3ck0.seriestracker.di

import com.g3ck0.seriestracker.data.telemetry.FirebaseTelemetry
import com.g3ck0.seriestracker.data.telemetry.Telemetry
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * The `store` half of the binding; its twin in `app/src/direct` binds the no-op. Exactly
 * one of the two is compiled, which is why the binding cannot live in `AppModule`.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class TelemetryModule {

    @Binds
    @Singleton
    abstract fun bindTelemetry(telemetry: FirebaseTelemetry): Telemetry
}
