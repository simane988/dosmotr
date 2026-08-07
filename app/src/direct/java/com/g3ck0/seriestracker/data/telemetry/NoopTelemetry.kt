package com.g3ck0.seriestracker.data.telemetry

import javax.inject.Inject
import javax.inject.Singleton

/**
 * What the `direct` flavour reports: nothing.
 *
 * Not a stub waiting to be filled in — it is the whole point of the flavour. `direct` is
 * what GitHub Releases and IzzyOnDroid serve and what F-Droid could serve, and none of
 * those may carry a Google library. Keeping the calls in the shared code and emptying the
 * implementation is what lets both builds compile from the same ViewModels while
 * `directReleaseRuntimeClasspath` stays free of `com.google.firebase`.
 */
@Singleton
class NoopTelemetry @Inject constructor() : Telemetry {

    override fun event(name: String, param: String?) = Unit

    override fun nonFatal(t: Throwable) = Unit

    override fun setEnabled(enabled: Boolean) = Unit
}
