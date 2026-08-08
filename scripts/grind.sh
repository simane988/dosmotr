#!/usr/bin/env bash
# Work the todo/ backlog down to nothing, one full Claude Code session per task.
#
#   scripts/grind.sh              # pick, work, close, repeat — asks before each task
#   scripts/grind.sh --yes        # unattended: no prompt between tasks
#   scripts/grind.sh --task bug-4 # start from a specific task, then continue as usual
#   scripts/grind.sh --once       # do exactly one task and stop
#   scripts/grind.sh --no-review  # skip the review rounds, merge straight away
#   scripts/grind.sh --rounds 5   # allow more review rounds before asking
#   scripts/grind.sh --bugs       # only todo/bugs/, ignore the features
#   scripts/grind.sh --features   # only todo/features/, ignore the bugs
#   scripts/grind.sh --in-order   # backlog order, not the picker's idea of value
#   scripts/grind.sh --model-complex opus --model-simple sonnet
#   scripts/grind.sh --effort-author max --effort-review high
#   scripts/grind.sh --escalate-after 3   # or --no-escalate
#   scripts/grind.sh --autocompact 150k   # or 'auto' (claude decides) or 'off'
#   scripts/grind.sh --no-hotkey  # do not read the keyboard while a task runs
#
# By default Claude picks the next task by value. --in-order takes them in
# backlog order instead — bugs before features, then by number (feature-20,
# feature-21, …), which is what you want when the specs build on each other.
# The task is still sized simple/complex, so the model choice is unaffected.
#
# Models are per job, and there are four jobs:
#
#   pick    haiku    which id next — a ranking over a list already in the prompt
#   size    sonnet   simple or complex, i.e. which model writes the code
#   author  sonnet on simple, opus on complex
#   review  sonnet   always; reading a diff against a checklist
#
# Sizing leans towards `simple` on purpose and is not left to the cheapest
# model, because that one word is what a task costs. When it guesses low, two
# review rounds ending in REQUEST_CHANGES hand the author session to Opus
# mid-conversation (--escalate-after, --no-escalate). --model forces the author
# sessions and switches escalation off with them.
#
# Effort (`claude --effort`) is per job too: `high` for the author and the
# reviewer, `low` for the picker. --effort-author, --effort-review,
# --effort-pick, or --effort for all three. low | medium | high | xhigh | max.
#
# Long author sessions summarise themselves at --autocompact tokens (100k by
# default) instead of carrying the whole conversation into every turn.
#
# Each task gets its own session in THIS terminal, printed live — thinking, tool
# calls and results, as they happen. The sessions are non-interactive on purpose:
# an interactive one never returns control to the loop, it waits at its prompt
# after the work is done. So watch, do not type; Ctrl-C stops the run and the
# session id is kept, so `--resume` picks the conversation back up.
#
# Per task: the author session implements it and opens a PR without merging, a
# fresh reviewer session reads the diff, and its findings are resumed back into
# the author's conversation. That repeats until the reviewer approves, and only
# then is the merge armed.
#
# Sessions are never thrown away. Every task gets a fixed --session-id, so if a
# run stops for any reason (usage limit, Ctrl-C, closed laptop) `--resume` picks
# it back up with the whole conversation intact. Ids live in todo/.grind/.
#
# A usage limit is waited out to the minute, not guessed at: the session says
# when the limit lifts, scripts/claude-stream.py parses that out, and the loop
# sleeps until then plus a two-minute cushion. Only when nothing said a time is
# there a fixed fallback interval.
#
# Every line the script prints is timestamped, and so is every event inside a
# session (tool call, tool result, end of run) — a backlog run lasts hours and
# the log is read afterwards to find out where they went.
#
# Press `f` while a task is running to stop after it: the task in hand is
# finished — reviewed, merged, closed — and the loop then exits instead of
# picking the next one. `c` cancels the request while the task is still going.
# Same thing without a keyboard: `touch todo/.grind/stop`.
set -euo pipefail

cd "$(git rev-parse --show-toplevel)"

STATE_DIR="todo/.grind"
ASK=1
ONCE=0
START_TASK=""
SCOPE="all"         # all | bugs | features — which directories count as the backlog
ORDER=0             # 1: backlog order instead of the picker's ranking
# bypassPermissions: no prompts at all. The sessions push branches, open PRs and
# arm auto-merge on their own, so they run unattended by design; --mode auto is
# the middle ground if you want the risky calls to still stop and ask.
MODE="bypassPermissions"
REVIEW=1
MAX_ROUNDS=3
HOTKEY=1            # read single keys off /dev/tty while a task runs

# Usage limits. The reset time comes out of the session itself; these are the
# guard rails around it: a cushion, because a limit that lifts "at 15:00" is not
# reliably lifted at 15:00:00; a fallback for a session that died without saying
# anything; and a ceiling, so a misparsed timestamp cannot park the loop for a
# week.
LIMIT_BUFFER=120
LIMIT_FALLBACK=900
LIMIT_MAX_WAIT=$((6 * 3600))

# Model per job, not one model for everything: the author of a fiddly change is
# the only session worth Opus tokens. Picking a task is one short answer, and a
# review is reading a diff against a checklist — Sonnet does both, at a fraction
# of the cost of a backlog worked end to end on Opus.
MODEL_SIMPLE="sonnet"
MODEL_COMPLEX="opus"
MODEL_REVIEW="sonnet"
# Picking is a closed question over a list that is already in the prompt: which
# of these ids first. Haiku answers it, and the worst it can do is a suboptimal
# order — nothing downstream depends on being right.
MODEL_PICK="haiku"
# Sizing is a different question wearing the same clothes, and it is NOT Haiku's.
# Measured on four real specs, Haiku disagreed with Sonnet on two, in the
# expensive direction: it sized `feature-13`, "настоящие миграции Room instead of
# fallbackToDestructiveMigration", as `simple`. The word decides which model
# writes the code, so a wrong `simple` costs two failed review rounds and the
# escalation's re-cache — far more than the call it saved.
MODEL_SIZE="sonnet"
MODEL=""            # --model: forces every author session, review excluded
AUTHOR_MODEL="$MODEL_COMPLEX"   # what run_task settled on for the task in hand

