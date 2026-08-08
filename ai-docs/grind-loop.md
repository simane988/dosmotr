# Цикл `grind.sh`: как бэклог закрывается сам

Читать, только если правишь саму обвязку (`scripts/grind.sh`,
`scripts/close-task.sh`, `scripts/claude-stream.py`). Сессиям, которые делают
задачи из бэклога, это не нужно — и они за это не платят.

## Working the whole backlog

`scripts/grind.sh` runs the backlog down to nothing. Per task: pick it, implement it in a
full Claude Code session, open a PR, have a second session review that PR until it passes,
then merge and close it with `close-task.sh`. Repeat.

```bash
scripts/grind.sh                 # asks before each task
scripts/grind.sh --yes           # unattended
scripts/grind.sh --task bug-4    # start here, then carry on picking
scripts/grind.sh --once          # one task, then stop
scripts/grind.sh --no-review     # straight to merge, no review rounds
scripts/grind.sh --rounds 5      # more review rounds before it gives up
scripts/grind.sh --bugs          # only todo/bugs/ (--features, or --only bugs|features)
scripts/grind.sh --in-order      # backlog order instead of the picker's ranking
scripts/grind.sh --model-complex opus --model-simple sonnet --model-review sonnet
scripts/grind.sh --model-pick haiku --model-size sonnet     # ranking vs sizing
scripts/grind.sh --effort-author max --effort-review high   # or --effort for all jobs
scripts/grind.sh --escalate-after 3    # hand a struggling author to Opus later
scripts/grind.sh --no-escalate         # never; --model implies this too
scripts/grind.sh --autocompact 150k    # or 'auto' (claude decides), or 'off'
scripts/grind.sh --no-hotkey     # do not read the keyboard while a task runs
```

**Press `f` to stop after the task in hand.** Ctrl-C stops the run where it stands,
which mid-review means a PR left open and a session to resume by hand; `f` lets the
current task finish — review, merge, `close-task.sh` — and the loop then exits instead
of picking the next one. `c` cancels the request while the task is still running, and
`q` during a task means the same as `f` (there is no safe way to abandon a task
mid-flight). The request is the file `todo/.grind/stop`, so `touch` on it does the same
thing from another terminal or over ssh, which is the way in when the run has no
keyboard — `--no-hotkey`, or a shell with no controlling terminal, where the key reader
is switched off by itself.

A background reader owns the keyboard while a task runs, so it is handed back around
every prompt the script asks (`ask_tty`): two readers on one tty and the answer lands in
whichever won the race. Read this together with "watch, do not type" above — a stray
`f` is now a request to stop.

**Run it from a terminal yourself, not from inside a session** — each task is a real
`claude` process in the foreground of that terminal, so the work is visible as it happens.
No background agent; the script is only the loop.

**Every session is `claude -p`, not an interactive one, and that is not negotiable.** An
interactive session does not exit when the work is done — it returns to its prompt and
waits, so the script above it hangs until someone presses Ctrl-C, and the review and merge
phases never run. Print mode ends on its own. To keep it watchable the sessions stream
`--output-format stream-json --include-partial-messages --verbose` through
`scripts/claude-stream.py`, which renders thinking, tool calls and their results the way an
interactive session shows them. So: watch, do not type. `--text-out` on that filter is also
how the reviewer's reply is captured for the `VERDICT:` line, and `--limit-out` is how the
usage-limit reset time is (see below).

**Everything is timestamped**, both the script's own lines and every discrete event inside
a session — session start, each tool call, each tool result, the end of a run. Streamed
prose is not: it arrives a character at a time. A backlog run lasts hours and the log is
what gets read afterwards to see which task ate the evening and how long it sat waiting on
a limit.

**The prompt goes in on stdin, never as a trailing argument.** `--tools` is variadic, so
anything after it — the prompt included — is read as one more tool name, and claude exits
with `Input must be provided either through stdin or as a prompt argument when using
--print`. That is exactly how a review round once produced an empty report, which the loop
then read as "no verdict" and treated as an approval. An empty report is now a failure that
leaves the PR open instead.

