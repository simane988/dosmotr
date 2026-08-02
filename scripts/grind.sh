#!/usr/bin/env bash
# Work the todo/ backlog down to nothing, one full Claude Code session per task.
#
#   scripts/grind.sh              # pick, work, close, repeat — asks before each task
#   scripts/grind.sh --yes        # unattended: no prompt between tasks
#   scripts/grind.sh --task bug-4 # start from a specific task, then continue as usual
#   scripts/grind.sh --once       # do exactly one task and stop
#   scripts/grind.sh --no-review  # skip the review rounds, merge straight away
#   scripts/grind.sh --rounds 5   # allow more review rounds before asking
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
REVIEW=1
MAX_ROUNDS=3

while [ $# -gt 0 ]; do
    case "$1" in
        --yes|-y) ASK=0; shift ;;
        --once) ONCE=1; shift ;;
        --task) START_TASK="$2"; shift 2 ;;
        --no-review) REVIEW=0; shift ;;
        --rounds) MAX_ROUNDS="$2"; shift 2 ;;
        --mode) MODE="$2"; shift 2 ;;
        --model) MODEL="$2"; shift 2 ;;
        -h|--help) sed -n '2,/^set -/{/^set -/!p}' "$0"; exit 0 ;;
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

# Every session runs non-interactively. An interactive `claude` does not end when
# the work is done — it sits at its prompt waiting for input, so the loop above it
# never gets control back. Print mode ends by itself, and streaming JSON through
# scripts/claude-stream.py keeps what an interactive session shows: thinking, tool
# calls, results. The pipeline is deliberately non-fatal — a session that dies (a
# usage limit above all) is handled by the phase checks, not by killing the loop.
#   claude_run [--text-out FILE] <prompt> [claude flags...]
#
# The prompt goes in on stdin, never as a trailing argument: `--tools` takes a
# variadic list, so anything after it is read as one more tool name — including
# the prompt, which then leaves claude exiting with "Input must be provided
# either through stdin or as a prompt argument when using --print".
claude_run() {
    local text_out=""
    if [ "$1" = "--text-out" ]; then text_out="$2"; shift 2; fi
    local prompt="$1"; shift
    local model_flag=() render=(python3 scripts/claude-stream.py)
    [ -n "$MODEL" ] && model_flag=(--model "$MODEL")
    [ -n "$text_out" ] && render+=(--text-out "$text_out")
    set +e
    printf '%s' "$prompt" \
        | claude -p --output-format stream-json --include-partial-messages --verbose \
            "${model_flag[@]}" --permission-mode "$MODE" "$@" \
        | "${render[@]}"
    set -e
    return 0
}

# Resume the author's conversation, optionally seeding it with a message.
author_resume() {
    local sid="$1"
    shift
    if [ $# -eq 0 ]; then
        # Nothing to say: a bare resume in print mode would have no prompt at
        # all, so ask it to carry on from where it stopped.
        set -- "Continue where you left off and finish the task."
    fi
    claude_run "$1" --resume "$sid"
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
        } | timeout 120 claude -p --tools "" ${MODEL:+--model "$MODEL"} 2>/dev/null \
          | head -c 200 | tr -d '[:space:]`'
        # `head` bounds what the command substitution can buffer — without it a
        # producer that never stops is read into memory until the OOM killer
        # takes the script (and whatever else is running) out. `head` closing
        # the pipe then SIGPIPEs the producer, which `set -o pipefail` would
        # turn into a fatal error, so the substitution ends on `true` instead:
        # bounded AND non-fatal. The fallback below handles the empty result.
        true
    )"
    picked="${picked:0:64}"
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
EOF
}

