#!/usr/bin/env bash
#
# Local emulator for instrumented tests, so `connectedDebugAndroidTest` does not
# need the phone on USB.
#
# The AVD mirrors the `instrumented` job in .github/workflows/ci.yml: API 35,
# google_apis, x86_64, pixel_6, and the same emulator options. A test that passes
# here should pass there.
#
#   scripts/emulator.sh start    # boot headless, wake the screen, kill animations
#   scripts/emulator.sh gui      # boot in a window, on the host GPU, animations on
#   scripts/emulator.sh stop     # shut it down
#   scripts/emulator.sh status   # is it up, in which mode, and what it costs in RAM
#   scripts/emulator.sh test     # start, then run connectedDebugAndroidTest on it
#
# `start` is idempotent: if the AVD is already booted it just re-applies the
# wake/animation settings and returns.
#
# headless vs gui is fixed at launch (-no-window cannot be toggled on a running
# emulator), so switching means `stop` first. The two also differ deliberately
# beyond the window: gui renders on the real GPU and leaves animations at 1x,
# because watching the nav pill animate is the point of having a window.
#
# Use `test` rather than calling Gradle directly whenever the phone might also be
# plugged in: connectedDebugAndroidTest installs on *every* connected device, so
# an attached phone means the suite runs twice. `test` pins ANDROID_SERIAL to the
# emulator, which is the only thing that stops it.

set -euo pipefail

export JAVA_HOME=${JAVA_HOME:-/home/g3ck0/.local/jdk/jdk-17.0.20+8}
export ANDROID_HOME=${ANDROID_HOME:-/home/g3ck0/Android/Sdk}
export PATH=$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$PATH

AVD_NAME=${AVD_NAME:-dosmotr_ci_35}
BOOT_TIMEOUT=${BOOT_TIMEOUT:-300}

# Headless: same flags as the CI job. Software rendering is deliberate here — with
# -no-window the host GL path is the flaky one, and swiftshader is what the pipeline
# is verified against.
HEADLESS_OPTS=(
  -no-window
  -gpu swiftshader_indirect
  -noaudio
  -no-boot-anim
  -camera-back none
  -no-snapshot
)

# Windowed: for looking at the app by hand. -gpu host hands rendering to the real
# GPU, which swiftshader cannot keep up with once there is a window to paint.
GUI_OPTS=(
  -gpu host
  -camera-back none
  -no-snapshot
)

log() { printf '\033[1;34m==>\033[0m %s\n' "$*"; }
warn() { printf '\033[1;33m!!\033[0m %s\n' "$*" >&2; }
die() { printf '\033[1;31mxx\033[0m %s\n' "$*" >&2; exit 1; }

serial_of_avd() {
  # `adb devices` gives emulator-5554, not the AVD name; ask each one who it is.
  local s
  for s in $(adb devices | awk '/^emulator-/ {print $1}'); do
    if [[ "$(adb -s "$s" emu avd name 2>/dev/null | head -1 | tr -d '\r')" == "$AVD_NAME" ]]; then
      echo "$s"
      return 0
    fi
  done
  return 1
}

check_ram() {
  local avail
  avail=$(awk '/MemAvailable/ {print int($2/1024)}' /proc/meminfo)
  # 2048M AVD + Gradle 1536m + Kotlin daemon 1280m does not fit in less than this.
  if (( avail < 3500 )); then
    warn "only ${avail}M available; this AVD wants ~2.5G and Gradle another ~2.8G."
    warn "close Firefox, or stop k3s (sudo systemctl stop k3s), or the OOM killer picks the winner."
  fi
}

wait_for_boot() {
  local serial=$1 waited=0
  log "waiting for boot (timeout ${BOOT_TIMEOUT}s)"
  adb -s "$serial" wait-for-device
  while [[ "$(adb -s "$serial" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" != "1" ]]; do
    (( waited += 2 ))
    (( waited > BOOT_TIMEOUT )) && die "not booted after ${BOOT_TIMEOUT}s; see $LOG_FILE"
    sleep 2
  done
  log "booted after ${waited}s"
}

prepare_device() {
  local serial=$1 mode=${2:-headless}
  # CLAUDE.md: a dozing screen fails Compose assertions about rendered text.
  adb -s "$serial" shell input keyevent KEYCODE_WAKEUP
  adb -s "$serial" shell wm dismiss-keyguard 2>/dev/null || true
  adb -s "$serial" shell svc power stayon true

  # Animations off is a *test* setting — the CI runner's `disable-animations: true`
  # is exactly these three. In a window you are there to watch the UI, and the nav
  # pill's animated highlight is the thing worth watching, so leave them at 1.
  local scale=0
  [[ "$mode" == gui ]] && scale=1
  adb -s "$serial" shell settings put global window_animation_scale $scale
  adb -s "$serial" shell settings put global transition_animation_scale $scale
  adb -s "$serial" shell settings put global animator_duration_scale $scale

  if [[ "$mode" == gui ]]; then
    log "screen awake, animations at 1x — $serial ready"
  else
    log "screen awake, animations off — $serial ready"
  fi
}

