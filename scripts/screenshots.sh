#!/usr/bin/env bash
#
# Store screenshots, in one command.
#
#   scripts/screenshots.sh              # boot the AVD, shoot all six frames, shut it down
#   scripts/screenshots.sh --keep       # leave the emulator up afterwards
#   scripts/screenshots.sh --no-build   # skip Gradle, use whatever is already installed
#   scripts/screenshots.sh --raw        # keep the uncaptioned frames too
#
# Six frames land in store/screenshots/, captioned, in the order the store shows them —
# the first two are the ones visible in a search result, so they are the library and the
# detail screen and nothing else.
#
# Reshooting has to be cheap, or the screenshots quietly go stale one redesign after
# another. Three things make a run reproducible rather than merely automatic:
#
#   * the status bar is put in SystemUI's demo mode, so every frame carries the same
#     12:00, a full battery and no notification icons — otherwise the six frames disagree
#     about the time and the set looks assembled from different days;
#   * the library is not built by hand but restored from store/demo-library.json through
#     the app's own import, so the same titles sit at the same progress every time;
#   * animations are pinned off for the length of the shoot, so nothing is caught
#     mid-transition, and the AVD is switched to gesture navigation, so the three-button
#     row does not eat the bottom of every frame.
#
# Two runs in a row therefore produce the same images. Demo mode, the navigation mode and
# the night setting are put back on the way out, however the run ends.
#
# The device-side work — seeding and rendering the 512px icon — is StoreAssetsTest, run
# through `am instrument` rather than through Gradle: connectedDirectDebugAndroidTest
# uninstalls both APKs when it finishes and would take the seeded library with it.

set -euo pipefail

export JAVA_HOME=${JAVA_HOME:-/home/g3ck0/.local/jdk/jdk-17.0.20+8}
export ANDROID_HOME=${ANDROID_HOME:-/home/g3ck0/Android/Sdk}
export PATH=$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$PATH

cd "$(dirname "${BASH_SOURCE[0]}")/.."

PKG=com.g3ck0.dosmotr.debug
TEST_PKG=$PKG.test
ACTIVITY=$PKG/com.g3ck0.seriestracker.MainActivity
RUNNER=androidx.test.runner.AndroidJUnitRunner
OUT_DIR=store/screenshots
RAW_DIR=$(mktemp -d -t dosmotr-shots-XXXXXX)

KEEP=0
BUILD=1
KEEP_RAW=0
while [[ $# -gt 0 ]]; do
  case "$1" in
    --keep) KEEP=1; shift ;;
    --no-build) BUILD=0; shift ;;
    --raw) KEEP_RAW=1; shift ;;
    *) echo "usage: $0 [--keep] [--no-build] [--raw]" >&2; exit 2 ;;
  esac
done

log() { printf '\033[1;34m==>\033[0m %s\n' "$*"; }
warn() { printf '\033[1;33m!!\033[0m %s\n' "$*" >&2; }
die() { printf '\033[1;31mxx\033[0m %s\n' "$*" >&2; exit 1; }

# --- the emulator ------------------------------------------------------------------

# Whose emulator is this? One that was already up is left up, the same rule emulator.sh
# test follows: 2.3G held for the rest of the day is 2.3G Gradle does not get.
OWN=1
if scripts/emulator.sh serial >/dev/null 2>&1; then OWN=0; fi
(( KEEP )) && OWN=0

log "emulator"
scripts/emulator.sh gui >&2
SERIAL=$(scripts/emulator.sh serial) || die "no emulator"
export ANDROID_SERIAL="$SERIAL"
log "shooting on $SERIAL"

dev() { adb -s "$SERIAL" shell "$@"; }

restore() {
  dev cmd uimode night no >/dev/null 2>&1 || true
  dev cmd overlay enable com.android.internal.systemui.navbar.threebutton >/dev/null 2>&1 || true
  dev am broadcast -a com.android.systemui.demo -e command exit >/dev/null 2>&1 || true
  dev settings put global sysui_demo_allowed 0 >/dev/null 2>&1 || true
  # gui mode runs at 1x; emulator.sh sets that on every start, so put it back.
  local s
  for s in window_animation_scale transition_animation_scale animator_duration_scale; do
    dev settings put global "$s" 1 >/dev/null 2>&1 || true
  done
  (( KEEP_RAW )) || rm -rf "$RAW_DIR"
  if (( OWN )); then
    log "stopping the emulator this run started"
    scripts/emulator.sh stop >&2 || true
  fi
}
trap restore EXIT

