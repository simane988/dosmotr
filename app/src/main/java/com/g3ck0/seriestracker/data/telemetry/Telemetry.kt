package com.g3ck0.seriestracker.data.telemetry

import com.g3ck0.seriestracker.BuildConfig
import com.g3ck0.seriestracker.data.local.MediaType

/**
 * The one way anything leaves the device besides a backend request.
 *
 * An interface rather than a Firebase call at the call site for two reasons. The obvious
 * one is testing: a ViewModel takes this and a JVM test hands it `FakeTelemetry`. The
 * other is that Firebase exists in the `store` flavour only — `direct` is built for
 * GitHub Releases and IzzyOnDroid, where a Google dependency is both a broken promise
 * (product/02-positioning.md says nothing watches the user) and, for F-Droid, a hard
 * rule. So [Telemetry] is implemented twice, in `app/src/store` and in `app/src/direct`,
 * and nothing above this file knows which one it got.
 *
 * What may be reported is not a matter of taste — see [TelemetryEvent].
 */
interface Telemetry {

    /**
     * Reports that something happened. [name] must be one of [TelemetryEvent]'s constants
     * and [param] one of [TelemetryEvent.ALLOWED_PARAMS]; anything else is dropped by the
     * implementation rather than sent, because the failure mode of a mistake here is a
     * title someone is watching ending up on a Google server.
     */
    fun event(name: String, param: String? = null)

    /** An exception that was handled but is still worth knowing about. */
    fun nonFatal(t: Throwable)

    /**
     * Turns collection on or off, from the switch in «О приложении». Persisted by
     * [CrashReporting]; this only tells the backend of the day about it.
     */
    fun setEnabled(enabled: Boolean)
}

/**
 * Every event the app is allowed to send, and every parameter value it may carry.
 *
 * The list is closed on purpose. The app's description, the about dialog and the privacy
 * policy all say the library stays on the device, so a title's name, a search query, a
 * note, a rating or a timestamp leaving the phone would not be a bug but a lie — and a
 * mismatch with what Data Safety in the Play Console declares. Names live here rather
 * than as literals at the call sites so a typo cannot quietly create a twelfth event.
 */
object TelemetryEvent {

    /** Once per install, not per start — see `SettingsStore.firstLaunchReported`. */
    const val FIRST_LAUNCH = "first_launch"

    /** Carries `tv` or `movie` and nothing else. */
    const val TITLE_ADDED = "title_added"

    const val EPISODE_WATCHED = "episode_watched"
    const val SEARCH_PERFORMED = "search_performed"
    const val MANUAL_ADD = "manual_add"
    const val IMPORT_STARTED = "import_started"
    const val IMPORT_FINISHED = "import_finished"
    const val EXPORT_DONE = "export_done"
    const val BACKUP_AUTO_OK = "backup_auto_ok"
    const val NOTIFICATIONS_ALLOWED = "notifications_allowed"
    const val ABOUT_OPENED = "about_opened"

    val ALL: Set<String> = setOf(
        FIRST_LAUNCH,
        TITLE_ADDED,
        EPISODE_WATCHED,
        SEARCH_PERFORMED,
        MANUAL_ADD,
        IMPORT_STARTED,
        IMPORT_FINISHED,
        EXPORT_DONE,
        BACKUP_AUTO_OK,
        NOTIFICATIONS_ALLOWED,
        ABOUT_OPENED,
    )

    /**
     * The only strings that may ride along with an event. Two media types — which of the
     * two kinds of thing was added is a product question worth an answer, and neither
     * value says anything about a person.
     */
    val ALLOWED_PARAMS: Set<String> = setOf("tv", "movie")
}

/**
 * The only shape in which a title may be described to the outside: which of the two kinds
 * it is. Lower case to match [TelemetryEvent.ALLOWED_PARAMS] and the `tv_`/`movie_` prefix
 * of [com.g3ck0.seriestracker.data.local.TitleEntity.id].
 */
val MediaType.telemetryParam: String get() = name.lowercase()

/**
 * True when an event with this name and this parameter may be sent at all.
 *
 * Kept as a free function so both implementations apply the same rule and a JVM test can
 * check it without a build variant, the way `donationsVisible` is split out of
 * `DonateConfig`.
 */
fun telemetryAllows(name: String, param: String?): Boolean =
    name in TelemetryEvent.ALL && (param == null || param in TelemetryEvent.ALLOWED_PARAMS)

/**
 * Whether this build can report anything at all, i.e. whether the switch in «О приложении»
 * is worth drawing. False in `direct`, where [Telemetry] is a no-op and there would be
 * nothing behind the switch.
 */
object TelemetryConfig {
    val available: Boolean = BuildConfig.CRASH_REPORTING_AVAILABLE
}
