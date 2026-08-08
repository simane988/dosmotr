# Флейворы, право, телеметрия, материалы для сторов

Читать, когда трогаешь `store`/`direct`, блок донатов, телеметрию, Firebase
или тексты листинга. Здесь границы, за которые нельзя: 259-ФЗ, правила Play и
RuStore, обещание «библиотека остаётся на устройстве».

## Distribution flavours

One dimension, `distribution`, with two flavours — **`store`** and **`direct`**. Same
`applicationId`, same signing key: this is one app with two ways of reaching a phone, and
an APK from GitHub Releases updates to a store build without losing the library.

Two `BuildConfig` flags differ, `DONATIONS_ENABLED` and `CRASH_REPORTING_AVAILABLE` (see
«Crash reporting» below). The first is a legal boundary, not a preference:

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

## Crash reporting

Firebase Crashlytics and Analytics are compiled into **`store` only**, behind the
`Telemetry` interface (`data/telemetry/`). `FirebaseTelemetry` lives in `app/src/store`,
`NoopTelemetry` in `app/src/direct`, and the Hilt binding is a `TelemetryModule` in each
flavour source set — it cannot live in `AppModule`, since only one of the two is ever
compiled. Everything above the interface (ViewModels, `AutoBackupManager`) is flavour-blind.

`direct` carries **no Google code at all**: that is an F-Droid rule and the "никакой
слежки" promise in `product/02-positioning.md`. The check is
`./gradlew :app:dependencies --configuration directReleaseRuntimeClasspath`, which must
print nothing matching `com.google.firebase` — it currently prints 56 such lines for
`storeReleaseRuntimeClasspath` and 0 for `direct`.

**What may be reported is a closed list**, `TelemetryEvent`: eleven event names and exactly
two parameter values, `tv` and `movie`. Titles, search queries, notes, ratings, timestamps
and counts of anything are forbidden — not as a matter of taste but because the store
description, «О приложении» and the privacy policy all promise the library stays on the
device, and Data Safety in the Play Console declares the same. `telemetryAllows()` is
applied inside `FirebaseTelemetry` too, so a bad call is dropped rather than sent, and
`FakeTelemetry` throws on one (see `ai-docs/architecture.md`). Adding a twelfth event means editing
`TelemetryEvent.ALL` *and* the count assertion in `TelemetryTest`.

**`google-services.json` goes in `app/src/store/` and is committed** — it is configuration,
not a secret; its keys are bound to the package name and signing certificate. Three things
about it that cost time to find out:

- **A build without it must keep working.** CI has no secrets, and neither does a fork or a
  fresh clone. So the two Gradle plugins are applied only `if (firebaseConfig.exists())`,
  and `FirebaseTelemetry` checks `FirebaseApp.getApps()` before touching anything —
  otherwise every reported event would throw on a build that has no Firebase project.
- **It needs a client entry per applicationId, suffixes included.** The plugin does not
  strip `applicationIdSuffix`: with only `com.g3ck0.dosmotr` in the file,
  `assembleStoreDebug` fails with *"No matching client found for package name
  'com.g3ck0.dosmotr.debug'"*. The Firebase project therefore needs three Android apps —
  `com.g3ck0.dosmotr`, `.debug` and `.profileable`.
- **The plugins are applied to the module, not to a flavour**, so they also create tasks
  for the `direct` variants, which have no config to read. `app/build.gradle.kts` disables
  every task whose name contains `Direct` and `GoogleServices`/`Crashlytics`; without that,
  a machine that *has* the file cannot build `direct` at all.

`storeRelease` uploads its R8 mapping file to Crashlytics (`uploadCrashlyticsMappingFile…`,
on by default), which is what keeps shipped stack traces readable — it needs the real
Firebase project and network at build time. The same task is disabled for `direct`.

The switch is «Отправлять отчёты о падениях» in «О приложении», stored in `SettingsStore`
and applied by `CrashReporting`. Collection starts **off** in `app/src/store/AndroidManifest.xml`
and is switched on from the stored setting by `CrashReporting.sync()` on every app start —
the other way round, a build would report once before the setting had been read. That
manifest also switches off ad-id and Android-id collection, which are exactly the stable
cross-app identifiers the feature forbids.

## Store materials

`store/` is the source of the listing, not a copy of it: `listing-ru.md` and
`listing-en.md` (a draft — the app is Russian-only until stage 2), the six screenshots,
`icon-512.png`, `feature-graphic.png` and `demo-library.json`. The consoles keep no
history, so a line changed there and not here is a line nobody can explain later.

`scripts/screenshots.sh` shoots all of it. Three things it does that a by-hand pass does
not: SystemUI demo mode, so every frame carries the same 12:00 and a full battery; the
library restored from `demo-library.json` through the app's own
`BackupRepository.importFromJson`, so the same titles sit at the same progress; and
animations pinned off. Two runs produce the same images. Targets are found by dumping the
accessibility hierarchy rather than by remembered coordinates — Compose publishes its
semantics, and a coordinate breaks silently the first time a padding changes.

Both device-side steps live in `StoreAssetsTest`, which is **skipped unless
`am instrument` passes `-e demoLibrary` / `-e iconOut`** — `connectedDirectDebugAndroidTest`
runs it in CI and it does nothing. It is not run through Gradle because that task
uninstalls both APKs when it finishes and would take the seeded library with it.

Three constraints on the texts, all of which cost a rejection if broken:

- **No donations, no wallet address, not even a hint.** The store build compiles the
  destinations out (see «Distribution flavours» above); part 7 of article 14 of 259-ФЗ forbids
  distributing information about accepting digital currency, and Play reads an external
  donate link as circumventing its payment policy. The listing is one more place the
  ban applies.
- **Nothing the build does not do.** Notifications are stage 1: the permission is asked
  for, the reminders do not exist, so they must not be described.
- **The TMDB sentence, verbatim and in English**, at the end of the full description —
  the same string as `TMDB_DISCLAIMER` in `AboutDialog`.

The Google Play title is capped at 30 characters, which «Досмотр — трекер сериалов и
фильмов» (35) does not fit; Play gets «Досмотр: трекер сериалов» and RuStore the full
one. The listing links to the site and the privacy policy from feature-19, so it cannot
be filled in until those pages are published.
