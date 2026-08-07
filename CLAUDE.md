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

**Every task name carries a flavour.** `store` and `direct` (see "Distribution flavours"
below) multiply the variants, so `installDebug`, `testDebugUnitTest` and
`connectedDebugAndroidTest` no longer exist — Gradle fails with "task not found" rather
than picking one. `direct` is the default everywhere: it is the superset, since the
donation block is compiled into it and absent from `store`.

```bash
./gradlew installDirectDebug     # «Досмотр debug», com.g3ck0.dosmotr.debug
./gradlew installStoreDebug      # same app id — replaces the above on the device
./gradlew installDirectRelease   # «Досмотр», com.g3ck0.dosmotr — signed, R8 on
./gradlew installDirectProfileable    # for performance measurement only

./gradlew testDirectDebugUnitTest     # 139 JVM tests, no device needed
./gradlew testDirectDebugUnitTest --tests "com.g3ck0.seriestracker.LabelsTest"
./gradlew testStoreDebugUnitTest      # the other flavour, same tests

./gradlew connectedDirectDebugAndroidTest   # 191 tests, needs a device
./gradlew connectedDirectDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.g3ck0.seriestracker.ui.StatsContentTest

scripts/emulator.sh gui          # local AVD in a window — the fast path, see below
scripts/emulator.sh test         # start on the GPU, run the suite, shut it down again
scripts/emulator.sh test --headless   # the swiftshader path CI uses, for reproducing it
scripts/emulator.sh test --keep       # leave it up afterwards
FLAVOR=store scripts/emulator.sh test  # the store variant instead of direct

./gradlew :app:lintDirectDebug   # what CI's `static` job fails on
./gradlew detekt                 # applied at the root, covers app/src entirely
./gradlew :app:updateLintBaselineDirectDebug  # after fixing (or accepting) findings
./gradlew detektBaseline
```

Lint is the second-heaviest task after R8 on this machine. If it gets OOM-killed, cap it
rather than raising `gradle.properties`:

```bash
./gradlew --no-daemon -Dorg.gradle.jvmargs="-Xmx1280m -XX:MaxMetaspaceSize=512m" \
  -Dkotlin.daemon.jvmargs=-Xmx1g :app:lintDirectDebug
```

**Wake the screen before instrumented tests** — `adb shell input keyevent KEYCODE_WAKEUP`.
With the display dozing, Compose UI tests fail on assertions about rendered text; the
same tests pass once it is awake. This is reproducible, not flaky hardware.

`connected<Flavour>DebugAndroidTest` is finalized by `install<Flavour>Debug` (see the
bottom of `app/build.gradle.kts`): AGP uninstalls both APKs when it finishes, which used
to leave the phone with no build on it. The finalizer runs even when tests fail. It
matches the task by pattern rather than by name, because an equality check against
`connectedDebugAndroidTest` stopped matching anything the moment flavours were added —
silently, with the bare phone as the only symptom.

### Local emulator

`scripts/emulator.sh` runs the suite without the phone on USB. The AVD it expects,
`dosmotr_ci_35`, mirrors the `instrumented` job: API 35, `google_apis`, x86_64,
`pixel_6`. Create it with:

```bash
sdkmanager --install emulator "system-images;android-35;google_apis;x86_64"
avdmanager create avd -n dosmotr_ci_35 -k "system-images;android-35;google_apis;x86_64" -d pixel_6
```

then raise `hw.ramSize` to 2048M, `vm.heapSize` to 512M and the data partition to 4096M
in `~/.android/avd/dosmotr_ci_35.avd/config.ini`. Also set `hw.keyboard=yes`, or the
soft keyboard covers the views Compose tests assert on. KVM needs nothing on this
machine — an ACL on `/dev/kvm` already grants the user `rw`; the udev rule in `ci.yml`
is for GitHub runners.

`gui` is the mode to reach for, and not only because a window is nice: it renders on the
real GPU, which is *faster* than the headless mode, not slower.

|                 | `start` (headless)     | `gui`              |
| --------------- | ---------------------- | ------------------ |
| renderer        | `swiftshader_indirect` | `-gpu host`        |
| suite runtime   | 116s                   | 50s                |
| emulator RSS    | 3390M                  | 2342M              |
| animations      | 0                      | 1x                 |