Since nobody can answer, the task prompt tells the session to decide open questions itself
and state the assumption — a question there just ends the turn with the task unfinished,
which the phase-1 check then reports as "opened no PR".

**The task prompt also spends a paragraph on keeping the context small, and it is not
politeness.** Every turn re-sends the whole conversation: measured over this project's
transcripts, 523M cache-read tokens against 26.6M written and 3.5M produced — about half
the bill is re-reading what earlier turns put there, and a third is putting it there. So
the prompt asks for CodeGraph instead of whole-file reads (`LibraryScreen.kt` is 47 KB,
`DetailScreen.kt` 53 KB), for builds redirected to a log with only the failing tail read
back (gradle results of 107 KB and 98 KB are in the logs), for `--stat` before `diff`, and
for screenshots taken once and described rather than re-read — one session read the same
PNG seven times. A session that fills its context dies half-done, and that looks like a
usage limit rather than what it is.

- **Picking** is one tool-less `claude -p` call per iteration, fed the open ids and
  `todo/README.md`, answering `<id> <simple|complex>`. It reruns before every task so the
  choice accounts for what the last one changed. An unusable answer, or an id outside the
  list it was given, falls back to backlog order, bugs first.
  **It runs outside the repository, on purpose** (`$TMPDIR/dosmotr-grind-pick`), with
  `--system-prompt`, `--strict-mcp-config` and `--effort low`. Everything it needs is in
  its own prompt, and a session started *here* is charged for the whole working
  environment instead: measured on the real prompt, **33.4k input tokens against 4.4k**,
  for an answer of two words. CLAUDE.md is discovered from the working directory upwards,
  which is why the cheap trick is a `cd` — and why it is only safe for these two calls,
  which have no tools and never touch the tree. `--bare` would do the same in one flag but
  refuses to read the OAuth login (*"Not logged in · Please run /login"*), so it needs an
  `ANTHROPIC_API_KEY`, which is a different bill. The sizer (`--in-order`) goes the same
  way.
- **`--bugs` / `--features` narrow the backlog** (`--only bugs|features` is the same thing
  spelled out): the picker only sees that directory, and so does the "nothing left to do"
  check. `--task` still reaches either directory — naming an id explicitly outranks the
  filter.
- **`--in-order` replaces the ranking with backlog order** — bugs before features, then by
  number, so `feature-20` is followed by `feature-21`. Ordering is `sort -V` per directory,
  not `ls`, which would put `feature-9` after `feature-21`. The task is still sized by its
  own one-word `claude -p` call, so the per-job model choice survives; only the "what is
  most valuable" question is dropped.
- **The model is per job, not per run — and there are four jobs.** *Picking* (which id
  next) is Haiku: it ranks ids that are already in the prompt, and the worst it can do is a
  suboptimal order. *Sizing* (simple or complex) is Sonnet and is asked separately, about
  the one task that was picked, because that word decides which model writes the code —
  measured on four real specs, Haiku disagreed with Sonnet on two and sized
  «настоящие миграции Room» as `simple`. *Authoring* is Sonnet on `simple`, Opus on
  `complex`. *Review* is Sonnet always.
  **Sizing leans towards `simple`**: `complex` needs one of a closed list (schema or
  migration, `TrackerRepository`/DAO/sync, a new screen or a product decision, a spec that
  does not say what the result should be), and anything unanswered — an unreachable model,
  a hallucinated id, `--task <id>` — is `simple` too. That used to be `complex`, which
  quietly made Opus the default. **Two review rounds ending in REQUEST_CHANGES hand the
  author session to Opus mid-conversation** (`--escalate-after N`, `--no-escalate`); the
  switch costs one re-cache of the whole conversation, since a different model is a
  different cache, which is why it happens at round two rather than round five and why
  `--model` (an explicit choice) disables it. The choice is written to
  `todo/.grind/<task>.model` so a resume after a usage limit continues the conversation on
  the model it started on. `--model` forces the author sessions only — **review is Sonnet
  either way**, deliberately, since it reads a diff against a checklist.
