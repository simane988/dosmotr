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

./gradlew :app:lintDebug         # what CI's `static` job fails on
./gradlew detekt                 # applied at the root, covers app/src entirely
./gradlew :app:updateLintBaseline  # after fixing (or accepting) findings
./gradlew detektBaseline
```

Lint is the second-heaviest task after R8 on this machine. If it gets OOM-killed, cap it
rather than raising `gradle.properties`:

```bash
./gradlew --no-daemon -Dorg.gradle.jvmargs="-Xmx1280m -XX:MaxMetaspaceSize=512m" \
  -Dkotlin.daemon.jvmargs=-Xmx1g :app:lintDebug
```

**Wake the screen before instrumented tests** — `adb shell input keyevent KEYCODE_WAKEUP`.
With the display dozing, Compose UI tests fail on assertions about rendered text; the
same tests pass once it is awake. This is reproducible, not flaky hardware.

`connectedDebugAndroidTest` is finalized by `installDebug` (see the bottom of
`app/build.gradle.kts`): AGP uninstalls both APKs when it finishes, which used to leave
the phone with no build on it. The finalizer runs even when tests fail.

## Secrets and signing

`local.properties` holds `backend.url` / `backend.token` and the `release.*` signing
credentials; `keystore/dosmotr-release.jks` holds the key. Both are gitignored and exist
only on this machine — losing the keystore means never being able to update a published
build.

With no `backend.url` the app still builds; the search screen shows an explanatory empty
state and only manual entry works. `backend.url` without `backend.token` fails the build
on purpose — it would otherwise 403 on every call at runtime.

**The app knows one remote: its own backend.** Which catalogue that backend reads from is
not represented anywhere in this codebase, and deliberately so — the source can change
server-side without a new build. Concretely: `CatalogApi` speaks the app's own
`/v1/search`, `/v1/tv/{id}`, `/v1/tv/{id}/season/{n}`, `/v1/movie/{id}`; artwork comes
from `/img/{size}/{path}` via `CatalogImage`; the single credential is `@BackendToken`,
sent as `X-Backend-Token`. Language and adult filtering are pinned on the backend, not
here, because they are properties of the source.

Two persisted names still say `tmdb`: the `tmdbId` / `tmdbRating` columns of `titles` and
the `tmdb_id` / `tmdb_rating` keys in backup JSON. Kotlin calls them `catalogId` and
`rating`, mapped with `@ColumnInfo` / `@SerialName`. **Renaming them for real is not a
cosmetic change**: the database uses `fallbackToDestructiveMigration()`, so a column
rename without a migration wipes every user's library, and changing the JSON keys breaks
importing older backups.

The backend is a **separate project**, not part of this build: `~/projects/dosmotr-backend`
(nginx + Caddy, deployed with Docker Compose). Nothing here depends on it at compile
time — the coupling is the two `local.properties` values, the `X-Backend-Token` header
name, and the `/v1` paths above.

## Memory budget

`gradle.properties` is tuned for an 8 GB machine (Gradle 1536m, Kotlin daemon 1280m,
`org.gradle.parallel=false`). The OOM killer takes builds out without these. R8 in the
release build is the heaviest step — the `profileable` build type disables it on purpose,
since what makes debug frame times meaningless is `debuggable`, not the missing shrinking.

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
verbatim in English. `LibraryContentTest` asserts on the full sentence, so it cannot be
reworded by accident. The obligation stands while TMDB is the catalogue behind the
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

Instrumented tests cover the real Room database, JSON backup merge/replace, all four
screens and the dark scheme. Espresso 3.7+ is required: older releases crash on Android
16+ with `NoSuchMethodException: InputManager.getInstance`, which fails every Compose test.

Animation work is verified by slowing the device down
(`adb shell settings put global animator_duration_scale 8`), capturing a frame, then
restoring `1.0`. That shows geometry and colour, not smoothness.

## Branches and releases

Remote is `git@github.com:simane988/dosmotr.git` — **public**, and the "О приложении"
dialog links to it, so anything committed here is published. `local.properties` and
`keystore/` are gitignored and have never been committed; keep it that way, because on a
public repo a leaked `backend.token` is a leaked backend and a leaked keystore is
permanent.

```
feature/<name> ──▶ develop ──▶ release/<x.y.z> ──▶ master
                                     │
                                     └─▶ GitHub Release + signed APK