# --- the build ---------------------------------------------------------------------

if (( BUILD )); then
  log "installing directDebug and its test APK"
  ./gradlew installDirectDebug installDirectDebugAndroidTest >&2
fi

# --- a device that looks the same on every frame -------------------------------------

# Gesture navigation, not the three buttons the AVD ships with: the back/home/recents row
# eats 130 px at the bottom of every frame and dates the screenshot to no phone in
# particular. First, because switching it restarts SystemUI — and a restart drops demo
# mode, which is how six frames ended up stamped with the wall clock. Put back in restore().
log "gesture navigation"
dev cmd overlay enable com.android.internal.systemui.navbar.gestural >/dev/null 2>&1 || true
sleep 5

log "demo status bar"
dev settings put global sysui_demo_allowed 1
demo() { dev am broadcast -a com.android.systemui.demo "$@" >/dev/null; }
demo -e command enter
demo -e command clock -e hhmm 1200
demo -e command battery -e level 100 -e plugged false
demo -e command network -e wifi show -e level 4
demo -e command network -e mobile show -e datatype none -e level 4
demo -e command notifications -e visible false

for s in window_animation_scale transition_animation_scale animator_duration_scale; do
  dev settings put global "$s" 0
done
dev cmd uimode night no >/dev/null
dev input keyevent KEYCODE_WAKEUP
dev wm dismiss-keyguard 2>/dev/null || true

# --- driving the app -----------------------------------------------------------------

