package com.g3ck0.seriestracker.di

import javax.inject.Qualifier

/**
 * The token this build uses to reach its backend. Injected rather than read from
 * BuildConfig so tests can set it.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class BackendToken
