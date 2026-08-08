# Ветки, релизы, CI

Читать, когда трогаешь workflow'ы, релиз или закрываешь задачу вручную.
Правило «в develop и master — только через PR» продублировано в CLAUDE.md,
потому что нарушить его дороже всего.

## Branches and releases

Remote is `git@github.com:simane988/dosmotr.git` — **public**, and the "О приложении"
dialog links to it, so anything committed here is published. `local.properties` and
`keystore/` are gitignored and have never been committed; keep it that way, because on a
public repo a leaked `backend.token` is a leaked backend and a leaked keystore is
permanent.

```
feature/<name> ──▶ develop ──▶ release/<x.y.z> ──▶ master
                                     │
                                     └─▶ GitHub Release + `direct` APK
                                     └─▶ run artifacts: `store` AAB + `store` APK
```

- `master` is what is published — every commit on it is a shipped version.
- `develop` is where finished features accumulate.
- `feature/<name>` branches off `develop` and merges back into it.
- `release/<x.y.z>` branches off `develop`. **Pushing it is what publishes**, so the
  branch name is the version: `release/1.2.0` becomes `v1.2.0`. Both merge-backs — into
  `master` *and* into `develop` — are opened as pull requests by the release workflow with
  auto-merge on; without the second one the version bump CI made lives only on a branch
  nobody reads again.

**Never merge into `develop` or `master` locally.** Every arrow into those two branches
is a pull request on GitHub — the merge is the reviewable event, and a local merge pushed
straight to `origin/develop` lands the change with no review and auto-closes an open PR.
So "get this feature into develop" means: finish it on `feature/<name>`, push that branch,
open the PR, and hand the merge to GitHub's auto-merge — never run the merge locally.
Afterwards sync with `git pull`, never with a local merge.

## Closing a todo task

`scripts/close-task.sh <task-id>` is the whole flow; do not do the steps by hand:

```bash
scripts/close-task.sh feature-11                       # title = last commit subject
scripts/close-task.sh bug-3 --title "fix: …" --no-wait
scripts/close-task.sh bug-3 --body-file todo/.grind/bug-3.pr.md
```

**The PR description is prose in Russian** — what was wrong, what changed and why, how it
was verified — because the PR page is what gets read later, and a title plus a link to the
spec is not that. `--body-file` is how a session hands its own text over; the `Closes
<spec>` line is appended by the script. Without the flag the body is that link alone.

It refuses to run anywhere but a `feature/**` branch, or with a dirty tree, then:

1. pushes the branch and opens the PR into `develop` (reuses an already-open one), then
   waits until the PR head has check runs, pushing an empty commit if it has none;
2. arms `gh pr merge --auto --merge --delete-branch` and waits for the merge;
3. appends `**PR:** <url>` to `todo/{bugs,features}/<task>.md`;
4. moves that file **and every `<task>.*` / `<task>-*` asset** into `todo/done/`;
5. rewrites the matching `todo/README.md` links to `done/…` and marks the row ✅;
6. checks out `develop`, pulls, and deletes the local feature branch.

**`todo/` is gitignored**, so steps 3–5 are local-only — no commit, nothing pushed, and
in particular nothing that would require a direct push to `develop`. That is also why the
task file is edited *after* the merge rather than inside the PR: the PR would not carry it
anyway. `--no-wait` stops after step 2 and leaves the todo files alone; re-running the
script later picks the same PR back up and finishes.

## Workflows

`.github/workflows/ci.yml` is the only place the verification steps are written down, in
this order — cheapest first, so the emulator never starts for a commit that lint already
rejected:

```
decide ─▶ sync-develop ─▶ static (lint + detekt) ─┬─▶ unit ─▶ instrumented (API 35)
                       └─▶ secrets (gitleaks)  ───┘
                       └─▶ dependencies (PRs only)
```

It builds *without* backend credentials on purpose — that is what keeps a build with no
`backend.url` working, since the search screen's empty state depends on it.

Two mechanisms keep one commit from being tested twice, which is the whole point of the
`decide` job:

- **A push to `feature/**` is skipped once that branch has an open PR.** The `pull_request`
  run tests the same commit merged into its base, so both is pure waste. `decide` looks the
  PR up with `gh pr list --head` and every other job hangs off `needs.decide.outputs.run`.
  The check is limited to `feature/**`: `master`/`develop` are never the head of a PR, and a
  release branch must still be verified while its own merge-back PRs are open.
- **`release/**` is not a trigger in `ci.yml`.** `release.yml`'s `verify` job calls it
  through `workflow_call` instead, so a release branch runs the suite once, inside the run
  that builds the artifacts, rather than in a second pipeline racing it.

