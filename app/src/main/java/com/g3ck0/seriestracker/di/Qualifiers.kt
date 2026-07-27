package com.g3ck0.seriestracker.di

import javax.inject.Qualifier

/** The TMDB v3 key. Injected rather than read from BuildConfig so tests can set it. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class TmdbApiKey
