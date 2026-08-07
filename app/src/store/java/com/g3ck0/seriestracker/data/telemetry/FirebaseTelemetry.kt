package com.g3ck0.seriestracker.data.telemetry

import android.content.Context
import android.os.Bundle
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The `store` half: crashes to Crashlytics, the eleven counters to Analytics.
 *
 * Two things this class exists to guarantee.
 *
 * **A build without `google-services.json` still works.** The file is configuration, not a
 * secret, but it belongs to a Firebase project — so CI, a fresh clone and every fork build
 * without one. The Gradle plugins are then not applied, no `FirebaseApp` is initialised,
 * and every call here would throw. [firebaseReady] is what turns that into a no-op instead
 * of a crash on the first added title.
 *
 * **Nothing personal can leave even by mistake.** [telemetryAllows] is applied to every
 * call rather than trusted to the caller: an event name that is not on the list, or a
 * parameter that is not `tv`/`movie`, is dropped. A title's name reaching this method is a
 * bug somewhere above, and dropping it is the only harmless way to react.
 */
@Singleton
class FirebaseTelemetry @Inject constructor(
    @ApplicationContext private val context: Context,
) : Telemetry {

    /**
     * Resolved once, lazily: touching Firebase during Hilt construction would run its
     * initialisation on the main thread before the first frame.
     */
    private val firebaseReady: Boolean by lazy { FirebaseApp.getApps(context).isNotEmpty() }

    private val analytics: FirebaseAnalytics? by lazy {
        if (firebaseReady) FirebaseAnalytics.getInstance(context) else null
    }

    private val crashlytics: FirebaseCrashlytics? by lazy {
        if (firebaseReady) FirebaseCrashlytics.getInstance() else null
    }

    override fun event(name: String, param: String?) {
        if (!telemetryAllows(name, param)) {
            // Loud in a debug build, silent in a shipped one: a dropped counter is not
            // worth a log line on someone's phone, but it must not pass unnoticed here.
            Log.w(TAG, "refusing to report $name")
            return
        }
        val extras = param?.let { Bundle().apply { putString(PARAM_KEY, it) } }
        analytics?.logEvent(name, extras)
    }

    override fun nonFatal(t: Throwable) {
        crashlytics?.recordException(t)
    }

    override fun setEnabled(enabled: Boolean) {
        // Both, and deliberately: the switch says «отчёты о падениях», and a person who
        // turns it off does not expect the counters to keep going.
        crashlytics?.isCrashlyticsCollectionEnabled = enabled
        analytics?.setAnalyticsCollectionEnabled(enabled)
    }

    private companion object {
        const val TAG = "Telemetry"

        /** One key for the one parameter there is — see `TelemetryEvent.ALLOWED_PARAMS`. */
        const val PARAM_KEY = "kind"
    }
}
