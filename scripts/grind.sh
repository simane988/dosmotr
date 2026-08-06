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
#
# By default Claude picks the next task by value. --in-order takes them in
# backlog order instead — bugs before features, then by number (feature-20,
# feature-21, …), which is what you want when the specs build on each other.
# The task is still sized simple/complex, so the model choice is unaffected.
#
# Models are per job. The picker sizes each task simple/complex and the author
# session runs on the matching model; reviews are always Sonnet — reading a diff
# against a checklist does not need more, and the backlog is long. --model
# overrides the author session only.
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
SCOPE="all"         # all | bugs | features — which directories count as the backlog
ORDER=0             # 1: backlog order instead of the picker's ranking
# bypassPermissions: no prompts at all. The sessions push branches, open PRs and
# arm auto-merge on their own, so they run unattended by design; --mode auto is
# the middle ground if you want the risky calls to still stop and ask.
MODE="bypassPermissions"
REVIEW=1
MAX_ROUNDS=3

# Model per job, not one model for everything: the author of a fiddly change is
# the only session worth Opus tokens. Picking a task is one short answer, and a
# review is reading a diff against a checklist — Sonnet does both, at a fraction
# of the cost of a backlog worked end to end on Opus.
MODEL_SIMPLE="sonnet"
MODEL_COMPLEX="opus"
MODEL_REVIEW="sonnet"
MODEL_PICK="sonnet"
MODEL=""            # --model: forces every author session, review excluded
AUTHOR_MODEL="$MODEL_COMPLEX"   # what run_task settled on for the task in hand

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
        --mode) MODE="$2"; shift 2 ;;
        --model) MODEL="$2"; shift 2 ;;
        --model-simple) MODEL_SIMPLE="$2"; shift 2 ;;
        --model-complex) MODEL_COMPLEX="$2"; shift 2 ;;
        --model-review) MODEL_REVIEW="$2"; shift 2 ;;
        -h|--help) sed -n '2,/^set -/{/^set -/!p}' "$0"; exit 0 ;;
        *) echo "unknown option: $1" >&2; exit 2 ;;
    esac
done

die() { echo "grind: $*" >&2; exit 1; }

command -v claude >/dev/null || die "claude CLI not on PATH"
command -v gh >/dev/null || die "gh CLI not on PATH"
[ -d todo ] || die "no todo/ directory here"
case "$SCOPE" in all|bugs|features) ;; *) die "--only takes 'bugs' or 'features', not '$SCOPE'" ;; esac
mkdir -p "$STATE_DIR"

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
    local text_out="" model=""
    while [ $# -gt 0 ]; do
        case "$1" in
            --text-out) text_out="$2"; shift 2 ;;
            --model) model="$2"; shift 2 ;;
            *) break ;;
        esac
    done
    local prompt="$1"; shift
    local model_flag=() render=(python3 scripts/claude-stream.py)
    [ -n "$model" ] && model_flag=(--model "$model")
    [ -n "$text_out" ] && render+=(--text-out "$text_out")
    set +e
    printf '%s' "$prompt" \
        | claude -p --output-format stream-json --include-partial-messages --verbose \
            "${model_flag[@]}" --permission-mode "$MODE" "$@" \
        | "${render[@]}"
    set -e
    return 0
}

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
    claude_run --model "$AUTHOR_MODEL" "$1" --resume "$sid"
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
# Claude reads the backlog and names one id plus how hard it is. No tools, no
# edits: this is a one-shot text call, cheap enough to run before every task so
# the choice reflects what the previous task already changed. It answers
# "<id> <simple|complex>" — the second word decides which model writes the code,
# which is why the pick and the sizing are one call and not two.
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
            echo "Pick the SINGLE most valuable task to do next, from the ids listed"
            echo "above and no others. Weigh: user-visible damage (data corruption and"
            echo "unreachable actions first), how many users hit it, and cost to fix."
            echo "Prefer a cheap high-impact fix over an expensive one."
            echo
            echo "Then size it: 'simple' if it is a contained change — one or two"
            echo "files, a wording or layout fix, an obvious null/edge case, no"
            echo "schema and no new screen. 'complex' if it touches the database,"
            echo "the repository or sync logic, spans several layers, needs a"
            echo "design decision, or the spec is vague. When in doubt: complex."
            echo
            echo "Answer with exactly two words: <id> <simple|complex>. Nothing else."
        } | timeout 120 claude -p --tools "" --model "$MODEL_PICK" 2>/dev/null \
          | head -c 200 | tr -d '`' | tr '\n' ' '
        # `head` bounds what the command substitution can buffer — without it a
        # producer that never stops is read into memory until the OOM killer
        # takes the script (and whatever else is running) out. `head` closing
        # the pipe then SIGPIPEs the producer, which `set -o pipefail` would
        # turn into a fatal error, so the substitution ends on `true` instead:
        # bounded AND non-fatal. The fallback below handles the empty result.
        true
    )"
    picked="${picked:0:96}"
    local id size
    id="$(printf '%s' "$picked" | awk '{print $1}')"
    size="$(printf '%s' "$picked" | awk '{print tolower($2)}')"
    case "$size" in simple|complex) ;; *) size="" ;; esac
    # Checked against the list it was given, not just against todo/: under
    # --bugs/--features an id from the other directory is a hallucination too.
    if [ -n "$id" ] && printf '%s\n' "$list" | grep -qxF -- "$id"; then
        echo "$id ${size:-complex}"
        return 0
    fi
    # Picker unavailable or hallucinated an id: fall back to backlog order,
    # bugs before features, and to the careful model.
    echo "$(echo "$list" | head -1) complex"
}

