# CLAUDE.md

Guidance for Claude Code (claude.ai/code) working in this repository.

**This file is the short list: what is expensive to get wrong and cheap to state.**
Everything else lives in `ai-docs/` — see the index at the bottom, and read the file that
covers what you are touching *before* you touch it. When you learn something new about this
project, it goes into `ai-docs/`; it belongs here only if not knowing it costs a wiped
library, a rejected build or an unreviewed merge.

## Toolchain

The JDK, Android SDK and Gradle live under `$HOME`, not in system paths. Nothing builds
without exporting them first:

```bash
export JAVA_HOME=/home/g3ck0/.local/jdk/jdk-17.0.20+8
export ANDROID_HOME=/home/g3ck0/Android/Sdk
export PATH=$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$PATH
```

## Commands

**Every task name carries a distribution flavour** (`direct` or `store`), so `installDebug`,
`testDebugUnitTest` and `connectedDebugAndroidTest` do not exist — Gradle fails with "task
not found" rather than picking one. `direct` is the default: it is the superset.

```bash
./gradlew installDirectDebug          # «Досмотр debug», com.g3ck0.dosmotr.debug
./gradlew testDirectDebugUnitTest     # JVM tests, no device needed
./gradlew :app:lintDirectDebug        # what CI's `static` job fails on
./gradlew detekt                      # applied at the root, covers app/src entirely
./gradlew connectedDirectDebugAndroidTest   # needs a device
scripts/emulator.sh gui               # local AVD in a window — the fast path
scripts/emulator.sh test              # boot, run the suite, shut down again
```

**Wake the screen before instrumented tests** — `adb shell input keyevent KEYCODE_WAKEUP`.
With the display dozing, Compose UI tests fail on assertions about rendered text.

Lint is the second-heaviest task after R8 on this 8 GB machine, and `gradle.properties` is
tuned for it. If a build gets OOM-killed, cap that invocation rather than raising the
defaults — see `ai-docs/build-and-test.md`, which also has the emulator modes, the store
screenshot pipeline and the memory budget.

## Invariants

Breaking one of these is expensive and often silent. The reasoning behind each is in
`ai-docs/architecture.md`.

- **`TitleEntity.id`** is `tv_1399` / `movie_550` / `local_<uuid>`, so the same title cannot
  be added twice and manual entries never collide with catalogue ones.
- **Movies have no rows in `episodes`** — they carry a `movieWatched` flag. Anything
  iterating episodes must handle that.
- **Progress is never stored.** `TrackerDao.observeLibrary` computes it with `COUNT(*)`
  subqueries, so it cannot drift.
- **Schema changes are migrated, never dropped.** `fallbackToDestructiveMigration()` must
  not come back — it deletes the library of everyone who updates, and there is no cloud
  copy. Bumping `version` means three things in one commit: the `Migration`, the exported
  `app/schemas/…/<version>.json`, and the `FakeTrackerDao` update.
- **`upsertTitle` is `@Upsert`, not `INSERT OR REPLACE`.** REPLACE deletes the row first and
  the FK cascade takes every watched episode with it.
- **`insertEpisodes` uses `IGNORE`** so a refresh adds newly aired episodes without resetting
  watched flags. `upsertEpisodes` exists only for JSON import.
- **Season 0 is skipped** when pulling from the backend — specials wreck the percentages.
- **Library sort order lives twice**: `WatchStatus.libraryOrder` and a `CASE` in the DAO's
  SQL. `TrackerDaoTest.sqlOrderMatchesTheEnumOrder` fails if they drift apart.
- **Status is derived**, not just set: `TrackerRepository.afterProgressChange` completes a
  title on its last episode and pulls a completed one back to watching when unchecked.
- **The fakes mirror the DAO.** JVM tests use `fake/FakeTrackerDao`, which reproduces the SQL
  semantics on purpose, so changing the DAO means changing the fake.
- **Two persisted names still say `tmdb`** (`tmdbId`/`tmdbRating` columns, `tmdb_id`/
  `tmdb_rating` JSON keys), mapped to `catalogId`/`rating` in Kotlin. Renaming them for real
  is a migration, not a cosmetic change.

## Boundaries that are not preferences

- **No donations in the `store` flavour, and no wallet address either.** Part 7 of article 14
  of 259-ФЗ forbids distributing information about accepting digital currency, and Play reads
  an external donate link as circumventing its payment policy. `DONATE_URL`/`DONATE_SBP`/
  `DONATE_USDT` are compiled in as empty strings there, and `AboutDialogTest` asserts it.