```

- `master` is what is published — every commit on it is a shipped version.
- `develop` is where finished features accumulate.
- `feature/<name>` branches off `develop` and merges back into it.
- `release/<x.y.z>` branches off `develop`. **Pushing it is what publishes**, so the
  branch name is the version: `release/1.2.0` becomes `v1.2.0`. Both merge-backs — into
  `master` *and* into `develop` — are opened as pull requests by the release workflow with
  auto-merge on; without the second one the version bump CI made lives only on a branch
  nobody reads again.

**Never merge into `develop` or `master` locally.** Every arrow into those two branches
is a pull request on GitHub — the merge is the reviewable event, and a local merge pushed
straight to `origin/develop` lands the change with no review and auto-closes an open PR.
So "get this feature into develop" means: finish it on `feature/<name>`, push that branch,
`gh pr create --base develop`, and stop — merging the PR is not yours to do. Afterwards
sync with `git pull`, never with a local merge.

Nothing about the release is typed by hand. `.github/workflows/release.yml` reads the
version out of the branch name, bumps `versionCode` in `version.properties`, commits that
back with `[skip ci]` (without which the push would start the workflow over), builds the
signed APK, attaches it to a GitHub Release tagged on the release branch, and finally opens
the two merge-back PRs (`→ master`, then `→ develop`) with `gh pr merge --auto`.
`version.properties` is the only place a version lives — `app/build.gradle.kts` reads it,
so do not put literals back into `defaultConfig`, and do not edit the file on a release
branch by hand: CI rewrites it there.

### Workflows

`.github/workflows/ci.yml` is the only place the verification steps are written down, in
this order — cheapest first, so the emulator never starts for a commit that lint already
rejected:

```
decide ─▶ sync-develop ─▶ static (lint + detekt) ─┬─▶ unit ─▶ instrumented (API 35)
                       └─▶ secrets (gitleaks)  ───┘
                       └─▶ dependencies (PRs only)
```

It builds *without* backend credentials on purpose — that is what keeps a build with no
`backend.url` working, since the search screen's empty state depends on it.

Two mechanisms keep one commit from being tested twice, which is the whole point of the
`decide` job:

- **A push to `feature/**` is skipped once that branch has an open PR.** The `pull_request`
  run tests the same commit merged into its base, so both is pure waste. `decide` looks the
  PR up with `gh pr list --head` and every other job hangs off `needs.decide.outputs.run`.
  The check is limited to `feature/**`: `master`/`develop` are never the head of a PR, and a
  release branch must still be verified while its own merge-back PRs are open.
- **`release/**` is not a trigger in `ci.yml`.** `release.yml`'s `verify` job calls it
  through `workflow_call` instead, so a release branch runs the suite once, inside the run
  that builds the APK, rather than in a second pipeline racing it.

`sync-develop` merges `origin/develop` into the branch **before** anything is tested, on
feature pushes only, so a stale branch is tested as it will be merged. The merge commit
carries `[skip ci]`: the jobs after it already test the merged tree, and without the marker
pushing it would start a second identical run. A conflict fails the job with a message —
CI does not resolve conflicts.

Static analysis is baselined, so both tools fail on findings a commit *introduces*:

- `app/lint-baseline.xml` (regenerate with `./gradlew :app:updateLintBaseline`). The `lint`
  block in `app/build.gradle.kts` has `warningsAsErrors`, and switches off the checks that
  are decisions rather than defects: `MissingTranslation`/`HardcodedText` (the app is
  Russian-only) and `GradleDependency`/`AndroidGradlePluginVersion`/`OldTargetApi` (a
  version bump is its own change; CVEs are the dependency job's business). `OldTargetApi`
  also *only* fires on CI, whose SDK manager knows about a newer platform than this machine
  has installed — the kind of check that passes locally and fails in the pipeline.
  `checkDependencies` is off — with it on, lint is another step this machine cannot fit in
  RAM.
- `config/detekt/baseline.xml` (regenerate with `./gradlew detektBaseline`), configured by
  `config/detekt/detekt.yml` on top of detekt's defaults. Detekt is applied at the *root*
  project over `app/src`, so `./gradlew detekt` covers main, test and androidTest at once.

Both emit SARIF that lands in the repository's Security tab.

Security jobs, and why each one is where it is:

- `secrets` runs **gitleaks over the full history** on every branch — the repo is public and
  `local.properties` holds `backend.token`, and a secret deleted in a later commit is still
  published by the one that added it. No licence key while the repo is public.
- `dependencies` runs on pull requests only, because that is the one event
  `dependency-review-action` supports. Gradle has no lockfile for a scanner to read, so
  `gradle/actions/dependency-submission` resolves the graph first. The push side of that is
  `.github/workflows/dependency-graph.yml` (master/develop + weekly), which is what makes
  Dependabot alerts appear at all.
- `.github/workflows/codeql.yml` is **not** part of CI: it costs a full compile, so it runs
  weekly against `develop` (and on `workflow_dispatch`). A scheduled run starts on the
  default branch, hence the explicit `ref: develop` in its checkout. `build-mode: manual` —
  autobuild guesses the Gradle invocation and can end up analysing nothing.

Auto-merge on the release PRs needs **"Allow auto-merge" enabled in the repository
settings**. Without it the workflow logs a warning and leaves both PRs open, which is the
same outcome minus the convenience.

Releasing needs six repository secrets, and CI fails loudly if any is missing:
`KEYSTORE_BASE64` (`base64 -w0 keystore/dosmotr-release.jks`), `KEYSTORE_PASSWORD`,
`KEY_ALIAS`, `KEY_PASSWORD`, `BACKEND_URL`, `BACKEND_TOKEN`. They exist only as secrets
and are written into `local.properties` for the length of one job, so the build has
exactly one way to find them either way.