# How hard each session thinks (`claude --effort`), per job like the model.
# `high` everywhere by default: the author is writing into a codebase whose
# invariants are expensive to break, and the reviewer is the only thing standing
# between a wrong diff and `develop`. Lower them per job when a backlog of small
# fixes does not need it — `--effort` sets all three at once.
EFFORT_AUTHOR="high"
EFFORT_REVIEW="high"
# Where a Sonnet author is handed to Opus: after this many rounds have ended in
# REQUEST_CHANGES. 0 disables it. Two is deliberate — the switch invalidates the
# conversation's prompt cache (a different model is a different cache), so the
# whole history is re-written at 1.25x, and the later it happens the more there
# is of it.
ESCALATE_AFTER=2
# Where a long author session starts summarising itself instead of carrying the
# whole conversation into every turn. Measured on this project: sessions peak at
# 200–300k tokens of context and auto-compaction never fired on its own, so the
# last turns of a long task each cost a quarter of a million tokens to re-read.
AUTOCOMPACT="100000"
# The picker answers two words. Measured: the same call at `low` and at `high`
# returns the same id, and thinking is billed as output — the most expensive
# token there is. It is the one job where effort buys nothing.
EFFORT_PICK="low"

while [ $# -gt 0 ]; do
    case "$1" in
        --yes|-y) ASK=0; shift ;;
        --once) ONCE=1; shift ;;
        --task) START_TASK="$2"; shift 2 ;;
        --no-review) REVIEW=0; shift ;;
        --rounds) MAX_ROUNDS="$2"; shift 2 ;;
        --bugs) SCOPE="bugs"; shift ;;
        --features) SCOPE="features"; shift ;;
        --only) SCOPE="$2"; shift 2 ;;
        --in-order|--sequential) ORDER=1; shift ;;
        --no-hotkey) HOTKEY=0; shift ;;
        --mode) MODE="$2"; shift 2 ;;
        --model) MODEL="$2"; shift 2 ;;
        --model-simple) MODEL_SIMPLE="$2"; shift 2 ;;
        --model-complex) MODEL_COMPLEX="$2"; shift 2 ;;
        --model-review) MODEL_REVIEW="$2"; shift 2 ;;
        --model-pick) MODEL_PICK="$2"; shift 2 ;;
        --model-size) MODEL_SIZE="$2"; shift 2 ;;
        --effort) EFFORT_AUTHOR="$2"; EFFORT_REVIEW="$2"; EFFORT_PICK="$2"; shift 2 ;;
        --effort-author) EFFORT_AUTHOR="$2"; shift 2 ;;
        --effort-review) EFFORT_REVIEW="$2"; shift 2 ;;
        --effort-pick) EFFORT_PICK="$2"; shift 2 ;;
        --escalate-after) ESCALATE_AFTER="$2"; shift 2 ;;
        --no-escalate) ESCALATE_AFTER=0; shift ;;
        --autocompact) AUTOCOMPACT="$2"; shift 2 ;;
        -h|--help) sed -n '2,/^set -/{/^set -/!p}' "$0"; exit 0 ;;
        *) echo "unknown option: $1" >&2; exit 2 ;;
    esac
done

# --- output -------------------------------------------------------------------
# Everything this script says is timestamped. A run works a backlog down over
# hours; the log is what gets read afterwards to see which task ate the evening
# and how long a session sat waiting for a usage limit to lift.
stamp() { date '+[%H:%M:%S]'; }
log() { printf '%s grind: %s\n' "$(stamp)" "$*"; }

# Seconds as something a human reads at a glance: 4500 → "1h 15m".
hms() {
    local s="$1"
    if [ "$s" -ge 3600 ]; then printf '%dh %02dm' $((s / 3600)) $((s % 3600 / 60))
    elif [ "$s" -ge 60 ]; then printf '%dm %02ds' $((s / 60)) $((s % 60))
    else printf '%ds' "$s"
    fi
}

die() { log "$*" >&2; exit 1; }

command -v claude >/dev/null || die "claude CLI not on PATH"
command -v gh >/dev/null || die "gh CLI not on PATH"
[ -d todo ] || die "no todo/ directory here"
case "$SCOPE" in all|bugs|features) ;; *) die "--only takes 'bugs' or 'features', not '$SCOPE'" ;; esac
mkdir -p "$STATE_DIR"

# A bad effort level has to be caught here. Sent through, it is claude that
# rejects it — once per session, after the model and the prompt are already
# paid for, and the loop reads that as "the session ended early" and retries it
# five times.
check_effort() {
    case "$2" in
        low|medium|high|xhigh|max) ;;
        *) die "$1 takes low|medium|high|xhigh|max, not '$2'" ;;
    esac
}
check_effort --effort-author "$EFFORT_AUTHOR"
check_effort --effort-review "$EFFORT_REVIEW"
check_effort --effort-pick "$EFFORT_PICK"

# Same reasoning for the other two values that are only read much later: a typo
# in --escalate-after would otherwise surface as an arithmetic error in the
# middle of a review round, and a bad --autocompact as a dead session.
case "$ESCALATE_AFTER" in
    ''|*[!0-9]*) die "--escalate-after takes a whole number of rounds (0 disables), not '$ESCALATE_AFTER'" ;;
esac
[[ "$AUTOCOMPACT" =~ ^(auto|off|[0-9]+k?)?$ ]] \
    || die "--autocompact takes 'auto', 'off' or a token count in claude's 100k–1M range (100000, 100k), not '$AUTOCOMPACT'"
[ "$AUTOCOMPACT" = off ] && AUTOCOMPACT=""

# Not every claude build has these flags, and passing one to a build that has
# never heard of it is a hard exit before the prompt is even read. So `--help`
# is read once and each flag is dropped with a warning rather than taking the
# whole run down. `head -c` bounds what the substitution buffers, and it ends on
# `true` so the SIGPIPE that bounding causes is not fatal under `pipefail`.
CLAUDE_HELP="$(claude --help 2>/dev/null | head -c 20000 || true)"
supports() { case "$CLAUDE_HELP" in *"$1"*) return 0 ;; esac; return 1; }

EFFORT_SUPPORTED=1
supports '--effort' || { EFFORT_SUPPORTED=0; log "this claude build has no --effort; running without it"; }

# Cache-friendliness: cwd, environment and **git status** are part of the system
# prompt, which is the cached prefix — and git status changes with every commit,
# so every new session re-pays for everything after it. This flag moves those
# sections into the first user message instead. Measured on this repo, across a
# change of git state: 26.1k tokens re-cached without it against 21.0k with it.
# It is ignored when --system-prompt is given, so it does nothing for the picker.
CACHE_FLAG=()
supports '--exclude-dynamic-system-prompt-sections' && CACHE_FLAG=(--exclude-dynamic-system-prompt-sections)