MODE_FILE=/tmp/dosmotr-emulator.mode

cmd_start() {
  local mode=${1:-headless}
  [[ -x "$ANDROID_HOME/emulator/emulator" ]] || die "emulator not installed: sdkmanager --install emulator"
  "$ANDROID_HOME/emulator/emulator" -list-avds | grep -qx "$AVD_NAME" \
    || die "no AVD named $AVD_NAME (see scripts/emulator.sh in the repo for how it was created)"

  local serial
  if serial=$(serial_of_avd); then
    # -no-window is fixed at launch, so an already-running emulator cannot grow a
    # window. Say so instead of silently handing back the wrong mode.
    local running_mode; running_mode=$(cat "$MODE_FILE" 2>/dev/null || echo unknown)
    if [[ "$running_mode" != "$mode" && "$running_mode" != unknown ]]; then
      warn "$AVD_NAME is already running in '$running_mode' mode; the window is decided at launch."
      die "run '$0 stop' first, then '$0 $mode'"
    fi
    log "$AVD_NAME already running on $serial ($running_mode)"
    prepare_device "$serial" "$mode"
    return 0
  fi

  check_ram
  local -a opts
  if [[ "$mode" == gui ]]; then
    opts=("${GUI_OPTS[@]}")
    # The emulator's Qt window goes through XWayland on a Wayland session.
    [[ -z "${DISPLAY:-}" ]] && warn "DISPLAY is empty — a window needs X or XWayland"
  else
    opts=("${HEADLESS_OPTS[@]}")
  fi

  LOG_FILE=$(mktemp -t "dosmotr-emulator-XXXXXX.log")
  log "starting $AVD_NAME ($mode) — log: $LOG_FILE"
  echo "$mode" >"$MODE_FILE"
  nohup "$ANDROID_HOME/emulator/emulator" -avd "$AVD_NAME" "${opts[@]}" \
    >"$LOG_FILE" 2>&1 &

  # The process can die before adb ever sees a device (bad image, no KVM, OOM).
  local pid=$! waited=0
  while ! serial=$(serial_of_avd); do
    kill -0 "$pid" 2>/dev/null || die "emulator exited during startup; log: $LOG_FILE"
    (( waited += 2 ))
    (( waited > BOOT_TIMEOUT )) && die "no device appeared after ${BOOT_TIMEOUT}s; log: $LOG_FILE"
    sleep 2
  done

  wait_for_boot "$serial"
  prepare_device "$serial" "$mode"
}

cmd_stop() {
  local serial
  if serial=$(serial_of_avd); then
    log "stopping $serial"
    adb -s "$serial" emu kill
  else
    log "$AVD_NAME is not running"
  fi
  rm -f "$MODE_FILE"
}

cmd_status() {
  local serial
  if serial=$(serial_of_avd); then
    local rss
    rss=$(ps -eo rss,args | awk '/qemu-system|emulator64/ && !/awk/ {s+=$1} END {print int(s/1024)}')
    log "$AVD_NAME up on $serial — ${rss:-?}M RSS, mode $(cat "$MODE_FILE" 2>/dev/null || echo unknown)"
    adb -s "$serial" shell getprop ro.build.version.sdk | sed 's/^/    API /'
  else
    log "$AVD_NAME is not running"
  fi
  awk '/MemAvailable/ {printf "    %dM host RAM available\n", $2/1024}' /proc/meminfo
}

cmd_test() {
  # Reuse whatever is already up — a windowed emulator runs the suite fine, it is
  # only slower. Starting from cold defaults to headless.
  local mode=headless
  if serial_of_avd >/dev/null 2>&1; then
    mode=$(cat "$MODE_FILE" 2>/dev/null || echo headless)
  fi
  cmd_start "$mode"

  local serial
  serial=$(serial_of_avd) || die "AVD vanished after start"

  # Non-negotiable for the suite, even in gui mode where prepare_device left them on.
  adb -s "$serial" shell settings put global window_animation_scale 0
  adb -s "$serial" shell settings put global transition_animation_scale 0
  adb -s "$serial" shell settings put global animator_duration_scale 0

  local attached
  attached=$(adb devices | awk 'NR>1 && $2=="device"' | wc -l)
  (( attached > 1 )) && log "$attached devices attached — pinning to $serial"

  cd "$(dirname "${BASH_SOURCE[0]}")/.."
  log "connectedDebugAndroidTest on $serial"
  # AGP's finalizer reinstalls debug after the run (see app/build.gradle.kts), so
  # the emulator keeps a build on it either way.
  ANDROID_SERIAL="$serial" ./gradlew connectedDebugAndroidTest "${@}"
}

case "${1:-start}" in
  start) cmd_start headless ;;
  gui) cmd_start gui ;;
  stop) cmd_stop ;;
  status) cmd_status ;;
  test) shift; cmd_test "$@" ;;
  *) die "usage: $0 {start|gui|stop|status|test}" ;;
esac
