#!/usr/bin/env bash
# Work the todo/ backlog down to nothing, one full Claude Code session per task.
#
#   scripts/grind.sh              # pick, work, close, repeat — asks before each task
#   scripts/grind.sh --yes        # unattended: no prompt between tasks
#   scripts/grind.sh --task bug-4 # start from a specific task, then continue as usual
#   scripts/grind.sh --once       # do exactly one task and stop
#
# Each task gets its own interactive session in THIS terminal — you watch it and
# can type into it. The loop is the script; the work is Claude.
#
# Sessions are never thrown away. Every task gets a fixed --session-id, so if a
# run stops for any reason (usage limit, Ctrl-C, closed laptop) `--resume` picks
# it back up with the whole conversation intact. Ids live in todo/.grind/.
set -euo pipefail

cd "$(git rev-parse --show-toplevel)"

STATE_DIR="todo/.grind"
ASK=1
ONCE=0
START_TASK=""
# bypassPermissions: no prompts at all. The sessions push branches, open PRs and
# arm auto-merge on their own, so they run unattended by design; --mode auto is
# the middle ground if you want the risky calls to still stop and ask.
MODE="bypassPermissions"
MODEL=""

while [ $# -gt 0 ]; do
    case "$1" in
        --yes|-y) ASK=0; shift ;;
        --once) ONCE=1; shift ;;
        --task) START_TASK="$2"; shift 2 ;;
        --mode) MODE="$2"; shift 2 ;;
        --model) MODEL="$2"; shift 2 ;;
        -h|--help) sed -n '2,16p' "$0"; exit 0 ;;
        *) echo "unknown option: $1" >&2; exit 2 ;;
    esac
done

die() { echo "grind: $*" >&2; exit 1; }

command -v claude >/dev/null || die "claude CLI not on PATH"
command -v gh >/dev/null || die "gh CLI not on PATH"
[ -d todo ] || die "no todo/ directory here"
mkdir -p "$STATE_DIR"