AUTOCOMPACT_SUPPORTED=1
supports '--autocompact' || { AUTOCOMPACT_SUPPORTED=0; log "this claude build has no --autocompact; long sessions will not self-compact"; }
unset CLAUDE_HELP

# The picker and the sizer call claude directly rather than through claude_run
# (no streaming, no renderer, one short answer), so they need the flag prebuilt.
EFFORT_PICK_FLAG=()
[ "$EFFORT_SUPPORTED" -eq 1 ] && EFFORT_PICK_FLAG=(--effort "$EFFORT_PICK")

# --- the one-shot calls -------------------------------------------------------
# The picker and the sizer are told everything they need in their own prompt —
# the open ids, todo/README.md, the spec. They still used to be charged for the
# whole working environment: measured at 29.3k input tokens for a two-word
# answer, because a session started in this directory loads CLAUDE.md (46 KB),
# the default system prompt, the skills index and the MCP tool definitions.
#
# Three things cut that to 1.4k, measured the same way:
#
#   - **run them outside the repository.** CLAUDE.md is discovered from the
#     working directory upwards, so a scratch directory has none of it. This is
#     safe precisely because these two calls have no tools and never touch the
#     tree; it would be wrong for the author or the reviewer, which need it.
#   - **--system-prompt** replaces the default one (the coding-agent preamble is
#     pointless for a call that answers one word).
#   - **--strict-mcp-config** drops the MCP servers' tool definitions.
#
# `--bare` would do all of it in one flag, but it also refuses to read the OAuth
# login — "Not logged in · Please run /login" — so it is only usable with an
# ANTHROPIC_API_KEY, which is a different bill.
PICK_DIR="${TMPDIR:-/tmp}/dosmotr-grind-pick"
PICK_SYSTEM="You answer questions about a software backlog in the exact format asked for, and nothing else."
mkdir -p "$PICK_DIR"

# Reads the prompt on stdin, prints the answer. Bounded by the caller.
#   claude_oneshot <model>
claude_oneshot() {
    local model="$1"
    (cd "$PICK_DIR" && timeout 120 claude -p --tools "" --model "$model" \
        "${EFFORT_PICK_FLAG[@]}" --strict-mcp-config --no-session-persistence \
        --system-prompt "$PICK_SYSTEM" 2>/dev/null)
}

# --- stop after this task -------------------------------------------------
# Ctrl-C stops the run where it stands, which in the middle of a review round
# means a PR left open and a session to resume by hand. `f` is the graceful
# version: the task in hand runs to the end — review, merge, close-task.sh — and
# the loop exits instead of picking up the next one.
#
# The request is a file rather than a variable because the key is read in a
# background subshell, which cannot write to the parent's memory. That also
# makes it usable from anywhere: `touch todo/.grind/stop` over ssh does the same
# thing as the keypress.
STOP_FILE="$STATE_DIR/stop"
LIMIT_FILE="$STATE_DIR/limit"
MAIN_PID=$$
WATCH_PID=""
TTY_STATE=""
# `[ -r /dev/tty ]` is not the question — the device node is readable by its
# permissions even for a process with no controlling terminal (a systemd unit,
# `setsid`, a cron job), and only the open fails. So the test is an open.
tty_usable() { { : </dev/tty; } 2>/dev/null; }
tty_usable || HOTKEY=0
[ "$HOTKEY" -eq 1 ] && TTY_STATE="$( { stty -g </dev/tty; } 2>/dev/null || true)"
rm -f "$STOP_FILE" "$LIMIT_FILE"

stop_requested() { [ -f "$STOP_FILE" ]; }

# Reading single keys puts the terminal in no-echo mode for the length of the
# read. Killing the reader mid-read would leave it that way, so the settings
# taken at startup are put back every time the watcher stops.
tty_restore() {
    [ -n "$TTY_STATE" ] && stty "$TTY_STATE" </dev/tty 2>/dev/null || true
}

watch_key_start() {
    [ "$HOTKEY" -eq 1 ] || return 0
    [ -n "$WATCH_PID" ] && return 0
    (
        { exec </dev/tty; } 2>/dev/null || exit 0
        local_fails=0
        while kill -0 "$MAIN_PID" 2>/dev/null; do
            # -t so the loop comes back around to check the parent is still
            # there; a plain blocking read would outlive it on a hard kill.
            if IFS= read -rsn1 -t 30 key; then
                local_fails=0
                case "$key" in
                    f|F|q|Q)
                        : > "$STOP_FILE"
                        printf '\n%s grind: stop requested — finishing this task, then exiting. [c] cancels.\n' "$(stamp)"
                        ;;
                    c|C)
                        if [ -f "$STOP_FILE" ]; then
                            rm -f "$STOP_FILE"
                            printf '\n%s grind: stop cancelled — the loop carries on after this task.\n' "$(stamp)"
                        fi
                        ;;
                esac
            else
                # >128 is the -t timeout, which is the normal way round the
                # loop. Anything else is a terminal that has gone away (the
                # session detached, the window closed) — retrying that read
                # costs a spinning core, so the watcher gives up instead.
                [ $? -gt 128 ] || local_fails=$((local_fails + 1))
                [ "$local_fails" -ge 3 ] && exit 0
            fi
        done
    ) &
    WATCH_PID=$!
}

watch_key_stop() {
    [ -n "$WATCH_PID" ] || return 0
    kill "$WATCH_PID" 2>/dev/null || true
    wait "$WATCH_PID" 2>/dev/null || true
    WATCH_PID=""
    tty_restore
}

trap 'watch_key_stop' EXIT

# Ask on the tty. The watcher owns the keyboard while a task runs, so it has to
# let go before a prompt: two readers on one tty means the answer lands in
# whichever won the race.
#
# The locals are underscored because bash has no lexical scope: a local named
# `answer` here would shadow the caller's `answer`, and `printf -v` would then
# fill in the shadow and leave the caller with nothing.
ask_tty() {
    local __prompt="$1" __var="$2" __reply=""
    watch_key_stop
    printf '%s' "$__prompt"
    IFS= read -r __reply </dev/tty || __reply=""
    printf -v "$__var" '%s' "$__reply"
    watch_key_start
}

