# Сборка, тесты, эмулятор

Читать, когда что-то не собирается, когда гоняешь тесты или когда прогон
упирается в память. Инварианты кода — в `ai-docs/architecture.md`.

## Commands

**Every task name carries a flavour.** `store` and `direct` (see
`ai-docs/distribution-and-legal.md`) multiply the variants, so `installDebug`, `testDebugUnitTest` and
`connectedDebugAndroidTest` no longer exist — Gradle fails with "task not found" rather
than picking one. `direct` is the default everywhere: it is the superset, since the
donation block is compiled into it and absent from `store`.

```bash
./gradlew installDirectDebug     # «Досмотр debug», com.g3ck0.dosmotr.debug
./gradlew installStoreDebug      # same app id — replaces the above on the device
./gradlew installDirectRelease   # «Досмотр», com.g3ck0.dosmotr — signed, R8 on
./gradlew installDirectProfileable    # for performance measurement only

./gradlew testDirectDebugUnitTest     # 153 JVM tests, no device needed
./gradlew testDirectDebugUnitTest --tests "com.g3ck0.seriestracker.LabelsTest"
./gradlew testStoreDebugUnitTest      # the other flavour, same tests

./gradlew connectedDirectDebugAndroidTest   # 196 tests, needs a device
./gradlew connectedDirectDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.g3ck0.seriestracker.ui.StatsContentTest

scripts/emulator.sh gui          # local AVD in a window — the fast path, see «Local emulator»
scripts/emulator.sh test         # start on the GPU, run the suite, shut it down again
scripts/emulator.sh test --headless   # the swiftshader path CI uses, for reproducing it
scripts/emulator.sh test --keep       # leave it up afterwards
FLAVOR=store scripts/emulator.sh test  # the store variant instead of direct

scripts/screenshots.sh           # the six store frames, the 512px icon, the feature graphic
scripts/screenshots.sh --no-build --keep   # reshoot on what is already installed

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

## Local emulator

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
