# Архитектура, UI-конвенции, тесты

Читать перед правкой кода приложения. Короткий список инвариантов есть в
CLAUDE.md — здесь то же самое с объяснениями, плюс конвенции экранов и тестов.

## Architecture

`ui` (Compose) → ViewModel → `TrackerRepository` → Room DAO + Retrofit `CatalogApi`,
wired by Hilt.
Room is the single source of truth; the network is only used for search and refresh.

Invariants that are easy to break and expensive to debug:

- **`TitleEntity.id`** is `tv_1399` / `movie_550` / `local_<uuid>`, so the same title
  cannot be added twice and manual entries never collide with catalogue ones.
- **Movies have no rows in `episodes`** — they carry a `movieWatched` flag. Anything
  iterating episodes must handle that.
- **Progress is never stored.** `TrackerDao.observeLibrary` computes it with `COUNT(*)`
  subqueries, so it cannot drift out of sync.
- **Schema changes are migrated, never dropped.** The database is built with
  `.addMigrations(*AppDatabase.MIGRATIONS)` and **`fallbackToDestructiveMigration()` must
  not come back** — it deletes the library of everyone who updates, and there is no cloud
  copy to restore from. So bumping `version` in `AppDatabase` means three things in one
  commit: the `Migration` itself, the exported `app/schemas/…/<version>.json`, and the
  `FakeTrackerDao` update if the DAO moved with it. `MigrationTest` opens a version 1
  file with the current schema and fails when any of that is missing.
- **`upsertTitle` is `@Upsert`, not `INSERT OR REPLACE`.** REPLACE deletes the row first
  and the FK cascade takes every watched episode with it.
- **`insertEpisodes` uses `IGNORE`** so a refresh adds newly aired episodes without
  resetting watched flags. `upsertEpisodes` (overwriting) exists only for JSON import.
- **Season 0 is skipped** when pulling from the backend — specials otherwise wreck the
  percentages.
- **Library sort order lives twice**: `WatchStatus.libraryOrder` and a `CASE` in the DAO's
  SQL, because Room cannot read an enum field from a query.
  `TrackerDaoTest.sqlOrderMatchesTheEnumOrder` fails if they drift apart.
- **Status is derived**, not just set: `TrackerRepository.afterProgressChange` completes a
  title on its last episode and pulls a completed one back to watching when unchecked.
- **Language is pinned to `ru-RU` on the backend**, not taken from the device locale —
  the UI is Russian, and an en-US phone would otherwise mix Russian labels with English
  synopses. The app does not send a language at all.
- **The backend token is injected** via `@BackendToken`, not read from `BuildConfig` at
  the call site, so tests do not depend on what is in `local.properties`.

UI strings are Russian literals in the composables (only `app_name` is in `strings.xml`).
Code, comments and commit messages are English.

## UI conventions

Every screen is split in two: `XxxScreen` resolves the Hilt ViewModel, `XxxContent` is
stateless and takes state plus callbacks. UI tests drive `XxxContent` directly, so they
need neither Hilt nor a database. Keep that split when adding screens.

Elements are addressed in tests through tag objects (`LibraryTags`, `SearchTags`,
`DetailTags`, `StatsTags`, `ManualAddTags`, `NavTags`, `AboutTags`) rather than by text or position —
preserve the tags when restyling. Tags on text fields belong on the editable node
(`fieldModifier`), not the container, or `performTextInput` cannot reach them.

**The TMDB attribution is a legal requirement, not decoration.** The library's overflow
menu opens `AboutDialog`, which carries `res/drawable/ic_tmdb_logo.xml` (TMDB's own asset,
converted to a vector drawable — do not restyle or recolour it) and their sentence
verbatim in English. `AboutDialogTest` asserts on the full sentence, so it cannot be
reworded by accident — it moved there from `LibraryContentTest` when the dialog gained the
crash-report switch and with it a ViewModel, so the dialog is hosted by `LibraryScreen` and
the stateless `LibraryContent` only raises `onAbout`. The obligation stands while TMDB is the catalogue behind the
backend, even though the app never talks to TMDB itself.

The current design is the Claude Design mock, variant B: floating navigation pill with a
highlight that animates to the selected tab's measured bounds, screens split by a
segmented control on the detail screen, and `FloatingNavClearance` as the bottom padding
for every scrollable list so rows are not trapped under the bar.

Theme takes the key colour from the system (Material You) on Android 12+, with the mock's
palette as the fallback; tests pass `dynamicColor = false` so results do not depend on the
device wallpaper. Anything painted behind the NavHost matters: without a surface-coloured
background and a matching `windowBackground`, the white window flashes between screens.

## Tests

JVM tests use hand-written fakes (`fake/FakeTrackerDao`, `fake/FakeCatalogApi`), not mocks.
`FakeTrackerDao` deliberately mirrors the SQL semantics — conflict strategies, the FK
cascade, sort order — so **changing the DAO means updating the fake too**; the instrumented
`TrackerDaoTest` is what proves the real SQL.

`fake/FakeTelemetry` is a fake with teeth: its `event()` **throws** on anything outside
`TelemetryEvent`'s allow-list instead of recording it. It is injected into every ViewModel
test, so a call that one day passes a title's name, a search query or a note fails the
suite here rather than shipping. Do not "fix" such a failure by widening the list — see
`ai-docs/distribution-and-legal.md`.

Instrumented tests cover the real Room database, JSON backup merge/replace, all four
screens and the dark scheme. Espresso 3.7+ is required: older releases crash on Android
16+ with `NoSuchMethodException: InputManager.getInstance`, which fails every Compose test.

Animation work is verified by slowing the device down
(`adb shell settings put global animator_duration_scale 8`), capturing a frame, then
restoring `1.0`. That shows geometry and colour, not smoothness.