Software rendering competes for the same cores the tests run on and costs an extra
gigabyte of buffers. So **`test` boots gui from cold** — `scripts/emulator.sh test
--headless` is the opt-out, and it opts out automatically when neither `DISPLAY` nor
`WAYLAND_DISPLAY` is set (a bare tty, ssh), where a window cannot be drawn at all. An
emulator that is already up keeps the mode it booted in either way.

Headless is still what CI uses, so it is the mode to reproduce in when a test fails only
in the pipeline.

Two things the script exists to get right:

- **`test` pins `ANDROID_SERIAL`.** `connectedDirectDebugAndroidTest` installs on *every*
  connected device, so a phone plugged in beside the emulator runs the whole suite twice.
- **It applies what CI's `disable-animations: true` applies** — all three animation
  scales — plus the `KEYCODE_WAKEUP` above, on every start.

**`test` puts back what it found.** An emulator it started is stopped once the suite ends,
however it ends — failed tests, Ctrl-C — because 2.3G held for the rest of the day is 2.3G
Gradle does not get on an 8G machine. One that was already up in the mode asked for is
left alone: it is not this run's to close. `--keep` opts out.

`-no-window` is fixed at launch, so **the mode you ask for is the mode you get**: asking
for the one the emulator is already in reuses it, asking for the other stops it and boots
it again. The live mode is read off the running process (`-no-window` on its command line),
not off `/tmp/dosmotr-emulator.mode`, so an emulator someone started by hand is classified
correctly too — which is what used to leave `test` silently on swiftshader.

The emulator wants ~2.5G next to Gradle's ~2.8G, which does not fit in 8G. This machine
carries a second swap file (`/swap2.img`, 8G) for exactly that; a run peaks around 5.6G
of swap. Without it the OOM killer takes the build.

## Secrets and signing

`local.properties` holds `backend.url` / `backend.token`, the `donate.*` destinations (see
"Distribution flavours") and the `release.*` signing credentials;
`keystore/dosmotr-release.jks` holds the key. Both are gitignored and exist
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
cosmetic change**: a column rename is a schema change like any other, so it costs a
hand-written entry in `AppDatabase.MIGRATIONS` that copies every existing library across —
and a rename shipped *without* one no longer wipes the library, it fails to open the
database at all. Changing the JSON keys separately breaks importing older backups.

The backend is a **separate project**, not part of this build: `~/projects/dosmotr-backend`
(nginx + Caddy, deployed with Docker Compose). Nothing here depends on it at compile
time — the coupling is the two `local.properties` values, the `X-Backend-Token` header
name, and the `/v1` paths above.

## Distribution flavours

One dimension, `distribution`, with two flavours — **`store`** and **`direct`**. Same
`applicationId`, same signing key: this is one app with two ways of reaching a phone, and
an APK from GitHub Releases updates to a store build without losing the library.

The only difference is `BuildConfig.DONATIONS_ENABLED`, and it is a legal boundary, not a
preference:

- **part 7 of article 14 of 259-ФЗ** forbids not only accepting digital currency as
  consideration but **distributing information about accepting it**;
- **Google Play** requires its own billing for in-app payments, which a Russian account
  cannot use at all, so an external donate button reads as circumventing the payment
  policy;
- **RuStore** requires compliance with Russian law, and arguing the point with moderation
  costs weeks.

So `store` carries no donation UI *and no wallet address*: the `DONATE_URL` / `DONATE_SBP`
/ `DONATE_USDT` fields are compiled in as empty strings there, and `AboutDialogTest`
asserts it. Hiding the block at runtime would not be enough — the string would still ship.

The destinations themselves live in `local.properties` (`donate.url`, `donate.sbp`,
`donate.usdt`), like the backend credentials. Not because a wallet address is secret — it
is public by nature — but because an address baked into a public repository cannot be
changed without a release and is an invitation to swap it in a fork. **Missing values are
a supported state**: `direct` then has nothing to show and hides the block, which is
exactly what CI builds and what a fresh clone gets.

Wording in that block is constrained by `product/09-donations.md` and must not drift:
nothing is given in return (no feature, no badge, no thank-you), no amounts are named, and
it supports *the author*, not the app or a feature. The moment a donation buys something
it stops being a gift.

