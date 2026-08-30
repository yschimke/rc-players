@AGENTS.md

The file above is the repository's canonical agent instructions — the CI-enforced invariants and the
PR workflow. Everything below is Claude-Code-specific harness mechanics that no other agent can act
on. Nothing here restates a rule from `AGENTS.md`; if the two ever disagree, `AGENTS.md` wins.

## Claude Code

- **Subscribe to every PR you open**, on the same turn you open it (`subscribe_pr_activity`). Don't
  ask — tracking is the default; mention it in the reply. Stop on request with
  `unsubscribe_pr_activity` and push nothing further to that branch.
- **Keep a check-in scheduled while a PR you own is red or conflicted** (`send_later`, roughly an
  hour out). Webhook events miss CI successes and merge transitions, so events alone are not enough.
  Re-arm silently when nothing changed; stop once the PR is merged or closed.
- **PR activity events on tracked PRs are not no-ops.** Push the fix when the change is clear and in
  scope, `AskUserQuestion` when it is ambiguous or architecturally significant, skip silently only
  for duplicates and echoes of your own comments.
- **The iOS and Android lanes need real hosts.** `linkReleaseFramework*` only runs on macOS, and
  `third_party/rc-embedded-player` needs an Android SDK. On a Linux sandbox the Kotlin Gradle plugin
  disables the native targets with a warning — that warning is not a failure, and "it built here"
  does not mean the macOS job will pass.