open_tasks() {
    ls todo/bugs/*.md todo/features/*.md 2>/dev/null | xargs -r -n1 basename | sed 's/\.md$//'
}

task_path() {
    for dir in todo/bugs todo/features; do
        [ -f "$dir/$1.md" ] && { echo "$dir/$1.md"; return 0; }
    done
    return 1
}

# --- pick the next task -------------------------------------------------------
# Claude reads the backlog and names one id. No tools, no edits: this is a
# one-shot text call, cheap enough to run before every task so the choice
# reflects what the previous task already changed.
pick_task() {
    local list picked
    list="$(open_tasks)"
    picked="$(
        {
            echo "Backlog of an Android app. Open task ids, one per line:"
            echo "$list"
            echo
            echo "The index, with severity and a suggested order:"
            echo '```'
            cat todo/README.md 2>/dev/null
            echo '```'
            echo
            echo "Pick the SINGLE most valuable task to do next. Weigh: user-visible"
            echo "damage (data corruption and unreachable actions first), how many"
            echo "users hit it, and cost to fix. Prefer a cheap high-impact fix over"
            echo "an expensive one. Answer with the id ALONE, nothing else."
        } | claude -p --tools "" ${MODEL:+--model "$MODEL"} 2>/dev/null \
          | tr -d '[:space:]`' | head -c 64
    )"
    if [ -n "$picked" ] && task_path "$picked" >/dev/null; then
        echo "$picked"
        return 0
    fi
    # Picker unavailable or hallucinated an id: fall back to backlog order,
    # bugs before features.
    echo "$list" | head -1
}

# --- the prompt each working session starts with ------------------------------
build_prompt() {
    local task="$1" path="$2"
    cat <<EOF
Work task \`$task\` from the backlog. Its spec is \`$path\` — read it first, it
says what to change and usually which files.

Follow CLAUDE.md. In particular:

1. Branch off develop: \`git checkout develop && git pull && git checkout -b feature/<short-name>\`.
2. Implement what the spec asks. If the spec is wrong or incomplete, say so and
   do the sensible thing rather than stopping.
3. Update the tests the change touches — the fakes mirror the DAO, so DAO
   changes mean \`FakeTrackerDao\` too.
4. Verify before committing: \`./gradlew testDebugUnitTest\`, \`./gradlew :app:lintDebug\`
   and \`./gradlew detekt\`. Run \`scripts/emulator.sh test\` when the change is UI
   or DAO. Fix what fails.
5. Commit in English, conventional-commits style.
6. Close it out with \`scripts/close-task.sh $task\`. That opens the PR into
   develop, auto-merges it, records the PR link in the task file, moves the task
   into todo/done/ and returns the checkout to develop.

Do not merge anything locally. Stop and ask me only if a decision is genuinely
mine to make.
EOF
}

# --- one task -----------------------------------------------------------------
run_task() {
    local task="$1" path sid prompt
    path="$(task_path "$task")" || die "no todo/{bugs,features}/$task.md"

    local sid_file="$STATE_DIR/$task.session"
    if [ -f "$sid_file" ]; then
        sid="$(cat "$sid_file")"
        echo "grind: resuming session $sid for $task"
        claude --resume "$sid" ${MODEL:+--model "$MODEL"} --permission-mode "$MODE" || true
    else
        sid="$(python3 -c 'import uuid; print(uuid.uuid4())')"
        echo "$sid" > "$sid_file"
        prompt="$(build_prompt "$task" "$path")"
        echo "grind: starting session $sid for $task"
        claude --session-id "$sid" -n "grind:$task" \
            ${MODEL:+--model "$MODEL"} --permission-mode "$MODE" "$prompt" || true
    fi

    # close-task.sh is what moves the file; that move is the definition of done.
    local attempts=0
    while [ ! -f "todo/done/$task.md" ]; do
        attempts=$((attempts + 1))
        if [ "$ASK" -eq 0 ] && [ "$attempts" -gt 5 ]; then
            echo "grind: $task did not close in 5 unattended attempts — skipping it"
            echo "grind: come back with: claude --resume $sid"
            return 1
        fi
        echo
        echo "grind: $task is not in todo/done/ yet — the session ended early."
        echo "  Usage limit or Ctrl-C? Nothing is lost: 'r' continues the same"
        echo "  conversation from where it stopped."
        if [ "$ASK" -eq 0 ]; then
            echo "grind: --yes, waiting 15 min then resuming"
            sleep 900
            claude --resume "$sid" ${MODEL:+--model "$MODEL"} --permission-mode "$MODE" || true
            continue
        fi
        printf "  [r]esume  [w]ait 1h then resume  [s]kip  [q]uit > "
        read -r answer </dev/tty
        case "$answer" in
            r|R|"") claude --resume "$sid" ${MODEL:+--model "$MODEL"} --permission-mode "$MODE" || true ;;
            w|W) sleep 3600; claude --resume "$sid" ${MODEL:+--model "$MODEL"} --permission-mode "$MODE" || true ;;
            s|S) echo "grind: skipping $task (session kept: claude --resume $sid)"; return 1 ;;
            q|Q) echo "grind: stopping (session kept: claude --resume $sid)"; exit 0 ;;
        esac
    done

    echo "grind: $task closed"
    return 0
}

# --- the loop -----------------------------------------------------------------
SKIPPED=" "
while :; do
    # Skipped tasks stay in todo/, so they have to be filtered out by hand or
    # the loop offers the same one forever.
    REMAINING=""
    for t in $(open_tasks); do
        case "$SKIPPED" in *" $t "*) continue ;; esac
        REMAINING="$REMAINING $t"
    done
    [ -n "${REMAINING# }" ] || { echo "grind: nothing left to do (${SKIPPED# } skipped)"; exit 0; }

    if [ -n "$START_TASK" ]; then
        TASK="$START_TASK"; START_TASK=""
    else
        TASK="$(pick_task)"
        case "$SKIPPED" in *" $TASK "*) TASK="$(printf '%s' "$REMAINING" | awk '{print $1}')" ;; esac
    fi

    echo
    echo "======================================================================"
    echo "grind: next up — $TASK    ($(open_tasks | wc -l) open)"
    sed -n '1p' "$(task_path "$TASK")"
    echo "======================================================================"

    if [ "$ASK" -eq 1 ]; then
        printf "grind: [Enter] start  [o]ther task  [q]uit > "
        read -r answer </dev/tty
        case "$answer" in
            q|Q) exit 0 ;;
            o|O) printf "  task id > "; read -r TASK </dev/tty ;;
        esac
    fi

    run_task "$TASK" || SKIPPED="$SKIPPED $TASK"

    [ "$ONCE" -eq 1 ] && { echo "grind: --once, stopping"; exit 0; }
done