# --- usage limits -------------------------------------------------------------
# Wait out a usage limit. scripts/claude-stream.py writes "<epoch>\t<human>" to
# $LIMIT_FILE when the session that just died said when the limit lifts, so the
# wait is until that moment plus a cushion rather than a fixed interval — which
# was either far too long (a limit lifting in two minutes) or far too short (one
# lifting in three hours, retried twelve times for nothing).
wait_for_reset() {
    local reason="$1" epoch now delay when
    now="$(date +%s)"
    # `|| true`: a missing file makes awk exit non-zero, which under `pipefail`
    # would take the whole script down inside the assignment. The empty result
    # is the fallback case, not an error.
    epoch="$(awk -F'\t' 'NR==1{print $1}' "$LIMIT_FILE" 2>/dev/null | tr -cd '0-9' || true)"
    if [ -n "$epoch" ]; then
        delay=$((epoch - now + LIMIT_BUFFER))
        [ "$delay" -lt 60 ] && delay=60          # already lifted: a token pause
        if [ "$delay" -gt "$LIMIT_MAX_WAIT" ]; then
            log "reported reset is $(hms $((epoch - now))) away — capping the wait at $(hms "$LIMIT_MAX_WAIT")"
            delay="$LIMIT_MAX_WAIT"
        fi
        when="$(date -d "@$epoch" '+%Y-%m-%d %H:%M:%S' 2>/dev/null || echo "$epoch")"
        log "$reason: usage limit resets $when — waiting $(hms "$delay")"
    else
        delay="$LIMIT_FALLBACK"
        log "$reason: no reset time reported — waiting $(hms "$delay")"
    fi
    log "back at $(date -d "@$((now + delay))" '+%Y-%m-%d %H:%M:%S' 2>/dev/null || echo "+$(hms "$delay")")"
    sleep "$delay"
    rm -f "$LIMIT_FILE"
}

