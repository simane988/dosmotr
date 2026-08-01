#!/usr/bin/env bash
# Close a todo task: open its PR into develop, let GitHub auto-merge it, record
# the PR link in the task file, move everything the task owns into todo/done/,
# then leave the checkout on an up-to-date develop.
#
#   scripts/close-task.sh feature-11
#   scripts/close-task.sh bug-3 --title "fix: keep the card in place after +1"
#   scripts/close-task.sh feature-11 --no-wait   # stop once auto-merge is armed
#   scripts/close-task.sh feature-11 --pr-only   # open the PR, merge nothing
#
# --pr-only is the half that runs before review: it pushes and opens the PR and
# stops there. Running the script again later, plain, picks that same PR up and
# finishes the job.
#
# todo/ is gitignored, so the bookkeeping is local-only: nothing here commits or
# pushes the task files, and nothing ever pushes to develop or master directly.
set -euo pipefail

cd "$(git rev-parse --show-toplevel)"

TASK=""
TITLE=""
WAIT=1
PR_ONLY=0
MERGE_METHOD="--merge"

while [ $# -gt 0 ]; do
    case "$1" in
        --title) TITLE="$2"; shift 2 ;;
        --no-wait) WAIT=0; shift ;;
        --pr-only) PR_ONLY=1; shift ;;
        --squash) MERGE_METHOD="--squash"; shift ;;
        -*) echo "unknown option: $1" >&2; exit 2 ;;
        *) TASK="$1"; shift ;;
    esac
done

die() { echo "close-task: $*" >&2; exit 1; }

[ -n "$TASK" ] || die "usage: scripts/close-task.sh <task-id> [--title <pr title>] [--no-wait] [--squash]"

BRANCH="$(git rev-parse --abbrev-ref HEAD)"
case "$BRANCH" in
    feature/*) ;;
    *) die "on '$BRANCH'; this only runs on feature/* branches" ;;
esac

[ -z "$(git status --porcelain)" ] || die "working tree is dirty; commit or stash first"

# --- locate the task file -----------------------------------------------------
TASK_MD=""
for dir in todo/bugs todo/features todo; do
    if [ -f "$dir/$TASK.md" ]; then TASK_MD="$dir/$TASK.md"; break; fi
done
if [ -z "$TASK_MD" ]; then
    [ -f "todo/done/$TASK.md" ] && die "todo/done/$TASK.md is already closed"
    die "no todo/{bugs,features}/$TASK.md found"
fi
TASK_DIR="$(dirname "$TASK_MD")"

# --- open (or reuse) the pull request ----------------------------------------
git push -u origin "$BRANCH"

PR_URL="$(gh pr list --head "$BRANCH" --base develop --state open \
    --json url --jq '.[0].url // empty')"

if [ -z "$PR_URL" ]; then
    [ -n "$TITLE" ] || TITLE="$(git log -1 --pretty=%s)"
    PR_URL="$(gh pr create --base develop --head "$BRANCH" \
        --title "$TITLE" --body "Closes \`$TASK_MD\`.")"
    echo "close-task: opened $PR_URL"
else
    echo "close-task: reusing $PR_URL"
fi

# --- make sure the PR head is actually being tested --------------------------
# `develop` requires every CI check, and a head commit with no checks blocks the
# merge forever rather than failing it. That is a real possibility here: the
# sync-develop job pushes its merge commit with [skip ci], and GitHub starts no
# workflow at all for such a commit. If the PR ends up sitting on one, an empty
# commit gives the pipeline something it will run on.
REPO="$(gh repo view --json nameWithOwner --jq .nameWithOwner)"
git fetch -q origin "$BRANCH"
git merge --ff-only "origin/$BRANCH" -q 2>/dev/null || true
HEAD_SHA="$(git rev-parse "origin/$BRANCH")"

CHECKS=0
for _ in $(seq 1 12); do
    CHECKS="$(gh api "repos/$REPO/commits/$HEAD_SHA/check-runs" --jq '.check_runs | length' 2>/dev/null || echo 0)"
    [ "$CHECKS" -gt 0 ] && break
    sleep 10
done

if [ "$CHECKS" -eq 0 ]; then
    echo "close-task: no checks on ${HEAD_SHA:0:7} after 2 min — re-triggering CI"
    # Keep the skip marker out of THIS message, in any form: GitHub honours it
    # anywhere in a commit message, so spelling it here would skip the very run
    # this commit exists to start.
    git commit -q --allow-empty -m "ci: re-run checks

The previous head carried the skip-CI marker, which starts no workflow, and
develop requires every check before a merge."
    git push -q
fi

if [ "$PR_ONLY" -eq 1 ]; then
    echo "close-task: --pr-only; nothing merged, todo files untouched"
    echo "$PR_URL"
    exit 0
fi

# --- hand the merge to GitHub ------------------------------------------------
if ! gh pr merge "$PR_URL" --auto $MERGE_METHOD --delete-branch; then
    die "could not arm auto-merge (is 'Allow auto-merge' on in repo settings?); PR left open: $PR_URL"
fi
echo "close-task: auto-merge armed on $PR_URL"

if [ "$WAIT" -eq 0 ]; then
    echo "close-task: --no-wait; todo files untouched, re-run without it to finish"
    exit 0
fi

echo "close-task: waiting for checks and merge…"
# Bounded: a required check that hangs would otherwise wedge this script — and
# any grind.sh loop above it — silently and forever. A failed poll is not fatal
# either; rate limits and flaky networks are normal over an hour of waiting.
POLLS=0
while :; do
    STATE="$(gh pr view "$PR_URL" --json state --jq .state 2>/dev/null || echo UNKNOWN)"
    [ "$STATE" = "MERGED" ] && break
    [ "$STATE" = "CLOSED" ] && die "PR was closed without merging: $PR_URL"
    POLLS=$((POLLS + 1))
    if [ "$POLLS" -ge 120 ]; then
        die "still not merged after an hour (last state: $STATE); PR left open: $PR_URL"
    fi
    sleep 30
done
echo "close-task: merged"

# --- record the link, move the task into done/ (local, gitignored) -----------
if ! grep -qF "**PR:** $PR_URL" "$TASK_MD"; then
    printf '\n**PR:** %s\n' "$PR_URL" >> "$TASK_MD"
fi

mkdir -p todo/done
mv "$TASK_MD" "todo/done/$TASK.md"
for asset in "$TASK_DIR/$TASK".* "$TASK_DIR/$TASK"-*; do
    [ -e "$asset" ] || continue
    mv "$asset" "todo/done/$(basename "$asset")"
    echo "close-task: moved $(basename "$asset")"
done

# Every index row pointing into bugs/ or features/ now has to point into done/,
# and gets a ✅ so the table still reads as a status list.
if [ -f todo/README.md ]; then
    python3 - "$TASK" <<'PY'
import re, sys
task = sys.argv[1]
path = "todo/README.md"
text = open(path, encoding="utf-8").read()
text = re.sub(rf"\((?:bugs|features)/{re.escape(task)}\.md\)", f"(done/{task}.md)", text)
text = re.sub(rf"(?m)^\| \[{re.escape(task)}\]", f"| ✅ [{task}]", text)
open(path, "w", encoding="utf-8").write(text)
PY
fi

# --- back to develop ----------------------------------------------------------
git checkout develop
git pull
git branch -d "$BRANCH" 2>/dev/null || true
echo "close-task: done — $TASK in todo/done/, on develop at $(git rev-parse --short HEAD)"