# --in-order does its own picking, but the model still has to be chosen per task,
# so the size question is asked on its own. Same one-shot, tool-less call as the
# picker, minus the ranking; anything unparseable is 'complex', because paying
# for Opus on a small fix is cheaper than the reverse.
size_task() {
    local id="$1" path size
    path="$(task_path "$id")" || { echo complex; return 0; }
    size="$(
        {
            echo "Task spec from the backlog of an Android app:"
            echo '```'
            head -c 4000 "$path"
            echo '```'
            echo
            echo "Size it: 'simple' if it is a contained change — one or two files, a"
            echo "wording or layout fix, an obvious null/edge case, no schema and no"
            echo "new screen. 'complex' if it touches the database, the repository or"
            echo "sync logic, spans several layers, needs a design decision, or the"
            echo "spec is vague. When in doubt: complex."
            echo
            echo "Answer with exactly one word: simple or complex. Nothing else."
        } | timeout 120 claude -p --tools "" --model "$MODEL_PICK" 2>/dev/null \
          | head -c 100 | tr -d '`' | tr 'A-Z' 'a-z' | awk 'NF{print $1; exit}' | tr -cd 'a-z'
        # Bounded and non-fatal for the same reason as in pick_task: an unbounded
        # producer read into the shell's heap is what the OOM killer takes.
        true
    )"
    case "$size" in simple|complex) echo "$size" ;; *) echo complex ;; esac
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
    rm -f "$out"        # a stale report from an earlier round must not be read as this one's
    claude_run --text-out "$out" --model "$MODEL_REVIEW" \
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
    local task="$1" size="${2:-complex}" path sid prompt
    # Skip a bad id rather than killing the loop with it.
    path="$(task_path "$task")" || { echo "grind: no todo/{bugs,features}/$task.md"; return 1; }

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
        echo "grind: resuming session $sid for $task [$AUTHOR_MODEL]"
        author_resume "$sid"
    else
        sid="$(python3 -c 'import uuid; print(uuid.uuid4())')"
        echo "$sid" > "$sid_file"
        prompt="$(build_prompt "$task" "$path")"
        echo "grind: starting session $sid for $task [$size → $AUTHOR_MODEL]"
        claude_run --model "$AUTHOR_MODEL" "$prompt" --session-id "$sid"
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
        local round=1 review verdict failed=0
        while :; do
            review="$STATE_DIR/$task.review.$round.md"
            [ "$round" -gt 1 ] && cp "$STATE_DIR/$task.review.$((round - 1)).md" "$review.prev"
            echo
            echo "grind: review round $round on $(pr_url)"
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
                    echo "grind: the reviewer answered without a VERDICT line (see $review):"
                    head -c 400 "$review" | sed 's/^/  | /'
                    echo
                else
                    echo "grind: the reviewer produced nothing (see $review)"
                fi
                failed=$((failed + 1))
                if [ "$ASK" -eq 0 ]; then
                    if [ "$failed" -gt 5 ]; then
                        echo "grind: 5 failed review attempts — PR left open, NOT merged"
                        return 1
                    fi
                    echo "grind: --yes, waiting 15 min then retrying round $round (attempt $failed)"
                    sleep 900
                    continue
                fi
                printf "  [r]etry now  [w]ait 1h then retry  [s]kip, leave the PR open > "
                read -r answer </dev/tty
                case "$answer" in
                    r|R|"") continue ;;
                    w|W) sleep 3600; continue ;;
                    *) echo "grind: PR left open, NOT merged"; return 1 ;;
                esac
            fi
            failed=0

            case "$verdict" in
                APPROVE) echo "grind: review passed on round $round"; break ;;
                REQUEST_CHANGES) ;;
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

    # --task names an id but says nothing about its size, so it gets the careful
    # model; the picker sizes the ones it chooses itself.
    if [ -n "$START_TASK" ]; then
        TASK="$START_TASK"; START_TASK=""; SIZE="complex"
    elif [ "$ORDER" -eq 1 ]; then
        # Backlog order: the first id still open, skipped ones already filtered
        # out of REMAINING. No ranking call — only the sizing one.
        TASK="$(printf '%s' "$REMAINING" | awk '{print $1}')"
        SIZE="$(size_task "$TASK")"
    else
        PICKED="$(pick_task)"
        TASK="${PICKED%% *}"; SIZE="${PICKED##* }"
        case "$SKIPPED" in *" $TASK "*) TASK="$(printf '%s' "$REMAINING" | awk '{print $1}')"; SIZE="complex" ;; esac
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
    echo "grind: next up — $TASK    ($(open_tasks | wc -l) open, sized $SIZE)"
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

    run_task "$TASK" "$SIZE" || SKIPPED="$SKIPPED $TASK"

    [ "$ONCE" -eq 1 ] && { echo "grind: --once, stopping"; exit 0; }
done