CI, `release.yml`, `codeql.yml` and both scripts run **`direct` only** — it is the
superset, so testing `store` as well would double every job to cover strictly less code.
`storeRelease` is built when there is a store to upload it to (feature-21).

## Memory budget

`gradle.properties` is tuned for an 8 GB machine (Gradle 1536m, Kotlin daemon 1280m,
`org.gradle.parallel=false`). The OOM killer takes builds out without these. R8 in the
release build is the heaviest step — the `profileable` build type disables it on purpose,
since what makes debug frame times meaningless is `debuggable`, not the missing shrinking.

The budget is the whole machine's, not Gradle's. A k3s server runs here permanently and
holds ~0.6 GB before a single container starts; Firefox and VS Code together take more
than Gradle does. Two rules that follow, both learned the hard way:

- **Never capture unbounded output in a shell.** `x="$(producer | filter)"` buffers
  everything the producer emits in the shell's own heap — one runaway producer reached
  3.4 GB and the OOM killer took the terminal, its Claude Code session and two k3s pods
  with it. Bound it (`| head -c N`) and end the substitution with `true` so the SIGPIPE
  that bounding causes is not fatal under `set -o pipefail`. Probe commands that can loop
  belong under `ulimit -v`.
- **The emulator and a build do not both fit next to a browser.** `scripts/emulator.sh`
  wants ~2.5 GB and Gradle ~2.8 GB; that is what `/swap2.img` exists for, and it still
  peaks around 5.6 GB of swap. Stopping k3s (`sudo systemctl stop k3s`) buys back roughly
  a gigabyte when a run has to fit.

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
open the PR, and hand the merge to GitHub's auto-merge — never run the merge locally.
Afterwards sync with `git pull`, never with a local merge.

### Closing a todo task

`scripts/close-task.sh <task-id>` is the whole flow; do not do the steps by hand:

```bash
scripts/close-task.sh feature-11                       # title = last commit subject
scripts/close-task.sh bug-3 --title "fix: …" --no-wait
scripts/close-task.sh bug-3 --body-file todo/.grind/bug-3.pr.md
```

**The PR description is prose in Russian** — what was wrong, what changed and why, how it
was verified — because the PR page is what gets read later, and a title plus a link to the
spec is not that. `--body-file` is how a session hands its own text over; the `Closes
<spec>` line is appended by the script. Without the flag the body is that link alone.

It refuses to run anywhere but a `feature/**` branch, or with a dirty tree, then:

1. pushes the branch and opens the PR into `develop` (reuses an already-open one), then
   waits until the PR head has check runs, pushing an empty commit if it has none;
2. arms `gh pr merge --auto --merge --delete-branch` and waits for the merge;
3. appends `**PR:** <url>` to `todo/{bugs,features}/<task>.md`;
4. moves that file **and every `<task>.*` / `<task>-*` asset** into `todo/done/`;
5. rewrites the matching `todo/README.md` links to `done/…` and marks the row ✅;
6. checks out `develop`, pulls, and deletes the local feature branch.

**`todo/` is gitignored**, so steps 3–5 are local-only — no commit, nothing pushed, and
in particular nothing that would require a direct push to `develop`. That is also why the
task file is edited *after* the merge rather than inside the PR: the PR would not carry it
anyway. `--no-wait` stops after step 2 and leaves the todo files alone; re-running the
script later picks the same PR back up and finishes.

### Working the whole backlog

`scripts/grind.sh` runs the backlog down to nothing. Per task: pick it, implement it in a
full Claude Code session, open a PR, have a second session review that PR until it passes,
then merge and close it with `close-task.sh`. Repeat.

```bash
scripts/grind.sh                 # asks before each task
scripts/grind.sh --yes           # unattended
scripts/grind.sh --task bug-4    # start here, then carry on picking
scripts/grind.sh --once          # one task, then stop
scripts/grind.sh --no-review     # straight to merge, no review rounds
scripts/grind.sh --rounds 5      # more review rounds before it gives up
scripts/grind.sh --bugs          # only todo/bugs/ (--features, or --only bugs|features)
scripts/grind.sh --in-order      # backlog order instead of the picker's ranking
scripts/grind.sh --model-complex opus --model-simple sonnet --model-review sonnet
```

**Run it from a terminal yourself, not from inside a session** — each task is a real
`claude` process in the foreground of that terminal, so the work is visible as it happens.
No background agent; the script is only the loop.