# The backlog, narrowed by --bugs/--features. Bugs come before features, and
# within a directory the order is numeric (`sort -V`): plain `ls` puts feature-9
# after feature-21, which is the wrong "next one" in --in-order mode and the
# wrong fallback everywhere else.
open_tasks() {
    local dirs=(todo/bugs todo/features)
    case "$SCOPE" in
        bugs) dirs=(todo/bugs) ;;
        features) dirs=(todo/features) ;;
    esac
    local dir
    for dir in "${dirs[@]}"; do
        ls "$dir"/*.md 2>/dev/null | xargs -r -n1 basename | sed 's/\.md$//' | sort -V
    done
}

# Every session runs non-interactively. An interactive `claude` does not end when
# the work is done — it sits at its prompt waiting for input, so the loop above it
# never gets control back. Print mode ends by itself, and streaming JSON through
# scripts/claude-stream.py keeps what an interactive session shows: thinking, tool
# calls, results. The pipeline is deliberately non-fatal — a session that dies (a
# usage limit above all) is handled by the phase checks, not by killing the loop.
#   claude_run [--text-out FILE] [--model NAME] <prompt> [claude flags...]
#
# The prompt goes in on stdin, never as a trailing argument: `--tools` takes a
# variadic list, so anything after it is read as one more tool name — including
# the prompt, which then leaves claude exiting with "Input must be provided
# either through stdin or as a prompt argument when using --print".
claude_run() {
    local text_out="" model="" effort="" compact=""
    while [ $# -gt 0 ]; do
        case "$1" in
            --text-out) text_out="$2"; shift 2 ;;
            --model) model="$2"; shift 2 ;;
            --effort) effort="$2"; shift 2 ;;
            --autocompact) compact="$2"; shift 2 ;;
            *) break ;;
        esac
    done
    local prompt="$1"; shift
    local model_flag=("${CACHE_FLAG[@]}") render=(python3 scripts/claude-stream.py --limit-out "$LIMIT_FILE")
    [ -n "$model" ] && model_flag+=(--model "$model")
    [ -n "$effort" ] && [ "$EFFORT_SUPPORTED" -eq 1 ] && model_flag+=(--effort "$effort")
    # Only the author asks for this: a reviewer session is one turn and never
    # gets near the threshold, so the flag would be noise on its command line.
    [ -n "$compact" ] && [ "$AUTOCOMPACT_SUPPORTED" -eq 1 ] && model_flag+=(--autocompact "$compact")
    [ -n "$text_out" ] && render+=(--text-out "$text_out")
    # Stale reset time from an earlier session must not be read as this one's.
    rm -f "$LIMIT_FILE"
    set +e
    # stderr goes through the renderer too: a usage limit is announced there as
    # often as on stdout, and the filter prints anything it cannot parse as JSON
    # rather than swallowing it.
    printf '%s' "$prompt" \
        | claude -p --output-format stream-json --include-partial-messages --verbose \
            "${model_flag[@]}" --permission-mode "$MODE" "$@" 2>&1 \
        | "${render[@]}"
    set -e
    return 0
}

# Did the session that just ran die on a usage limit?
hit_limit() { [ -s "$LIMIT_FILE" ]; }

# Resume the author's conversation, optionally seeding it with a message. The
# model is whatever run_task chose for this task ($AUTHOR_MODEL, remembered in
# $STATE_DIR/<task>.model), so a resume after a usage limit does not silently
# continue an Opus conversation on Sonnet or the other way round.
author_resume() {
    local sid="$1"
    shift
    if [ $# -eq 0 ]; then
        # Nothing to say: a bare resume in print mode would have no prompt at
        # all, so ask it to carry on from where it stopped.
        set -- "Continue where you left off and finish the task."
    fi
    claude_run --model "$AUTHOR_MODEL" --effort "$EFFORT_AUTHOR" \
        --autocompact "$AUTOCOMPACT" "$1" --resume "$sid"
}

# The PR the author opened for whatever branch it is sitting on.
pr_url() {
    local branch
    branch="$(git rev-parse --abbrev-ref HEAD)"
    case "$branch" in
        develop|master) return 0 ;;
    esac
    gh pr list --head "$branch" --base develop --state open --json url --jq '.[0].url // empty' 2>/dev/null
}

task_path() {
    for dir in todo/bugs todo/features; do
        [ -f "$dir/$1.md" ] && { echo "$dir/$1.md"; return 0; }
    done
    return 1
}

# --- pick the next task -------------------------------------------------------
# Two questions, two calls, two models — they only look like one question.
#
# *Which task next* is a ranking over ids that are already in the prompt, and
# being wrong costs a suboptimal order and nothing else, so Haiku answers it.
# *How hard is it* decides whether Opus or Sonnet writes the code, and Haiku
# gets that wrong in the expensive direction (see MODEL_SIZE), so size_task asks
# Sonnet, and only about the one task that was picked.
#
# Both are tool-less one-shots, cheap enough to run before every task so the
# choice reflects what the previous one changed.
pick_task() {
    local list id
    list="$(open_tasks)"
    id="$(
        {
            echo "Backlog of an Android app. Open task ids, one per line:"
            echo "$list"
            echo
            echo "The index, with severity and a suggested order:"
            echo '```'
            cat todo/README.md 2>/dev/null
            echo '```'
            echo
            echo "Pick the SINGLE most valuable task to do next, from the ids listed"
            echo "above and no others. Weigh: user-visible damage (data corruption and"
            echo "unreachable actions first), how many users hit it, and cost to fix."
            echo "Prefer a cheap high-impact fix over an expensive one."
            echo
            echo "Answer with exactly one word: the id. Nothing else."
        } | claude_oneshot "$MODEL_PICK" | head -c 200 | tr -d '`' | tr '\n' ' '
        # `head` bounds what the command substitution can buffer — without it a
        # producer that never stops is read into memory until the OOM killer
        # takes the script (and whatever else is running) out. `head` closing
        # the pipe then SIGPIPEs the producer, which `set -o pipefail` would
        # turn into a fatal error, so the substitution ends on `true` instead:
        # bounded AND non-fatal. The fallback below handles the empty result.
        true
    )"
    id="$(printf '%s' "${id:0:96}" | awk '{print $1}')"
    # Checked against the list it was given, not just against todo/: under
    # --bugs/--features an id from the other directory is a hallucination too.
    if [ -n "$id" ] && printf '%s\n' "$list" | grep -qxF -- "$id"; then
        echo "$id $(size_task "$id")"
        return 0
    fi
    # Picker unavailable or hallucinated an id: fall back to backlog order,
    # bugs before features. Sized `simple` like any unanswered question — the
    # escalation after two failed review rounds is what covers a wrong guess,
    # and guessing `complex` here means paying Opus for every task whenever the
    # picker is unreachable.
    echo "$(echo "$list" | head -1) simple"
}

# The size question on its own: which model writes this task's code. Asked of
# $MODEL_SIZE, never of the picker's model, and asked about exactly one spec.
# Anything unparseable is 'simple' — the escalation after two failed review
# rounds is the safety net, so an unanswered question no longer means Opus.
size_task() {
    local id="$1" path size
    path="$(task_path "$id")" || { echo simple; return 0; }
    size="$(
        {
            echo "Task spec from the backlog of an Android app:"
            echo '```'
            head -c 4000 "$path"
            echo '```'
            echo
            echo "Size it. Answer 'complex' ONLY if at least one is true:"
            echo "  - it changes the database schema or needs a migration;"
            echo "  - it changes TrackerRepository, the DAO or sync/refresh logic;"
            echo "  - it adds a screen, or a product decision has to be made;"
            echo "  - the spec does not say what the result should be."
            echo "Everything else is 'simple', including when you are unsure — a"
            echo "task that turns out to be harder than it looked is handed to the"
            echo "stronger model automatically after two failed review rounds."
            echo
            echo "Answer with exactly one word: simple or complex. Nothing else."
        } | claude_oneshot "$MODEL_SIZE" \
          | head -c 100 | tr -d '`' | tr 'A-Z' 'a-z' | awk 'NF{print $1; exit}' | tr -cd 'a-z'
        # Bounded and non-fatal for the same reason as in pick_task: an unbounded
        # producer read into the shell's heap is what the OOM killer takes.
        true
    )"
    case "$size" in simple|complex) echo "$size" ;; *) echo simple ;; esac
}

# --- the prompt each working session starts with ------------------------------
build_prompt() {
    local task="$1" path="$2"
    cat <<EOF
Work task \`$task\` from the backlog. Its spec is \`$path\` — read it first, it
says what to change and usually which files.

Follow CLAUDE.md — it is short, and it is the list of things that are expensive
to get wrong. The detail sits in \`ai-docs/\`, and you are expected to open the
one file that covers what you are touching, not all of them: app code →
\`ai-docs/architecture.md\`; build, tests or the emulator →
\`ai-docs/build-and-test.md\`; flavours, donations, telemetry or store texts →
\`ai-docs/distribution-and-legal.md\`; network, signing or secrets →
\`ai-docs/secrets-and-backend.md\`; workflows or releases →
\`ai-docs/release-and-ci.md\`. Do not read \`ai-docs/grind-loop.md\` — it describes
the loop that is running you, and it is not about your task.

In particular:

1. Branch off develop: \`git checkout develop && git pull && git checkout -b feature/<short-name>\`.
2. Implement what the spec asks. If the spec is wrong or incomplete, say so and
   do the sensible thing rather than stopping.
3. Update the tests the change touches — the fakes mirror the DAO, so DAO
   changes mean \`FakeTrackerDao\` too.
4. Verify before committing: \`./gradlew testDirectDebugUnitTest\`, \`./gradlew :app:lintDirectDebug\`
   and \`./gradlew detekt\`. Run \`scripts/emulator.sh test\` when the change is UI
   or DAO. Fix what fails.
5. Commit in English, conventional-commits style.
6. Write the PR description **in Russian** into \`$STATE_DIR/$task.pr.md\` — prose,
   not a changelog: what the problem was, what you changed and why exactly that
   way, what you deliberately left alone, and anything you had to decide
   yourself. Mention how it was verified (which tests, which screen). Code,
   identifiers and commands stay as they are.
7. Open the PR with
   \`scripts/close-task.sh $task --pr-only --body-file $STATE_DIR/$task.pr.md\`.
   That pushes the branch and opens the PR into develop and stops there — it
   does NOT merge.

Then end your turn. A separate reviewer session reads the PR and its feedback
comes back to you in this same conversation; you fix what it raises and push,
and the merge happens only once review passes. Do not merge anything yourself
and do not run close-task.sh without --pr-only.

Nobody is at the keyboard: this session is non-interactive, so a question ends
the turn with the task unfinished. Decide it yourself, state the assumption you
made, and carry on.

**Keep the context small.** Everything in this conversation is re-sent to the
model on every turn, so a page of build log read once is paid for again on every
turn that follows it. Sessions that fill the context run out of budget before
the task is finished — that is the usual reason one dies half-done, not the size
of the change. Concretely:

- **Find code with CodeGraph, not by reading files.** This repository is indexed
  (\`.codegraph/\` exists), so \`codegraph explore "<symbols or question>"\` — or the
  \`codegraph_explore\` tool where it is available — returns the relevant symbols'
  source plus their callers in one call. Reach for it before grep and before
  Read. \`LibraryScreen.kt\` is 47 KB and \`DetailScreen.kt\` 53 KB: reading either
  whole costs more than the change usually does.
- **Read parts of files, not whole files**, once you know where you are going —
  \`offset\`/\`limit\`. Never read the same file twice: it is already above you in
  this conversation.
- **No build output in the conversation.** Redirect it and look only at what
  failed:
  \`./gradlew testDirectDebugUnitTest --console=plain -q > /tmp/$task-tests.log 2>&1 || tail -60 /tmp/$task-tests.log\`
  and the same shape for \`:app:lintDirectDebug\`, \`detekt\` and
  \`scripts/emulator.sh test\`. A green run needs one line from you, not the log.
- **\`git diff --stat\` before \`git diff\`**, and diff a path rather than the tree.
- **Screenshots only when the change is visual.** One shot, read it once, then
  write down what you saw; an image stays in the context for the rest of the
  session, and re-reading the same PNG buys nothing.
EOF
}

# --- reviewer -----------------------------------------------------------------
# Read-only tools, and no Bash: the reviewer reports, it never fixes, and the
# one thing it used Bash for — the diff — is handed to it in the prompt instead.
# Left to fetch its own, it ran `git diff origin/develop...HEAD` over the whole
# tree; the largest single result in the logs of this repo is 289 KB, which is
# ~70k tokens sitting in the context for the rest of the round. Read/Grep/Glob
# stay, because the diff alone does not show the rest of a changed file.
REVIEW_DIFF_MAX=${REVIEW_DIFF_MAX:-80000}

# The diff for a range, bounded. `head -c` is what keeps a runaway diff out of
# the shell's heap (and out of the context), and the substitution ends on `true`
# so the SIGPIPE bounding causes is not fatal under `pipefail`.
review_diff() {
    local range="$1" bytes
    bytes="$(git diff "$range" | wc -c || true)"
    bytes="$(printf '%s' "${bytes:-0}" | tr -cd '0-9')"
    if [ "${bytes:-0}" -eq 0 ]; then
        printf '(nothing changed in this range — the author pushed no new commits)\n'
        return 0
    fi
    printf '```diff\n'
    git diff "$range" | head -c "$REVIEW_DIFF_MAX" || true
    printf '\n```\n'
    if [ "$bytes" -gt "$REVIEW_DIFF_MAX" ]; then
        printf '\n(diff truncated at %s of %s bytes — Read the files themselves for the rest)\n' \
            "$REVIEW_DIFF_MAX" "$bytes"
    fi
    true
}

# Round 1 is a cold session that reads the spec and the whole diff. Rounds after
# it **resume that same session**: it already knows the spec, the diff and its
# own findings, so all it needs is what changed since — verifying five points
# does not need the PR re-read from nothing, and a cold session costs ~29k
# tokens before it starts. The reviewer is still not the author's session, which
# is what the separation was for.
#
# The session id is kept only once a round has actually produced a verdict, so a
# session that died before it existed (a usage limit at the first token) cannot
# be resumed into an error — the next attempt starts cold instead.
review_round() {
    local task="$1" round="$2" out="$3"
    rm -f "$out"        # a stale report from an earlier round must not be read as this one's
    local sid_file="$STATE_DIR/$task.review.session"
    local head_file="$STATE_DIR/$task.review.head"
    local sid="" since=""
    [ -s "$sid_file" ] && sid="$(cat "$sid_file")"
    [ -s "$head_file" ] && since="$(cat "$head_file")"
    # A remembered sha that is no longer in the branch (a rebase, a force-push)
    # cannot be diffed against; fall back to a cold review of everything.
    [ -n "$since" ] && ! git cat-file -e "$since^{commit}" 2>/dev/null && since=""

    if [ -n "$sid" ] && [ -n "$since" ]; then
        log "review round $round — resuming reviewer session $sid (fixes since ${since:0:8})"
        local fixes
        fixes="$(review_diff "$since..HEAD")"
        claude_run --text-out "$out" --model "$MODEL_REVIEW" --effort "$EFFORT_REVIEW" \
            "Review round $round on the same pull request. The author has answered your
last report and pushed to the same branch. Everything you already reviewed is in
this conversation — below is only what changed since, \`git diff ${since:0:8}..HEAD\`:

$fixes

Go through your last report point by point: fixed, not fixed, or answered with a
reason you accept. An unchanged file means the point was not addressed unless the
code shows otherwise. Then review these changes themselves — same invariants, same
bar as before; fixes introduce defects as readily as the original change did.

Read, Grep and Glob are available for the code around a hunk. Report only defects
worth another round trip; no praise, no summary.

End your reply with exactly one line:
VERDICT: APPROVE        — nothing worth blocking on
VERDICT: REQUEST_CHANGES — findings above must be fixed" \
            --resume "$sid" --tools "Read,Grep,Glob"
    else
        sid="$(python3 -c 'import uuid; print(uuid.uuid4())')"
        log "review round $round — fresh reviewer session $sid"
        claude_run --text-out "$out" --model "$MODEL_REVIEW" --effort "$EFFORT_REVIEW" \
            "You are reviewing a pull request in this repository, review round $round.

The task it implements is \`todo/*/$task.md\` — read that first, it is the spec.
CLAUDE.md holds the invariants of this codebase; several of them are expensive to
break (upsert vs REPLACE, IGNORE on episode insert, the DAO/enum sort order pair,
the fake that has to mirror the DAO, movies having no episode rows, season 0, the
TMDB attribution string).

The branch is \`$(git rev-parse --abbrev-ref HEAD)\`. Here is the change under
review — you do not need to fetch it, and you have no Bash to fetch it with.

\`\`\`
$(git diff --stat origin/develop...HEAD | tail -c 4000 || true)
\`\`\`

$(review_diff "origin/develop...HEAD")

Check, in this order: does it actually do what the spec asked; does it break an
invariant; is it correct at the edges the spec named; are the tests updated for
what changed; does it drag in unrelated changes. Read, Grep and Glob are there
for what the diff does not show — the rest of a changed file, a test that was
not touched.

$( [ -f "$out.prev" ] && printf 'An earlier round asked for changes:\n\n%s\n\nVerify each point was addressed.\n' "$(cat "$out.prev")" )

Report only defects worth a round trip. Style opinions, naming preferences and
'consider extracting' are not findings. Each finding: file:line, what is wrong,
what to do. No praise, no summary of the diff.

End your reply with exactly one line:
VERDICT: APPROVE        — nothing worth blocking on
VERDICT: REQUEST_CHANGES — findings above must be fixed" \
            --session-id "$sid" --tools "Read,Grep,Glob"
    fi

    # Both files are written only when the round produced a verdict — see above.
    if [ -n "$(verdict_of "$out")" ]; then
        echo "$sid" > "$sid_file"
        git rev-parse HEAD > "$head_file"
    else
        rm -f "$sid_file"
    fi
}

verdict_of() {
    grep -Eo 'VERDICT:[[:space:]]*(APPROVE|REQUEST_CHANGES)' "$1" 2>/dev/null \
        | tail -1 | grep -Eo 'APPROVE|REQUEST_CHANGES'
}

# --- one task -----------------------------------------------------------------
# Three phases: the author session works until a PR exists, the reviewer rounds
# run until they approve, and only then is the merge armed. The author session
# is one long conversation across all of it — review feedback is resumed into
# it, so it never re-reads its own diff from cold.
run_task() {
    local task="$1" size="${2:-simple}" path sid prompt started
    started="$(date +%s)"
    # Skip a bad id rather than killing the loop with it.
    path="$(task_path "$task")" || { log "no todo/{bugs,features}/$task.md"; return 1; }

    # The keyboard belongs to the watcher for as long as the task runs, so `f`
    # is available through every session, review round and limit wait. ask_tty
    # hands it back around each prompt.
    watch_key_start

    # Which model writes the code. --model forces it; otherwise the size the
    # picker gave decides. Remembered per task so a resume — days later, after a
    # usage limit — continues on the model the conversation was started on.
    local model_file="$STATE_DIR/$task.model"
    if [ -n "$MODEL" ]; then
        AUTHOR_MODEL="$MODEL"
    elif [ -s "$model_file" ]; then
        AUTHOR_MODEL="$(cat "$model_file")"
    elif [ "$size" = simple ]; then
        AUTHOR_MODEL="$MODEL_SIMPLE"
    else
        AUTHOR_MODEL="$MODEL_COMPLEX"
    fi
    echo "$AUTHOR_MODEL" > "$model_file"

    local sid_file="$STATE_DIR/$task.session"
    if [ -f "$sid_file" ]; then
        sid="$(cat "$sid_file")"
        log "resuming session $sid for $task [$AUTHOR_MODEL, effort $EFFORT_AUTHOR]"
        author_resume "$sid"
    else
        sid="$(python3 -c 'import uuid; print(uuid.uuid4())')"
        echo "$sid" > "$sid_file"
        prompt="$(build_prompt "$task" "$path")"
        log "starting session $sid for $task [$size → $AUTHOR_MODEL, effort $EFFORT_AUTHOR]"
        claude_run --model "$AUTHOR_MODEL" --effort "$EFFORT_AUTHOR" \
            --autocompact "$AUTOCOMPACT" "$prompt" --session-id "$sid"
    fi

    # Phase 1 — a PR has to exist before there is anything to review.
    local attempts=0
    while [ -z "$(pr_url)" ] && [ ! -f "todo/done/$task.md" ]; do
        attempts=$((attempts + 1))
        if [ "$ASK" -eq 0 ] && [ "$attempts" -gt 5 ]; then
            log "$task opened no PR in 5 unattended attempts — skipping"
            log "come back with: claude --resume $sid"
            return 1
        fi
        echo
        log "no open PR for $task yet — the session ended early (attempt $attempts)."
        echo "  Usage limit or Ctrl-C? Nothing is lost: 'r' continues the same"
        echo "  conversation from where it stopped."
        # A session killed by a limit cannot be resumed before the limit lifts,
        # so that case waits by itself even with someone at the keyboard —
        # asking would only be asking them to sit and press 'w'.
        if hit_limit; then
            wait_for_reset "$task"
            author_resume "$sid"
            continue
        fi
        if [ "$ASK" -eq 0 ]; then
            log "--yes, waiting $(hms "$LIMIT_FALLBACK") then resuming"
            sleep "$LIMIT_FALLBACK"
            author_resume "$sid"
            continue
        fi
        ask_tty "  [r]esume  [w]ait 1h then resume  [s]kip  [q]uit > " answer
        case "$answer" in
            r|R|"") author_resume "$sid" ;;
            w|W) log "waiting 1h, back at $(date -d '+1 hour' '+%H:%M:%S')"; sleep 3600; author_resume "$sid" ;;
            s|S) log "skipping $task (session kept: claude --resume $sid)"; return 1 ;;
            q|Q) log "stopping (session kept: claude --resume $sid)"; exit 0 ;;
        esac
    done

    [ -f "todo/done/$task.md" ] && { log "$task closed"; return 0; }

    # Phase 2 — review rounds against that PR.
    if [ "$REVIEW" -eq 1 ]; then
        local round=1 review verdict failed=0 rejected=0
        while :; do
            review="$STATE_DIR/$task.review.$round.md"
            [ "$round" -gt 1 ] && cp "$STATE_DIR/$task.review.$((round - 1)).md" "$review.prev"
            echo
            log "review round $round on $(pr_url)"
            review_round "$task" "$round" "$review"

            # No VERDICT line means the reviewer never actually reviewed: a usage
            # limit ("You've hit your session limit"), a crash, a bad invocation.
            # That is a failed round, NEVER an approval — treating it as one is
            # how unreviewed work merged twice already. So the round is retried
            # rather than counted, and if it cannot be made to run, the PR is
            # left open.
            verdict="$(verdict_of "$review")"
            if [ -z "$verdict" ]; then
                if [ -s "$review" ]; then
                    log "the reviewer answered without a VERDICT line (see $review):"
                    head -c 400 "$review" | sed 's/^/  | /'
                    echo
                else
                    log "the reviewer produced nothing (see $review)"
                fi
                failed=$((failed + 1))
                # The cap is on the retries the script decides to make by
                # itself; a person answering the prompt below can go on as long
                # as they like.
                if { [ "$ASK" -eq 0 ] || hit_limit; } && [ "$failed" -gt 5 ]; then
                    log "5 failed review attempts — PR left open, NOT merged"
                    return 1
                fi
                # A reviewer that hit the limit is retried when the limit lifts,
                # not fifteen minutes later — that is the case the retry loop
                # exists for, and the wait used to be a guess.
                if hit_limit; then
                    wait_for_reset "review round $round (attempt $failed)"
                    continue
                fi
                if [ "$ASK" -eq 0 ]; then
                    log "--yes, waiting $(hms "$LIMIT_FALLBACK") then retrying round $round (attempt $failed)"
                    sleep "$LIMIT_FALLBACK"
                    continue
                fi
                ask_tty "  [r]etry now  [w]ait 1h then retry  [s]kip, leave the PR open > " answer
                case "$answer" in
                    r|R|"") continue ;;
                    w|W) log "waiting 1h, back at $(date -d '+1 hour' '+%H:%M:%S')"; sleep 3600; continue ;;
                    *) log "PR left open, NOT merged"; return 1 ;;
                esac
            fi
            failed=0

            case "$verdict" in
                APPROVE) log "review passed on round $round"; break ;;
                REQUEST_CHANGES) rejected=$((rejected + 1)) ;;
            esac

            # Two rounds of findings mean the task was sized wrong: the sizing
            # call is one word from a small model over a spec, and it is meant
            # to be optimistic — this is the safety net under it, not a failure
            # of the author. The switch is written to <task>.model so a resume
            # days later stays on the model the rest of the conversation ran on.
            #
            # It costs one re-cache of the whole conversation (a different model
            # is a different cache), which is why it happens at round two rather
            # than round five, and why --autocompact keeps that conversation
            # from being enormous when it does.
            if [ "$ESCALATE_AFTER" -gt 0 ] && [ "$rejected" -ge "$ESCALATE_AFTER" ] \
               && [ "$AUTHOR_MODEL" != "$MODEL_COMPLEX" ] && [ -z "$MODEL" ]; then
                log "escalating $task: $AUTHOR_MODEL → $MODEL_COMPLEX after $rejected rounds of findings"
                AUTHOR_MODEL="$MODEL_COMPLEX"
                echo "$AUTHOR_MODEL" > "$model_file"
            fi

            if [ "$round" -ge "$MAX_ROUNDS" ]; then
                echo
                log "still not approved after $MAX_ROUNDS rounds. Findings are in $review."
                if [ "$ASK" -eq 0 ]; then
                    log "--yes, leaving the PR open for you and moving on"
                    return 1
                fi
                ask_tty "  [m]erge anyway  [o]ne more round  [s]kip and leave the PR open > " answer
                case "$answer" in
                    m|M) break ;;
                    o|O) MAX_ROUNDS=$((MAX_ROUNDS + 1)) ;;
                    *) return 1 ;;
                esac
            fi

            log "sending the findings back to the author session"
            author_resume "$sid" "A reviewer looked at your PR and asked for changes. Their
report, verbatim:

$(cat "$review")

Fix what is a real defect. If a finding is wrong, say why instead of changing
code to satisfy it. Commit and push to the same branch when done — do not merge,
another review round follows. If the fixes changed what the PR actually does,
update $STATE_DIR/$task.pr.md (Russian, same as before) and push it onto the PR
with: gh pr edit --body-file $STATE_DIR/$task.pr.md
(keep the trailing \`Closes\` line that is already on the PR — that file does not
carry it, so append it before you push the body)."
            round=$((round + 1))
        done
    fi

    # Phase 3 — merge, record, move, back to develop.
    log "closing $task"
    scripts/close-task.sh "$task" || {
        log "close-task.sh failed for $task; PR left open"
        return 1
    }
    log "$task closed after $(hms $(($(date +%s) - started)))"
    return 0
}

# --- the loop -----------------------------------------------------------------
SKIPPED=" "
log "started $(date '+%Y-%m-%d %H:%M:%S')$([ "$HOTKEY" -eq 1 ] && printf ' — [f] finish this task and stop')"
while :; do
    # Pressed during the picker, or between tasks: nothing is in flight, so
    # honour it right away.
    if stop_requested; then
        rm -f "$STOP_FILE"
        log "stop requested — nothing in flight, exiting"
        exit 0
    fi
    # Skipped tasks stay in todo/, so they have to be filtered out by hand or
    # the loop offers the same one forever.
    REMAINING=""
    for t in $(open_tasks); do
        case "$SKIPPED" in *" $t "*) continue ;; esac
        REMAINING="$REMAINING $t"
    done
    [ -n "${REMAINING# }" ] || { log "nothing left to do (${SKIPPED# } skipped)"; exit 0; }

    # --task names an id but says nothing about its size, so it is sized the
    # same way as one the picker chose. It used to mean `complex` outright,
    # which quietly made "work this one task" the most expensive way to run the
    # script; escalation after two failed rounds covers a wrong guess now.
    if [ -n "$START_TASK" ]; then
        TASK="$START_TASK"; START_TASK=""
        SIZE="$(task_path "$TASK" >/dev/null 2>&1 && size_task "$TASK" || echo simple)"
    elif [ "$ORDER" -eq 1 ]; then
        # Backlog order: the first id still open, skipped ones already filtered
        # out of REMAINING. No ranking call — only the sizing one.
        TASK="$(printf '%s' "$REMAINING" | awk '{print $1}')"
        SIZE="$(size_task "$TASK")"
    else
        PICKED="$(pick_task)"
        TASK="${PICKED%% *}"; SIZE="${PICKED##* }"
        case "$SKIPPED" in *" $TASK "*) TASK="$(printf '%s' "$REMAINING" | awk '{print $1}')"; SIZE="$(size_task "$TASK")" ;; esac
    fi

    # A typo in --task or in the [o]ther prompt must cost a re-ask, not the run.
    if ! TASK_FILE="$(task_path "$TASK")"; then
        log "no such task '$TASK'; open ids:${REMAINING}"
        [ "$ASK" -eq 0 ] && exit 1
        ask_tty "$(stamp) grind: task id > " START_TASK
        continue
    fi

    echo
    echo "======================================================================"
    log "next up — $TASK    ($(open_tasks | wc -l) open, sized $SIZE)    $(date '+%Y-%m-%d')"
    sed -n '1p' "$TASK_FILE"
    echo "======================================================================"

    if [ "$ASK" -eq 1 ]; then
        ask_tty "$(stamp) grind: [Enter] start  [o]ther task  [q]uit > " answer
        case "$answer" in
            q|Q) exit 0 ;;
            o|O)
                ask_tty "  task id > " START_TASK
                continue
                ;;
        esac
    fi

    run_task "$TASK" "$SIZE" || SKIPPED="$SKIPPED $TASK"

    # `f` during the task: it has been carried to the end, so stop here rather
    # than starting the next one.
    if stop_requested; then
        rm -f "$STOP_FILE"
        log "stop requested during $TASK — that task is finished, exiting"
        exit 0
    fi

    [ "$ONCE" -eq 1 ] && { log "--once, stopping"; exit 0; }
done