`release.yml`'s `publish` job is also the only place `store` is built and tested — three
artifacts from one Gradle invocation, one signing key, checked rather than assumed; see
`ai-docs/distribution-and-legal.md` for the checks and `store/publishing.md` for what a human
still has to do in the two consoles.

`sync-develop` merges `origin/develop` into the branch **before** anything is tested, on
feature pushes only, so a stale branch is tested as it will be merged. The merge commit
carries `[skip ci]`: the jobs after it already test the merged tree, and without the marker
pushing it would start a second identical run. A conflict fails the job with a message —
CI does not resolve conflicts.

Static analysis is baselined, so both tools fail on findings a commit *introduces*:

- `app/lint-baseline.xml` (regenerate with `./gradlew :app:updateLintBaselineDirectDebug` —
  the bare `updateLintBaseline` uses whatever AGP considers the default variant, which is
  not the one CI checks). The `lint`
  block in `app/build.gradle.kts` has `warningsAsErrors`, and switches off the checks that
  are decisions rather than defects: `MissingTranslation`/`HardcodedText` (the app is
  Russian-only) and `GradleDependency`/`AndroidGradlePluginVersion`/`OldTargetApi` (a
  version bump is its own change; CVEs are the dependency job's business). `OldTargetApi`
  also *only* fires on CI, whose SDK manager knows about a newer platform than this machine
  has installed — the kind of check that passes locally and fails in the pipeline.
  `checkDependencies` is off — with it on, lint is another step this machine cannot fit in
  RAM.
- `config/detekt/baseline.xml` (regenerate with `./gradlew detektBaseline`), configured by
  `config/detekt/detekt.yml` on top of detekt's defaults. Detekt is applied at the *root*
  project over `app/src`, so `./gradlew detekt` covers main, test and androidTest at once.

Both emit SARIF that lands in the repository's Security tab.

Security jobs, and why each one is where it is:

- `secrets` runs **gitleaks over the full history** on every branch — the repo is public and
  `local.properties` holds `backend.token`, and a secret deleted in a later commit is still
  published by the one that added it. No licence key while the repo is public.
- `dependencies` runs on pull requests only, because that is the one event
  `dependency-review-action` supports. Gradle has no lockfile for a scanner to read, so
  `gradle/actions/dependency-submission` resolves the graph first. The push side of that is
  `.github/workflows/dependency-graph.yml` (master/develop + weekly), which is what makes
  Dependabot alerts appear at all. **Both submit the same filtered graph** —
  `DEPENDENCY_GRAPH_INCLUDE_PROJECTS=^:app$` and
  `DEPENDENCY_GRAPH_INCLUDE_CONFIGURATIONS=^directReleaseRuntimeClasspath$` — for two reasons:
  unfiltered, the graph carries Gradle's own plugin classpath (AGP drags in Bouncy Castle,
  protobuf, netty), so an advisory against a build tool fails a PR over code no user runs;
  and the PR snapshot is diffed against whatever the base branch last submitted, so the two
  filters have to stay identical or the diff fills with packages nobody added.
  All of it needs **Dependency graph enabled** in the repository settings — without it the
  submission fails with "The Dependency graph is disabled for this repository".
- `.github/workflows/codeql.yml` is **not** part of CI: it costs a full compile, so it runs
  weekly against `develop` (and on `workflow_dispatch`). A scheduled run starts on the
  default branch, hence the explicit `ref: develop` in its checkout. `build-mode: manual` —
  autobuild guesses the Gradle invocation and can end up analysing nothing.

Auto-merge on the release PRs needs **"Allow auto-merge" enabled in the repository
settings**. Without it the workflow logs a warning and leaves both PRs open, which is the
same outcome minus the convenience.

Releasing needs six repository secrets, and CI fails loudly if any is missing:
`KEYSTORE_BASE64` (`base64 -w0 keystore/dosmotr-release.jks`), `KEYSTORE_PASSWORD`,
`KEY_ALIAS`, `KEY_PASSWORD`, `BACKEND_URL`, `BACKEND_TOKEN`. They exist only as secrets
and are written into `local.properties` for the length of one job, so the build has
exactly one way to find them either way.

Three more are **optional**: `DONATE_URL`, `DONATE_SBP`, `DONATE_USDT`. They go into the
same `local.properties` and are what puts the donation block into the published APK —
the Releases build is `direct`, so without them it ships with nothing to show. A release
must not fail over a wallet address, so a missing one is a `::warning::` rather than an
error; that warning is the only sign, and it is worth reading. When `DONATE_URL` *is* set the
job also fails if the built `direct` APK does not contain it — an empty destination is a
supported state, so a build that lost the block is otherwise green and silently wrong.
Setting them does **not** leak anything into a store build: `store` compiles the fields as
empty strings whatever `local.properties` says, and the job proves it on the artifact before
publishing (see `ai-docs/distribution-and-legal.md`).