**Every session is `claude -p`, not an interactive one, and that is not negotiable.** An
interactive session does not exit when the work is done — it returns to its prompt and
waits, so the script above it hangs until someone presses Ctrl-C, and the review and merge
phases never run. Print mode ends on its own. To keep it watchable the sessions stream
`--output-format stream-json --include-partial-messages --verbose` through
`scripts/claude-stream.py`, which renders thinking, tool calls and their results the way an
interactive session shows them. So: watch, do not type. `--text-out` on that filter is also
how the reviewer's reply is captured for the `VERDICT:` line.

**The prompt goes in on stdin, never as a trailing argument.** `--tools` is variadic, so
anything after it — the prompt included — is read as one more tool name, and claude exits
with `Input must be provided either through stdin or as a prompt argument when using
--print`. That is exactly how a review round once produced an empty report, which the loop
then read as "no verdict" and treated as an approval. An empty report is now a failure that
leaves the PR open instead.

Since nobody can answer, the task prompt tells the session to decide open questions itself
and state the assumption — a question there just ends the turn with the task unfinished,
which the phase-1 check then reports as "opened no PR".

- **Picking** is one tool-less `claude -p` call per iteration, fed the open ids and
  `todo/README.md`, answering `<id> <simple|complex>`. It reruns before every task so the
  choice accounts for what the last one changed. An unusable answer, or an id outside the
  list it was given, falls back to backlog order, bugs first.
- **`--bugs` / `--features` narrow the backlog** (`--only bugs|features` is the same thing
  spelled out): the picker only sees that directory, and so does the "nothing left to do"
  check. `--task` still reaches either directory — naming an id explicitly outranks the
  filter.
- **`--in-order` replaces the ranking with backlog order** — bugs before features, then by
  number, so `feature-20` is followed by `feature-21`. Ordering is `sort -V` per directory,
  not `ls`, which would put `feature-9` after `feature-21`. The task is still sized by its
  own one-word `claude -p` call, so the per-job model choice survives; only the "what is
  most valuable" question is dropped.
- **The model is per job, not per run.** The picker and every reviewer are Sonnet; the
  author session is Sonnet on a `simple` task and Opus on a `complex` one — the size comes
  from the same call that picks the task, so sizing costs nothing extra. Anything
  unparseable, and `--task <id>`, count as `complex`: paying for Opus on a small fix is
  cheaper than a schema migration written by the wrong model. The choice is written to
  `todo/.grind/<task>.model` so a resume after a usage limit continues the conversation on
  the model it started on. `--model` forces the author sessions only — **review is Sonnet
  either way**, deliberately, since it reads a diff against a checklist.
- **Review is a separate session, deliberately.** The author session ends its turn at
  `close-task.sh <task> --pr-only` — PR opened, nothing merged. A fresh `claude -p`
  reviewer then reads `git diff origin/develop...HEAD` against the task spec and
  CLAUDE.md's invariants, with read-only tools (it reports, it never fixes), and ends
  with `VERDICT: APPROVE` or `VERDICT: REQUEST_CHANGES`. On request-changes the findings
  are resumed **into the author's existing conversation**, so it fixes with the whole
  context of having written the code; the next round is a new reviewer again, and gets
  the previous round's report to check each point was addressed. Merge is armed only
  after an approve. Three rounds by default, then it asks (`--rounds N` to raise it);
  under `--yes` it gives up and leaves the PR open rather than merging unreviewed work.
  **A reply with no `VERDICT:` line is a failed round, never an approval.** It used to
  count as approve, and that is exactly how PR #14 and PR #23 merged unreviewed: the
  reviewer had hit a usage limit and answered `You've hit your session limit`, which is
  neither empty (so the empty-report guard missed it) nor a verdict. Such a round is
  retried rather than counted — under `--yes`, five times at 15-minute intervals, then the
  PR is left open. Reports are kept as `todo/.grind/<task>.review.<n>.md` and the file is
  deleted before each round, so a stale report cannot be read as this round's answer.
- **Nothing is lost at a usage limit.** Each task gets a fixed `--session-id`, stored in
  `todo/.grind/<task>.session`. When a session ends without the task reaching
  `todo/done/` — limit, Ctrl-C, anything — the script offers resume / wait an hour and
  resume / skip / quit, and resume continues *that* conversation with its whole context.
  `--yes` waits 15 minutes and resumes on its own. Skip and quit both print the
  `claude --resume <id>` needed to come back later.
