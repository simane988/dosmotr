package com.g3ck0.seriestracker.fake

import com.g3ck0.seriestracker.data.telemetry.Telemetry
import com.g3ck0.seriestracker.data.telemetry.telemetryAllows

/** One reported event, as the implementation would have sent it. */
data class RecordedEvent(val name: String, val param: String? = null)

/**
 * In-memory [Telemetry], the way [FakeTrackerDao] stands in for the DAO — and a tripwire.
 *
 * [event] does not merely record: it *throws* on anything the allow-list in
 * `TelemetryEvent` does not cover. That is deliberate and is the mechanism behind
 * feature-18's "ни одно событие не несёт названий, запросов и текстов". A ViewModel that
 * one day passes a title's name, a query or a note ends up here first — this fake is
 * injected into every ViewModel test — and the suite fails with the offending value in the
 * message, instead of the string quietly reaching a Google server in a shipped build.
 */
class FakeTelemetry : Telemetry {

    private val recorded = mutableListOf<RecordedEvent>()

    val events: List<RecordedEvent> get() = recorded.toList()

    /** Just the names, for the common assertion that something was or was not reported. */
    val names: List<String> get() = recorded.map { it.name }

    val nonFatals = mutableListOf<Throwable>()

    var enabled: Boolean? = null
        private set

    override fun event(name: String, param: String?) {
        require(telemetryAllows(name, param)) { "telemetry may not carry '$name'/'$param'" }
        recorded += RecordedEvent(name, param)
    }

    override fun nonFatal(t: Throwable) {
        nonFatals += t
    }

    override fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
    }

    fun count(name: String): Int = recorded.count { it.name == name }

    fun clear() {
        recorded.clear()
        nonFatals.clear()
    }
}