- **Effort is per job as well** — `claude --effort`, set with `--effort-author` /
  `--effort-review` / `--effort-pick`, or `--effort` for all three
  (`low|medium|high|xhigh|max`). `high` for the author and the reviewer, `low` for the
  picker, which answers two words and gets nothing from thinking harder about it. High on
  both of the other two is the deliberate default: the
  author writes into a codebase whose invariants cost a wiped library or a rejected build
  to break, and the reviewer is the only thing between a wrong diff and `develop`. A bad
  level is rejected at startup rather than by claude — sent through, it would fail one
  session at a time, after the model and prompt are paid for, and the loop would read that
  as "the session ended early" and retry it five times. A claude build without `--effort`
  is detected once from `--help` and the flag is dropped with a warning.
- **Review is a separate session, deliberately.** The author session ends its turn at
  `close-task.sh <task> --pr-only` — PR opened, nothing merged. A `claude -p` reviewer
  then reads the diff against the task spec and CLAUDE.md's invariants, with read-only
  tools (it reports, it never fixes), and ends with `VERDICT: APPROVE` or
  `VERDICT: REQUEST_CHANGES`. On request-changes the findings are resumed **into the
  author's existing conversation**, so it fixes with the whole context of having written
  the code. Merge is armed only after an approve.
  **The diff is handed to the reviewer, not fetched by it**, and it has no `Bash` —
  `Read,Grep,Glob` only. Left to run `git diff origin/develop...HEAD` itself it produced
  the single largest tool result in this repo's logs, 289 KB, which is ~70k tokens parked
  in the context for the rest of the round. The prompt carries `--stat` plus the diff
  capped at `REVIEW_DIFF_MAX` (80 KB), and says to `Read` the files for anything cut.
  **Rounds after the first resume the same reviewer session** and are sent only
  `git diff <last reviewed sha>..HEAD`: it already holds the spec, the diff and its own
  findings, so verifying five points does not need a cold session at ~29k tokens. It is
  still not the author's session, which is what the separation was for. The session id is
  kept only once a round has produced a verdict, so a reviewer that died before its
  session existed cannot be resumed into an error — that round starts cold instead, and
  `todo/.grind/<task>.review.head` is the sha it last saw (a force-push that orphans it
  falls back to a full review). Three rounds by default, then it asks (`--rounds N` to raise it);
  under `--yes` it gives up and leaves the PR open rather than merging unreviewed work.
  **A reply with no `VERDICT:` line is a failed round, never an approval.** It used to
  count as approve, and that is exactly how PR #14 and PR #23 merged unreviewed: the
  reviewer had hit a usage limit and answered `You've hit your session limit`, which is
  neither empty (so the empty-report guard missed it) nor a verdict. Such a round is
  retried rather than counted — five automatic retries, then the PR is left open. Reports
  are kept as `todo/.grind/<task>.review.<n>.md` and the file is deleted before each
  round, so a stale report cannot be read as this round's answer.
- **Long author sessions summarise themselves.** `--autocompact` (100k tokens by default,
  `auto` to let claude decide, `off` to disable) is passed to the author sessions only — a
  reviewer round is one turn and never gets near it. Measured on this project's transcripts:
  sessions peak at 200–300k tokens of context and automatic compaction fired exactly once in
  148 sessions, and that once was triggered by hand. Without it the last turns of a long task
  each re-read a quarter of a million tokens; with it the context sawtooths instead of
  growing, at the price of a summarisation pass and a cold cache after each one.
- **Cache-friendliness costs one flag.** `--exclude-dynamic-system-prompt-sections` moves
  cwd, environment and **git status** out of the system prompt — the cached prefix — and into
  the first user message. Git status changes with every commit, so without it every session
  re-pays for everything after it: measured across a change of git state, 26.1k tokens
  re-cached without the flag against 21.0k with it. It is ignored when `--system-prompt` is
  given, so the picker and the sizer do not get it.
- **Nothing is lost at a usage limit.** Each task gets a fixed `--session-id`, stored in
  `todo/.grind/<task>.session`. When a session ends without the task reaching
  `todo/done/` — limit, Ctrl-C, anything — the script offers resume / wait an hour and
  resume / skip / quit, and resume continues *that* conversation with its whole context.
  Skip and quit both print the `claude --resume <id>` needed to come back later.
