#!/usr/bin/env bash
#
# Build an APK from a branch and install it on the phone over USB.
#
#   scripts/install-phone.sh                 # debug, develop
#   scripts/install-phone.sh feature/foo     # that branch
#   scripts/install-phone.sh --production    # the signed release build instead
#   scripts/install-phone.sh --no-fetch v1   # skip the fetch/fast-forward
#   scripts/install-phone.sh --keep-branch   # stay on the built branch afterwards
#   scripts/install-phone.sh --serial XXX    # when more than one phone is plugged in
#
# --production is «Досмотр» (com.g3ck0.dosmotr), signed with the real key and with
# R8 on; without it, «Досмотр debug» (com.g3ck0.dosmotr.debug). Different app ids,
# so both live on the phone at once and neither replaces the other.
#
# Three things this exists to get right:
#
# - ANDROID_SERIAL is pinned to a *physical* device. `installDebug` installs on
#   every connected device, so with the emulator up the APK also lands there —
#   and an emulator-only run silently installs nothing on the phone.
# - The branch you were on is restored when the build ends, however it ends.
#   The checkout is refused outright on a dirty tree rather than stashing, since
#   a stash dropped by a failed run is work nobody gets back.
# - The release signing credentials are checked *before* the build. Without
#   `release.storeFile` in local.properties the release variant still builds, it
#   just comes out unsigned — and adb then rejects it after R8 has already run
#   for several minutes.

set -euo pipefail

export JAVA_HOME=${JAVA_HOME:-/home/g3ck0/.local/jdk/jdk-17.0.20+8}
export ANDROID_HOME=${ANDROID_HOME:-/home/g3ck0/Android/Sdk}
export PATH=$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$PATH

REPO_ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
cd "$REPO_ROOT"

BRANCH=""
SERIAL=${ANDROID_SERIAL:-}
FETCH=1
KEEP_BRANCH=0
PRODUCTION=0

while (($#)); do
  case "$1" in
    --serial) SERIAL=${2:?--serial needs a device serial}; shift 2 ;;
    --no-fetch) FETCH=0; shift ;;
    --keep-branch) KEEP_BRANCH=1; shift ;;
    --production|--release) PRODUCTION=1; shift ;;
    -h|--help) sed -n '2,28p' "${BASH_SOURCE[0]}" | sed 's/^# \?//'; exit 0 ;;
    -*) echo "unknown option: $1" >&2; exit 2 ;;
    *) [[ -n $BRANCH ]] && { echo "only one branch, got '$BRANCH' and '$1'" >&2; exit 2; }
       BRANCH=$1; shift ;;
  esac
done
BRANCH=${BRANCH:-develop}

if ((PRODUCTION)); then
  GRADLE_TASK=installRelease
  PACKAGE=com.g3ck0.dosmotr
  VARIANT="release (signed, R8)"
else
  GRADLE_TASK=installDebug
  PACKAGE=com.g3ck0.dosmotr.debug
  VARIANT=debug
fi

die() { echo "error: $*" >&2; exit 1; }

# --- release credentials ---------------------------------------------------

if ((PRODUCTION)); then
  props=local.properties
  [[ -f $props ]] || die "no local.properties — the release build cannot be signed"
  for key in release.storeFile release.storePassword release.keyAlias release.keyPassword; do
    grep -q "^[[:space:]]*${key}[[:space:]]*=" "$props" ||
      die "$key is missing from local.properties — the release APK would come out unsigned and adb would refuse it"
  done
  store=$(sed -n "s/^[[:space:]]*release\.storeFile[[:space:]]*=[[:space:]]*//p" "$props" | tail -1)
  [[ -f $store ]] || die "keystore not found: $store (release.storeFile in local.properties)"
fi

# --- the phone -------------------------------------------------------------

adb start-server >/dev/null 2>&1 || die "adb is not on PATH (ANDROID_HOME=$ANDROID_HOME)"

if [[ -z $SERIAL ]]; then
  mapfile -t devices < <(adb devices | awk '$2 == "device" && $1 !~ /^emulator-/ { print $1 }')
  case ${#devices[@]} in
    0)
      unauthorized=$(adb devices | awk '$2 == "unauthorized" { print $1 }')
      [[ -n $unauthorized ]] && die "phone $unauthorized is unauthorized — confirm the USB debugging prompt on it"
      die "no phone over USB. Plug it in, enable USB debugging, check \`adb devices\`"
      ;;
    1) SERIAL=${devices[0]} ;;
    *) die "more than one phone attached (${devices[*]}) — pick one with --serial" ;;
  esac
fi

model=$(adb -s "$SERIAL" shell getprop ro.product.model 2>/dev/null | tr -d '\r') || true
echo "device : $SERIAL${model:+ ($model)}"
export ANDROID_SERIAL=$SERIAL

# --- the branch ------------------------------------------------------------

ORIGINAL=$(git symbolic-ref --quiet --short HEAD || git rev-parse --short HEAD)
RESTORE=0

restore() {
  ((RESTORE)) || return 0
  echo "back to $ORIGINAL"
  git checkout --quiet "$ORIGINAL"
}
trap restore EXIT

((FETCH)) && git fetch --quiet origin

if [[ $(git rev-parse --abbrev-ref HEAD) != "$BRANCH" ]]; then
  [[ -n $(git status --porcelain) ]] &&
    die "working tree is dirty — commit or stash before switching to $BRANCH"

  if git show-ref --quiet --verify "refs/heads/$BRANCH"; then
    git checkout --quiet "$BRANCH"
  elif git show-ref --quiet --verify "refs/remotes/origin/$BRANCH"; then
    git checkout --quiet -b "$BRANCH" --track "origin/$BRANCH"
  else
    die "no such branch: $BRANCH (neither local nor on origin)"
  fi
  ((KEEP_BRANCH)) || RESTORE=1
fi

# Fast-forward only: a branch that has diverged is a merge decision, not this
# script's to make.
if ((FETCH)) && git show-ref --quiet --verify "refs/remotes/origin/$BRANCH"; then
  git merge --quiet --ff-only "origin/$BRANCH" 2>/dev/null ||
    echo "note: $BRANCH is not a fast-forward of origin/$BRANCH — building what is checked out"
fi

echo "branch : $BRANCH @ $(git rev-parse --short HEAD) — $(git log -1 --pretty=%s)"
echo "variant: $VARIANT → $PACKAGE"

# --- build and install -----------------------------------------------------

./gradlew "$GRADLE_TASK"

adb -s "$SERIAL" shell monkey -p "$PACKAGE" \
  -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1 ||
  echo "note: installed, but could not launch it — start the app by hand"

echo "installed $PACKAGE on $SERIAL"