- **The TMDB attribution is a legal requirement, not decoration.** `AboutDialog` carries
  TMDB's own logo asset and their sentence verbatim in English; `AboutDialogTest` asserts the
  full string, so it cannot be reworded by accident.
- **What telemetry may report is a closed list** (`TelemetryEvent`): eleven event names and
  two parameter values, `tv` and `movie`. Titles, queries, notes, ratings, timestamps and
  counts of anything are forbidden — the store listing, «О приложении» and the privacy policy
  all promise the library stays on the device. `FakeTelemetry` throws on anything else; do
  not widen the list to make a test pass.
- **`direct` carries no Google code at all** — an F-Droid rule and the "никакой слежки"
  promise. Firebase is compiled into `store` only.
- **Nothing in the store listing that the build does not do.** Notifications are asked for but
  not implemented, so they must not be described.

## Branches

```
feature/<name> ──▶ develop ──▶ release/<x.y.z> ──▶ master
```

**Never merge into `develop` or `master` locally.** Every arrow into those two is a pull
request on GitHub — the merge is the reviewable event, and a local merge pushed to
`origin/develop` lands the change with no review and auto-closes an open PR. "Get this into
develop" means: finish on `feature/<name>`, push, open the PR, hand the merge to auto-merge.
Sync with `git pull`, never with a local merge.

`scripts/close-task.sh <task-id>` is the whole flow for a backlog task — do not do the steps
by hand. The PR description is prose **in Russian**: what was wrong, what changed and why,
how it was verified.

The remote is **public** (`git@github.com:simane988/dosmotr.git`) and the app links to it, so
anything committed here is published. `local.properties` and `keystore/` are gitignored and
have never been committed; a leaked `backend.token` is a leaked backend, a leaked keystore is
permanent.

## Conventions

- UI strings are Russian literals in the composables (only `app_name` is in `strings.xml`).
  **Code, comments and commit messages are English.**
- Every screen is split in two: `XxxScreen` resolves the Hilt ViewModel, `XxxContent` is
  stateless and takes state plus callbacks. UI tests drive `XxxContent` directly.
- Elements are addressed in tests through tag objects (`LibraryTags`, `SearchTags`, …), not by
  text or position — preserve the tags when restyling.
- `ui` (Compose) → ViewModel → `TrackerRepository` → Room DAO + Retrofit `CatalogApi`, wired
  by Hilt. Room is the single source of truth; the network is only used for search and refresh.
- **The app knows one remote: its own backend.** Which catalogue that backend reads from is
  deliberately not represented in this codebase.

## Keep the context small

Everything in a session is re-sent to the model on every turn, so a page of build log read
once is paid for again on every turn after it. Sessions that fill their context run out of
budget before the work is done — which looks like a usage limit and is not one.

- This repository is indexed by CodeGraph: `codegraph explore "<symbols or question>"` returns
  the relevant source and its callers. Reach for it before grep and before reading files;
  `LibraryScreen.kt` is 47 KB and `DetailScreen.kt` is 53 KB.
- Redirect builds and read only what failed:
  `./gradlew … --console=plain -q > /tmp/build.log 2>&1 || tail -60 /tmp/build.log`.
- `git diff --stat` before `git diff`, and diff a path rather than the tree.
- Screenshots only for visual changes: one shot, read once, then describe it. An image stays
  in the context for the rest of the session.

## ai-docs

| файл | читать, когда |
| --- | --- |
| `ai-docs/build-and-test.md` | что-то не собирается, гоняешь тесты, нужен эмулятор или скриншоты для сторов, прогон не влезает в память |
| `ai-docs/architecture.md` | меняешь код приложения: слои, инварианты с объяснениями, конвенции экранов, тесты и фейки |
| `ai-docs/distribution-and-legal.md` | трогаешь `store`/`direct`, донаты, телеметрию, Firebase, тексты листинга |
| `ai-docs/secrets-and-backend.md` | трогаешь сеть, `local.properties`, подпись, ключи |
| `ai-docs/release-and-ci.md` | трогаешь workflow'ы, релиз, защиту веток, `close-task.sh` |
| `ai-docs/grind-loop.md` | правишь саму обвязку: `grind.sh`, `close-task.sh`, `claude-stream.py` |

`product/` (gitignored) holds the product plan; `todo/` (gitignored) holds the backlog.
