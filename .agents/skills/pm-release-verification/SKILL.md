---
name: pm-release-verification
description: Verify Pale Mirror changes, packaged artifacts, release candidates, completion claims, and commit readiness with evidence-based gates. Use when asked whether a version or feature is done, before declaring implementation complete, for release audits, build/package verification, pre-commit checks, or deciding whether server deployment is safe. This skill verifies artifacts; use pm-test-server-ops separately to deploy them.
---

# PM Release Verification

Separate code existence, automated correctness, physical-world validation, and
player comprehension. A green build proves only the gates it actually ran.

## Scope the risk

1. Read `AGENTS.md`, `CONTINUITY.md`, and the relevant architecture flow.
2. For Pale Mirror 0.3 acceptance, also read
   `docs/living-frontier-0.3-release-audit.md` completely.
3. Classify the change as `docs`, `small-code`, or `critical-code`. Use
   `references/gate-matrix.md` to add module-specific gates.

## Run gates

- `docs`: `git diff --check` and `./gradlew guardrails`.
- `small-code`: focused tests plus `./gradlew guardrails check`.
- `critical-code`: focused tests plus
  `./gradlew guardrails check :pale-mirror-neoforge:runGameTestServer
  :pale-mirror-neoforge:build :pale-mirror-neoforge:verifyPackagedJar`.
- Visuals changes: also run
  `./gradlew :pale-mirror-visuals:runGameTestServer
  :pale-mirror-visuals:build`; add `visualsIntegrationHarness` when genesis,
  packaging, restart, or cross-module materialization changed.

Run adapter or client smoke gates only when their integration is in scope, and
state any unavailable live/restart harness explicitly.

## Inspect before a commit or completion claim

Review `git status`, the full diff, generated/local files, architecture drift,
silent fallbacks, unbounded state, and missing recovery paths. Preserve user
changes. Use a concise Conventional Commit message only when asked to commit.

## Report accurately

List exact commands and results. Identify manual visual, co-op, usability, or
clean-room gates that remain. Say `automation-complete`, `feature-complete`, or
`product-validated` only when evidence supports that exact level. Deployment
and destructive world reset require the separate server-operations workflow.
