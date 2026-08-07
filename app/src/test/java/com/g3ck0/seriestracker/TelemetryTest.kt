package com.g3ck0.seriestracker

import com.g3ck0.seriestracker.data.local.MediaType
import com.g3ck0.seriestracker.data.local.WatchStatus
import com.g3ck0.seriestracker.data.remote.MovieDetailsDto
import com.g3ck0.seriestracker.data.repository.SearchItem
import com.g3ck0.seriestracker.data.repository.TrackerRepository
import com.g3ck0.seriestracker.data.telemetry.CrashReporting
import com.g3ck0.seriestracker.data.telemetry.TelemetryEvent
import com.g3ck0.seriestracker.data.telemetry.telemetryAllows
import com.g3ck0.seriestracker.data.telemetry.telemetryParam
import com.g3ck0.seriestracker.fake.FakeCatalogApi
import com.g3ck0.seriestracker.fake.FakeSettingsStore
import com.g3ck0.seriestracker.fake.FakeTelemetry
import com.g3ck0.seriestracker.fake.FakeTrackerDao
import com.g3ck0.seriestracker.fake.MainDispatcherRule
import com.g3ck0.seriestracker.fake.seasonOf
import com.g3ck0.seriestracker.fake.tvDetailsOf
import com.g3ck0.seriestracker.fake.tvResult
import com.g3ck0.seriestracker.ui.library.LibraryViewModel
import com.g3ck0.seriestracker.ui.search.SearchViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * What may leave the device, and what must not.
 *
 * The app's description, «О приложении» and the privacy policy all promise that the
 * library stays on the phone, and the Data Safety form in the Play Console repeats it. So
 * the interesting assertions here are the negative ones: a title's name, a search query
 * and a count of titles are checked to be *absent* from what was reported, using the real
 * ViewModels rather than by reading the code.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TelemetryTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val dao = FakeTrackerDao()
    private val api = FakeCatalogApi()
    private val telemetry = FakeTelemetry()
    private val settings = FakeSettingsStore()
    private val repository = TrackerRepository(dao, api, "key")

    // --- the allow-list itself ---

    @Test
    fun `only the listed events may be sent`() {
        assertTrue(telemetryAllows(TelemetryEvent.SEARCH_PERFORMED, null))
        assertFalse(telemetryAllows("episode_watched_dark_s01e02", null))
        assertFalse(telemetryAllows("screen_view", null))
    }

    @Test
    fun `only tv and movie may ride along as a parameter`() {
        assertTrue(telemetryAllows(TelemetryEvent.TITLE_ADDED, "tv"))
        assertTrue(telemetryAllows(TelemetryEvent.TITLE_ADDED, "movie"))
        assertFalse(telemetryAllows(TelemetryEvent.TITLE_ADDED, "Dark"))
        assertFalse(telemetryAllows(TelemetryEvent.SEARCH_PERFORMED, "тёмное дитя"))
    }

    @Test
    fun `the media type maps onto the two allowed values and nothing else`() {
        assertEquals("tv", MediaType.TV.telemetryParam)
        assertEquals("movie", MediaType.MOVIE.telemetryParam)
        assertEquals(TelemetryEvent.ALLOWED_PARAMS, MediaType.entries.map { it.telemetryParam }.toSet())
    }

    @Test
    fun `the list of names has no duplicates and no strays`() {
        // ALL is written out by hand next to the constants, so it can fall behind them.
        assertEquals(11, TelemetryEvent.ALL.size)
        assertTrue(TelemetryEvent.ALL.all { it == it.lowercase() && it.isNotBlank() })
    }

    // --- what the ViewModels actually report ---

    @Test
    fun `a search reports that it happened and not what was searched for`() = runTest {
        api.searchResults = listOf(tvResult(1, "Dark"))
        val vm = SearchViewModel(repository, telemetry)
        advanceUntilIdle()

        vm.onQueryChange("тёмное дитя")
        advanceUntilIdle()

        assertEquals("тёмное дитя", api.lastQuery)
        assertEquals(1, telemetry.count(TelemetryEvent.SEARCH_PERFORMED))
        assertTrue(telemetry.events.all { it.param == null })
    }

    @Test
    fun `adding a series reports its kind and never its name`() = runTest {
        api.tvDetails = tvDetailsOf(7, "Dark", mapOf(1 to 2))
        api.seasons = mapOf(1 to seasonOf(1, 2))
        val vm = SearchViewModel(repository, telemetry)
        advanceUntilIdle()
        telemetry.clear()

        vm.add(seriesItem(), WatchStatus.PLANNED)
        advanceUntilIdle()

        assertEquals(listOf(TelemetryEvent.TITLE_ADDED), telemetry.names)
        assertEquals("tv", telemetry.events.single().param)
    }

    @Test
    fun `adding a film reports movie, not the film`() = runTest {
        api.movieDetails = MovieDetailsDto(id = 9, title = "Fight Club", runtime = 139)
        val vm = SearchViewModel(repository, telemetry)
        advanceUntilIdle()
        telemetry.clear()

        vm.add(filmItem(), WatchStatus.PLANNED)
        advanceUntilIdle()

        assertEquals(listOf(TelemetryEvent.TITLE_ADDED), telemetry.names)
        assertEquals("movie", telemetry.events.single().param)
    }

    @Test
    fun `a manual entry reports nothing about what was typed`() = runTest {
        val vm = SearchViewModel(repository, telemetry)
        advanceUntilIdle()
        telemetry.clear()

        vm.addManual("Мой сериал", MediaType.TV, listOf(3), runtimeMinutes = 40, year = "2020")
        advanceUntilIdle()

        assertEquals(listOf(TelemetryEvent.MANUAL_ADD), telemetry.names)
        assertTrue(telemetry.events.single().param == null)
    }

    /** The one event that must not repeat, because it is what the funnel divides by. */
    @Test
    fun `first launch is reported once per install and never again`() = runTest {
        LibraryViewModel(repository, settings, telemetry)
        advanceUntilIdle()
        LibraryViewModel(repository, settings, telemetry)
        advanceUntilIdle()

        assertEquals(1, telemetry.count(TelemetryEvent.FIRST_LAUNCH))
        assertTrue(settings.storedFirstLaunchReported)
    }

    @Test
    fun `a restart with the flag already stored reports nothing`() = runTest {
        LibraryViewModel(repository, FakeSettingsStore(firstLaunchReported = true), telemetry)
        advanceUntilIdle()

        assertTrue(telemetry.names.isEmpty())
    }

    @Test
    fun `a refused notification prompt is not reported as an allowed one`() = runTest {
        val vm = LibraryViewModel(repository, settings, telemetry)
        advanceUntilIdle()
        telemetry.clear()

        vm.markNotificationsAsked(granted = false)
        advanceUntilIdle()

        assertEquals(0, telemetry.count(TelemetryEvent.NOTIFICATIONS_ALLOWED))
        assertTrue(settings.storedNotificationsAsked)

        vm.markNotificationsAsked(granted = true)
        advanceUntilIdle()

        assertEquals(1, telemetry.count(TelemetryEvent.NOTIFICATIONS_ALLOWED))
    }

    // --- the switch ---

    @Test
    fun `the stored setting is what a restart applies`() = runTest {
        val settings = FakeSettingsStore(crashReportsEnabled = false)

        CrashReporting(settings, telemetry).sync()

        assertEquals(false, telemetry.enabled)
    }

    @Test
    fun `switching it off both persists and takes effect`() = runTest {
        val reporting = CrashReporting(settings, telemetry)

        reporting.setEnabled(false)

        assertEquals(false, telemetry.enabled)
        assertFalse(settings.storedCrashReportsEnabled)

        // And a fresh process reads back what was stored rather than the default.
        FakeTelemetry().let { restarted ->
            CrashReporting(settings, restarted).sync()
            assertEquals(false, restarted.enabled)
        }
    }

    @Test
    fun `reporting is on until someone turns it off`() = runTest {
        CrashReporting(settings, telemetry).sync()

        assertEquals(true, telemetry.enabled)
    }

    private fun seriesItem() = SearchItem(
        catalogId = 7,
        mediaType = MediaType.TV,
        name = "Dark",
        overview = "",
        posterPath = null,
        backdropPath = null,
        year = "2017",
        voteAverage = 8.7,
    )

    private fun filmItem() = seriesItem().copy(catalogId = 9, mediaType = MediaType.MOVIE, name = "Fight Club")
}