# Compose publishes its semantics to accessibility, so the hierarchy dump is the honest
# way to find a target: tapping remembered coordinates breaks silently the first time a
# padding changes, and the frame is then of the wrong screen.
#
# Three kinds of match: text, desc and class. The last one exists for the search field,
# which carries neither — a Compose placeholder is drawn text, not the node's value, so
# an empty field is an EditText with nothing to grep for. It is also the only EditText on
# that screen, which makes the class specific enough.
node_center() {
  local kind=$1 value=$2 xml=$RAW_DIR/window.xml
  dev uiautomator dump /sdcard/window.xml >/dev/null 2>&1
  adb -s "$SERIAL" pull /sdcard/window.xml "$xml" >/dev/null 2>&1
  python3 - "$kind" "$value" "$xml" <<'PY'
import re, sys
kind, value, path = sys.argv[1], sys.argv[2], sys.argv[3]
attr = {'desc': 'content-desc', 'class': 'class'}.get(kind, 'text')
best = None
for node in re.finditer(r'<node [^>]*>', open(path, encoding='utf-8').read()):
    tag = node.group(0)
    m = re.search(attr + r'="([^"]*)"', tag)
    if not m or m.group(1) != value:
        continue
    b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', tag)
    if not b:
        continue
    x1, y1, x2, y2 = map(int, b.groups())
    # The topmost match wins: a title appears both on the card and, later, in a header.
    if best is None or y1 < best[0]:
        best = (y1, (x1 + x2) // 2, (y1 + y2) // 2)
if best:
    print(best[1], best[2])
PY
}

tap() {
  local kind=$1 value=$2 xy
  xy=$(node_center "$kind" "$value") || true
  [[ -n "$xy" ]] || die "nothing on screen with $kind=\"$value\""
  # shellcheck disable=SC2086
  dev input tap $xy
  sleep 1
}

on_screen() {
  local kind=$1 value=$2
  [[ -n "$(node_center "$kind" "$value")" ]]
}

launch() {
  dev am start -W -n "$ACTIVITY" >/dev/null
  sleep 3
}

shot() {
  local name=$1
  adb -s "$SERIAL" exec-out screencap -p >"$RAW_DIR/$name.png"
  log "  $name"
}

# --- 3. first launch, empty library, trending ----------------------------------------
#
# Shot out of order on purpose: it is the only frame that needs an empty library, and
# wiping the app after seeding would mean seeding twice.

log "first launch"
dev pm clear "$PKG" >/dev/null
# pm clear revokes runtime grants too. Granting it keeps the in-app "разрешить
# уведомления" card off the frame — it is a prompt, not a feature to advertise.
dev pm grant "$PKG" android.permission.POST_NOTIFICATIONS >/dev/null 2>&1 || true
launch
sleep 4   # trending comes off the network, and an empty grid is not the frame we want
shot 03-trending

# --- seed --------------------------------------------------------------------------

log "seeding the demo library"
dev am force-stop "$PKG"
# The app's own files dir, because /data/local/tmp is not readable by an app uid. run-as
# works on a debuggable build and needs no permission on either side.
adb -s "$SERIAL" push store/demo-library.json /data/local/tmp/dosmotr-demo.json >/dev/null
dev "cat /data/local/tmp/dosmotr-demo.json | run-as $PKG sh -c 'cat > files/demo-library.json'"

instrument() {
  local out
  out=$(adb -s "$SERIAL" shell am instrument -w "$@" "$TEST_PKG/$RUNNER" 2>&1 | head -c 20000) || true
  grep -q 'OK (' <<<"$out" || { printf '%s\n' "$out" >&2; die "instrumentation failed"; }
}

instrument -e class com.g3ck0.seriestracker.StoreAssetsTest#seedsTheDemoLibrary \
  -e demoLibrary "/data/data/$PKG/files/demo-library.json"

# --- 1, 2, 4, 5, 6 -------------------------------------------------------------------

log "library"
launch
shot 01-library

log "detail"
tap text "Разделение"
sleep 1
tap text "Серии"
sleep 1
# Seasons start collapsed, so the episodes with their ticks are one tap further in. If a
# future default opens them, the tap would close one instead — hence the check.
if ! on_screen text "1 серия"; then
  tap text "Сезон 1"
  sleep 1
fi
shot 02-detail
dev input keyevent KEYCODE_BACK
sleep 1

log "stats"
tap desc "Статистика"
sleep 1
shot 04-stats

log "search"
tap desc "Поиск"
sleep 1
tap class android.widget.EditText
sleep 1
# The query is Latin because there is no way to type Cyrillic here: `input text` maps
# characters through the US layout and dies with a NullPointerException on anything
# outside it, and this system image has no `cmd clipboard` to paste with. It costs
# nothing in the frame — the catalogue is pinned to ru-RU on the backend, so "dune"
# comes back as «Дюна» and «Дюна: Часть вторая», and only the query line is Latin.
dev input text "dune"
sleep 4
if ! on_screen desc "Дюна: Часть вторая"; then
  sleep 4   # a cold backend answers the first query slower than the rest
  on_screen desc "Дюна: Часть вторая" || warn "no search results in the frame — check 05"
fi
dev input keyevent KEYCODE_BACK   # drop the keyboard, keep the results
sleep 1
shot 05-search

log "dark theme"
tap desc "Моё" 2>/dev/null || dev input keyevent KEYCODE_BACK
sleep 1
dev cmd uimode night yes >/dev/null
sleep 3
shot 06-dark
dev cmd uimode night no >/dev/null

# --- the 512px icon ------------------------------------------------------------------

log "launcher icon"
instrument -e class com.g3ck0.seriestracker.StoreAssetsTest#rendersTheLauncherIcon \
  -e iconOut "/data/data/$PKG/files/icon-512.png"
adb -s "$SERIAL" exec-out run-as "$PKG" cat files/icon-512.png >store/icon-512.png
[[ -s store/icon-512.png ]] || die "the icon came back empty"

# --- captions and the feature graphic --------------------------------------------------

log "captions and feature graphic"
mkdir -p "$OUT_DIR"
python3 scripts/store-graphics.py --raw "$RAW_DIR" --out "$OUT_DIR" \
  --icon store/icon-512.png --feature store/feature-graphic.png

# Left where they were shot, deliberately not copied into store/screenshots: that
# directory is the set that gets uploaded, and an uncaptioned twin of every frame in it
# is how the wrong six end up in a console.
(( KEEP_RAW )) && log "uncaptioned frames left in $RAW_DIR"

log "done — $(ls "$OUT_DIR" | wc -l) files in $OUT_DIR"