# --- reviewer -----------------------------------------------------------------
# A fresh session every round, on purpose: the author's session has already
# convinced itself the code is right, so re-reading the diff with the same
# context is worth little. Read-only tools — the reviewer reports, it never
# fixes. Output is printed live and captured; the last VERDICT line decides.
review_round() {
    local task="$1" round="$2" out="$3"
    claude_run --text-out "$out" \
        "You are reviewing a pull request in this repository, review round $round.

The branch is \`$(git rev-parse --abbrev-ref HEAD)\`; the diff under review is
\`git diff origin/develop...HEAD\`. The task it implements is \`todo/*/$task.md\` —
read that first, it is the spec. CLAUDE.md holds the invariants of this codebase;
several of them are expensive to break (upsert vs REPLACE, IGNORE on episode
insert, the DAO/enum sort order pair, the fake that has to mirror the DAO,
movies having no episode rows, season 0, the TMDB attribution string).

Check, in this order: does it actually do what the spec asked; does it break an
invariant; is it correct at the edges the spec named; are the tests updated for
what changed; does it drag in unrelated changes.

$( [ -f "$out.prev" ] && printf 'The previous round asked for changes:\n\n%s\n\nVerify each point was addressed.\n' "$(cat "$out.prev")" )

Report only defects worth a round trip. Style opinions, naming preferences and
'consider extracting' are not findings. Each finding: file:line, what is wrong,
what to do. No praise, no summary of the diff.

End your reply with exactly one line:
VERDICT: APPROVE        — nothing worth blocking on
VERDICT: REQUEST_CHANGES — findings above must be fixed" \
        --tools "Read,Grep,Glob,Bash"
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
    local task="$1" path sid prompt
    # Skip a bad id rather than killing the loop with it.
    path="$(task_path "$task")" || { echo "grind: no todo/{bugs,features}/$task.md"; return 1; }

    local sid_file="$STATE_DIR/$task.session"
    if [ -f "$sid_file" ]; then
        sid="$(cat "$sid_file")"
        echo "grind: resuming session $sid for $task"
        author_resume "$sid"
    else
        sid="$(python3 -c 'import uuid; print(uuid.uuid4())')"
        echo "$sid" > "$sid_file"
        prompt="$(build_prompt "$task" "$path")"
        echo "grind: starting session $sid for $task"
        claude_run "$prompt" --session-id "$sid"
    fi

    # Phase 1 — a PR has to exist before there is anything to review.
    local attempts=0
    while [ -z "$(pr_url)" ] && [ ! -f "todo/done/$task.md" ]; do
        attempts=$((attempts + 1))
        if [ "$ASK" -eq 0 ] && [ "$attempts" -gt 5 ]; then
            echo "grind: $task opened no PR in 5 unattended attempts — skipping"
            echo "grind: come back with: claude --resume $sid"
            return 1
        fi
        echo
        echo "grind: no open PR for $task yet — the session ended early."
        echo "  Usage limit or Ctrl-C? Nothing is lost: 'r' continues the same"
        echo "  conversation from where it stopped."
        if [ "$ASK" -eq 0 ]; then
            echo "grind: --yes, waiting 15 min then resuming"
            sleep 900
            author_resume "$sid"
            continue
        fi
        printf "  [r]esume  [w]ait 1h then resume  [s]kip  [q]uit > "
        read -r answer </dev/tty
        case "$answer" in
            r|R|"") author_resume "$sid" ;;
            w|W) sleep 3600; author_resume "$sid" ;;
            s|S) echo "grind: skipping $task (session kept: claude --resume $sid)"; return 1 ;;
            q|Q) echo "grind: stopping (session kept: claude --resume $sid)"; exit 0 ;;
        esac
    done

    [ -f "todo/done/$task.md" ] && { echo "grind: $task closed"; return 0; }

    # Phase 2 — review rounds against that PR.
    if [ "$REVIEW" -eq 1 ]; then
        local round=1 review verdict
        while :; do
            review="$STATE_DIR/$task.review.$round.md"
            [ "$round" -gt 1 ] && cp "$STATE_DIR/$task.review.$((round - 1)).md" "$review.prev"
            echo
            echo "grind: review round $round on $(pr_url)"
            review_round "$task" "$round" "$review"

            # An empty report means the reviewer never ran (a usage limit, a bad
            # invocation) — that is a failure, not an approval. Merging on it
            # would land unreviewed work, so the PR is left open instead.
            if [ ! -s "$review" ]; then
                echo "grind: the reviewer produced nothing — PR left open, not merging"
                return 1
            fi
            verdict="$(verdict_of "$review")"

            case "$verdict" in
                APPROVE) echo "grind: review passed on round $round"; break ;;
                REQUEST_CHANGES) ;;
                *)
                    echo "grind: reviewer gave no verdict (see $review) — treating as approved"
                    break
                    ;;
            esac

            if [ "$round" -ge "$MAX_ROUNDS" ]; then
                echo
                echo "grind: still not approved after $MAX_ROUNDS rounds. Findings are in $review."
                if [ "$ASK" -eq 0 ]; then
                    echo "grind: --yes, leaving the PR open for you and moving on"
                    return 1
                fi
                printf "  [m]erge anyway  [o]ne more round  [s]kip and leave the PR open > "
                read -r answer </dev/tty
                case "$answer" in
                    m|M) break ;;
                    o|O) MAX_ROUNDS=$((MAX_ROUNDS + 1)) ;;
                    *) return 1 ;;
                esac
            fi

            echo "grind: sending the findings back to the author session"
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
    echo "grind: closing $task"
    scripts/close-task.sh "$task" || {
        echo "grind: close-task.sh failed for $task; PR left open"
        return 1
    }
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

    # A typo in --task or in the [o]ther prompt must cost a re-ask, not the run.
    if ! TASK_FILE="$(task_path "$TASK")"; then
        echo "grind: no such task '$TASK'; open ids:${REMAINING}"
        [ "$ASK" -eq 0 ] && exit 1
        printf "grind: task id > "; read -r START_TASK </dev/tty
        continue
    fi

    echo
    echo "======================================================================"
    echo "grind: next up — $TASK    ($(open_tasks | wc -l) open)"
    sed -n '1p' "$TASK_FILE"
    echo "======================================================================"

    if [ "$ASK" -eq 1 ]; then
        printf "grind: [Enter] start  [o]ther task  [q]uit > "
        read -r answer </dev/tty
        case "$answer" in
            q|Q) exit 0 ;;
            o|O)
                printf "  task id > "; read -r START_TASK </dev/tty
                continue
                ;;
        esac
    fi

    run_task "$TASK" || SKIPPED="$SKIPPED $TASK"

    [ "$ONCE" -eq 1 ] && { echo "grind: --once, stopping"; exit 0; }
done