- **Progress is measured by artefacts, not by claims.** Phase one ends when `gh pr list`
  shows an open PR for the branch, phase three when `todo/done/<task>.md` exists. A
  session that says it finished without either having happened is treated as interrupted.
- Sessions run with `--permission-mode bypassPermissions` — no permission prompts at
  all, which is the point of an unattended loop, and worth knowing since those sessions
  push branches, open PRs and arm auto-merge by themselves. `--mode auto` still stops on
  the risky calls; `--mode acceptEdits` prompts for everything but file edits. The
  task prompt spells out the CLAUDE.md rules: branch off develop, update the fakes with
  the DAO, run unit tests + lint + detekt, never merge locally.

Auto-merge needs **"Allow auto-merge"** in the repository settings (the same setting the
release PRs depend on); without it the script stops and leaves the PR open rather than
merging it another way.

**`--auto` waits for required checks and nothing else.** `develop` is protected with all
seven CI checks required:

```
Decide whether to run · Merge develop into the branch · Lint and detekt · Secret scan
Dependency review · JVM tests · Instrumented tests (API 35)
```

Those are the `github-actions` check names; `Android Lint` and `detekt` also appear on
commits but come from code scanning (`github-advanced-security`) and are deliberately not
required. `strict` is off — a branch does not have to be rebuilt on top of the newest
`develop` before merging, because `sync-develop` already merges `develop` into it. Admins
are not exempt (`enforce_admins` is false, so the setting can still be lifted by hand when
something is genuinely stuck).

Before this was turned on the branch was unprotected, which meant auto-merge landed a PR
the moment it opened — that is how PR #10 merged while lint was still running. If a merge
ever happens instantly again, that is the first thing to check.

Two consequences worth knowing, both of which cost a confused half hour once already:

- **`--delete-branch` removes the branch the instant the merge lands**, and any run still
  in flight for it fails in `actions/checkout` with `fetch ... failed with exit code 1`.
  That failure is bookkeeping, not a broken build — the run on `develop` is the verdict.
- **A required check that never starts blocks the merge forever**, unlike one that fails.
  `sync-develop` pushes its merge commit with the skip-CI marker, and GitHub starts no
  workflow for such a commit — so a PR whose head is that commit would wait out
  `close-task.sh`'s hour and stay open. That is why the script checks for check runs on
  the PR head and pushes an empty commit when there are none.
  **The marker counts anywhere in a commit message, not just in the subject.** A commit
  that merely *mentions* it in prose is skipped too — that is how the first attempt at
  this very change ran no pipeline at all. Write it as "skip-CI marker" in messages.

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

- `app/lint-baseline.xml` (regenerate with `./gradlew :app:updateLintBaselineDirectDebug` —
  the bare `updateLintBaseline` uses whatever AGP considers the default variant, which is
  not the one CI checks). The `lint`
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
  Dependabot alerts appear at all. **Both submit the same filtered graph** —
  `DEPENDENCY_GRAPH_INCLUDE_PROJECTS=^:app$` and
  `DEPENDENCY_GRAPH_INCLUDE_CONFIGURATIONS=^directReleaseRuntimeClasspath$` — for two reasons:
  unfiltered, the graph carries Gradle's own plugin classpath (AGP drags in Bouncy Castle,
  protobuf, netty), so an advisory against a build tool fails a PR over code no user runs;
  and the PR snapshot is diffed against whatever the base branch last submitted, so the two
  filters have to stay identical or the diff fills with packages nobody added.
  All of it needs **Dependency graph enabled** in the repository settings — without it the
  submission fails with "The Dependency graph is disabled for this repository".
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

Three more are **optional**: `DONATE_URL`, `DONATE_SBP`, `DONATE_USDT`. They go into the
same `local.properties` and are what puts the donation block into the published APK —
the Releases build is `direct`, so without them it ships with nothing to show. A release
must not fail over a wallet address, so a missing one is a `::warning::` rather than an
error; that warning is the only sign, and it is worth reading. Setting them does **not**
leak anything into a store build: `store` compiles the fields as empty strings whatever
`local.properties` says (see "Distribution flavours").