- **A usage limit is waited out to the minute, not guessed at.** The dying session says
  when the limit lifts — as an epoch (`Claude AI usage limit reached|1754661600`) or in
  words (`resets 3pm`, `will reset at 15:00 (UTC)`) — and `scripts/claude-stream.py`
  parses either form into `todo/.grind/limit` as `<epoch>\t<local time>`. The loop then
  sleeps until that moment plus two minutes and resumes, in phase one and between review
  rounds alike, with someone at the keyboard or not: the old fixed 15 minutes was either
  twelve pointless retries against a three-hour limit or an idle hour against a
  two-minute one. Guard rails, because the text comes from a machine that can change its
  wording: a reset already in the past waits a token minute, one further out than six
  hours is capped there, and a session that said nothing falls back to 15 minutes.
  Anything that cannot be parsed as JSON — claude's stderr included, which is one of the
  places the limit is announced — is printed as it came rather than swallowed.
- **Progress is measured by artefacts, not by claims.** Phase one ends when `gh pr list`
  shows an open PR for the branch, phase three when `todo/done/<task>.md` exists. A
  session that says it finished without either having happened is treated as interrupted.
- Sessions run with `--permission-mode bypassPermissions` — no permission prompts at
  all, which is the point of an unattended loop, and worth knowing since those sessions
  push branches, open PRs and arm auto-merge by themselves. `--mode auto` still stops on
  the risky calls; `--mode acceptEdits` prompts for everything but file edits. The
  task prompt spells out the CLAUDE.md rules: branch off develop, update the fakes with
  the DAO, run unit tests + lint + detekt, never merge locally.

Auto-merge needs **"Allow auto-merge"** in the repository settings (the same setting the
release PRs depend on); without it the script stops and leaves the PR open rather than
merging it another way.

**`--auto` waits for required checks and nothing else.** `develop` is protected with all
seven CI checks required:

```
Decide whether to run · Merge develop into the branch · Lint and detekt · Secret scan
Dependency review · JVM tests · Instrumented tests (API 35)
```

Those are the `github-actions` check names; `Android Lint` and `detekt` also appear on
commits but come from code scanning (`github-advanced-security`) and are deliberately not
required. `strict` is off — a branch does not have to be rebuilt on top of the newest
`develop` before merging, because `sync-develop` already merges `develop` into it. Admins
are not exempt (`enforce_admins` is false, so the setting can still be lifted by hand when
something is genuinely stuck).

Before this was turned on the branch was unprotected, which meant auto-merge landed a PR
the moment it opened — that is how PR #10 merged while lint was still running. If a merge
ever happens instantly again, that is the first thing to check.

Two consequences worth knowing, both of which cost a confused half hour once already:

- **`--delete-branch` removes the branch the instant the merge lands**, and any run still
  in flight for it fails in `actions/checkout` with `fetch ... failed with exit code 1`.
  That failure is bookkeeping, not a broken build — the run on `develop` is the verdict.
- **A required check that never starts blocks the merge forever**, unlike one that fails.
  `sync-develop` pushes its merge commit with the skip-CI marker, and GitHub starts no
  workflow for such a commit — so a PR whose head is that commit would wait out
  `close-task.sh`'s hour and stay open. That is why the script checks for check runs on
  the PR head and pushes an empty commit when there are none.
  **The marker counts anywhere in a commit message, not just in the subject.** A commit
  that merely *mentions* it in prose is skipped too — that is how the first attempt at
  this very change ran no pipeline at all. Write it as "skip-CI marker" in messages.

Nothing about the release is typed by hand. `.github/workflows/release.yml` reads the
version out of the branch name, bumps `versionCode` in `version.properties`, commits that
back with `[skip ci]` (without which the push would start the workflow over), builds the
three signed artifacts, attaches the `direct` APK to a GitHub Release tagged on the release
branch (the two `store` ones stay as run artifacts for the manual upload), and finally opens
the two merge-back PRs (`→ master`, then `→ develop`) with `gh pr merge --auto`.
`version.properties` is the only place a version lives — `app/build.gradle.kts` reads it,
so do not put literals back into `defaultConfig`, and do not edit the file on a release
branch by hand: CI rewrites it there.
