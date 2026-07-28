# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Toolchain

The JDK, Android SDK and Gradle live under `$HOME`, not in system paths. Nothing builds
without exporting them first:

```bash
export JAVA_HOME=/home/g3ck0/.local/jdk/jdk-17.0.20+8
export ANDROID_HOME=/home/g3ck0/Android/Sdk
export PATH=$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$PATH
```

## Commands

```bash
./gradlew installDebug           # «Досмотр debug», com.g3ck0.dosmotr.debug
./gradlew installRelease         # «Досмотр», com.g3ck0.dosmotr — signed, R8 on
./gradlew installProfileable     # for performance measurement only

./gradlew testDebugUnitTest      # 82 JVM tests, no device needed
./gradlew testDebugUnitTest --tests "com.g3ck0.seriestracker.LabelsTest"

./gradlew connectedDebugAndroidTest   # 105 tests, needs a device
./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.g3ck0.seriestracker.ui.StatsContentTest
```

**Wake the screen before instrumented tests** — `adb shell input keyevent KEYCODE_WAKEUP`.
With the display dozing, Compose UI tests fail on assertions about rendered text; the
same tests pass once it is awake. This is reproducible, not flaky hardware.

`connectedDebugAndroidTest` is finalized by `installDebug` (see the bottom of
`app/build.gradle.kts`): AGP uninstalls both APKs when it finishes, which used to leave
the phone with no build on it. The finalizer runs even when tests fail.

## Secrets and signing

`local.properties` holds `tmdb.apiKey` and the `release.*` signing credentials;
`keystore/dosmotr-release.jks` holds the key. Both are gitignored and exist only on this
machine — losing the keystore means never being able to update a published build.

Without a TMDB key the app still builds; the search screen shows an explanatory empty
state and only manual entry works.

## Memory budget

`gradle.properties` is tuned for an 8 GB machine (Gradle 1536m, Kotlin daemon 1280m,
`org.gradle.parallel=false`). The OOM killer takes builds out without these. R8 in the
release build is the heaviest step — the `profileable` build type disables it on purpose,
since what makes debug frame times meaningless is `debuggable`, not the missing shrinking.

## Architecture

`ui` (Compose) → ViewModel → `TrackerRepository` → Room DAO + Retrofit TMDB, wired by Hilt.
Room is the single source of truth; the network is only used for search and refresh.

Invariants that are easy to break and expensive to debug:

- **`TitleEntity.id`** is `tv_1399` / `movie_550` / `local_<uuid>`, so the same title
  cannot be added twice and manual entries never collide with TMDB ones.
- **Movies have no rows in `episodes`** — they carry a `movieWatched` flag. Anything
  iterating episodes must handle that.
- **Progress is never stored.** `TrackerDao.observeLibrary` computes it with `COUNT(*)`
  subqueries, so it cannot drift out of sync.
- **`upsertTitle` is `@Upsert`, not `INSERT OR REPLACE`.** REPLACE deletes the row first
  and the FK cascade takes every watched episode with it.
- **`insertEpisodes` uses `IGNORE`** so a TMDB refresh adds newly aired episodes without
  resetting watched flags. `upsertEpisodes` (overwriting) exists only for JSON import.
- **Season 0 is skipped** when pulling from TMDB — specials otherwise wreck the percentages.
- **Library sort order lives twice**: `WatchStatus.libraryOrder` and a `CASE` in the DAO's
  SQL, because Room cannot read an enum field from a query.
  `TrackerDaoTest.sqlOrderMatchesTheEnumOrder` fails if they drift apart.
- **Status is derived**, not just set: `TrackerRepository.afterProgressChange` completes a
  title on its last episode and pulls a completed one back to watching when unchecked.
- **TMDB language is pinned to `ru-RU`** (`TMDB_LANGUAGE` in `di/AppModule.kt`), not taken
  from the device locale — the UI is Russian, and an en-US phone would otherwise mix
  Russian labels with English synopses.
- **The API key is injected** via `@TmdbApiKey`, not read from `BuildConfig` at the call
  site, so tests do not depend on what is in `local.properties`.

UI strings are Russian literals in the composables (only `app_name` is in `strings.xml`).
Code, comments and commit messages are English.

## UI conventions

Every screen is split in two: `XxxScreen` resolves the Hilt ViewModel, `XxxContent` is
stateless and takes state plus callbacks. UI tests drive `XxxContent` directly, so they
need neither Hilt nor a database. Keep that split when adding screens.

Elements are addressed in tests through tag objects (`LibraryTags`, `SearchTags`,
`DetailTags`, `StatsTags`, `ManualAddTags`, `NavTags`) rather than by text or position —
preserve the tags when restyling. Tags on text fields belong on the editable node
(`fieldModifier`), not the container, or `performTextInput` cannot reach them.

The current design is the Claude Design mock, variant B: floating navigation pill with a
highlight that animates to the selected tab's measured bounds, screens split by a
segmented control on the detail screen, and `FloatingNavClearance` as the bottom padding
for every scrollable list so rows are not trapped under the bar.

Theme takes the key colour from the system (Material You) on Android 12+, with the mock's
palette as the fallback; tests pass `dynamicColor = false` so results do not depend on the
device wallpaper. Anything painted behind the NavHost matters: without a surface-coloured
background and a matching `windowBackground`, the white window flashes between screens.

## Tests

JVM tests use hand-written fakes (`fake/FakeTrackerDao`, `fake/FakeTmdbApi`), not mocks.
`FakeTrackerDao` deliberately mirrors the SQL semantics — conflict strategies, the FK
cascade, sort order — so **changing the DAO means updating the fake too**; the instrumented
`TrackerDaoTest` is what proves the real SQL.

Instrumented tests cover the real Room database, JSON backup merge/replace, all four
screens and the dark scheme. Espresso 3.7+ is required: older releases crash on Android
16+ with `NoSuchMethodException: InputManager.getInstance`, which fails every Compose test.

Animation work is verified by slowing the device down
(`adb shell settings put global animator_duration_scale 8`), capturing a frame, then
restoring `1.0`. That shows geometry and colour, not smoothness.

## Branches

`master` is the pre-redesign app; `redesign` carries the Claude Design variant B work and
is currently ahead. Remote is `git@github.com:simane988/dosmotr.git` (private).
