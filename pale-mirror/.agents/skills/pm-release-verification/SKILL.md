---
name: pm-release-verification
description: Verify Pale Mirror changes, packaged artifacts, release candidates, completion claims, and commit readiness with evidence-based gates. Use when asked whether a version or feature is done, before declaring implementation complete, for release audits, build/package verification, pre-commit checks, or deciding whether server deployment is safe. This skill verifies artifacts; use pm-test-server-ops separately to deploy them.
---

# PM Release Verification

Separate code existence, automated correctness, physical-world validation, and
player comprehension. A green build proves only the gates it actually ran.

The currently assigned executor owns verification, methodology, technical review
and scoped corrections. This skill does not assign a model, require delegation
or introduce an intermediate approval role. Follow the current user assignment
and canonical ledger; a single executor may implement and verify its own work.

## Scope the risk

1. Read `AGENTS.md`, `CONTINUITY.md`, and the relevant architecture flow.
2. For Pale Mirror 0.3 acceptance, also read
   `docs/living-frontier-0.3-release-audit.md` completely.
3. Classify the change as `docs`, `small-code`, or `critical-code`. Use
   `references/gate-matrix.md` to add module-specific gates.

## Gate applicability

Read `docs/engineering-agent-protocol.md` for lifecycle, evidence tiers/reuse,
method review and retries. It is the sole workflow authority. Local iterations
use relevant focused checks. Authorized private WIP checkpoints preserve bytes
and known failures; they are not acceptance and need no full milestone gate.
The following full risk gates apply at integration milestones/releases (docs
changes use the docs gate). Add only the scope-relevant matrix rows. Reuse
unchanged trustworthy evidence without mislabelling cached tests as fresh.

## Run applicable gates

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
state any unavailable live/restart harness explicitly. The gate matrix does not
mandate repeating each row on every correction. the executor evaluates a material new
method before expensive evidence when its validity is at risk, without waiting
for PM approval. Intrinsically
runtime-only failures use the protocol's bounded diagnostic path; no impossible
local reproducer or per-run approval gate.

Before claiming terminal evidence, the executor confirms that the exact candidate's
affected checks, applicable static/architecture constraints, evidence carrier
and retained receipts support the complete claim, with no targeted
`UNPROVEN`, `CONTRADICTED` or `PROVEN_NARROWER` criterion. the executor chooses the
order and form of that technical closure work by expected information gain,
cost and risk. Cheap checks should normally precede a terminal carrier when
they can invalidate it, but neither a fixed checklist nor one prescribed first
technique replaces engineering judgment. This is autonomous technical
self-review, not an additional PM gate or proof campaign.

For a player-visible correction, the exact identity must already be a protocol
`PRODUCT_CANDIDATE` before spending the terminal aggregate. A successful
aggregate promotes that same frozen identity to `RELEASE_CANDIDATE`; subsequent
source, harness or oracle edits invalidate only their dependent evidence and do
not justify a confidence rerun of unaffected gates. Release verification alone
never promotes it to `HUMAN_CANDIDATE`: the exact deployed artifact/world must
also pass the protocol's graphical full-pack real-client product preflight, with
actual frames reviewed against the whole relevant player-visible promise.

## Inspect before a commit or completion claim

For technical delivery, the executor reviews `git status`, the full diff,
generated/local files, architecture drift,
silent fallbacks, unbounded state, and missing recovery paths. Preserve user
changes. This is technical self-review; a separate PM review applies only in paired mode.
Use a concise Conventional Commit message only when asked to commit.

## Report accurately

Use the protocol's minimal receipt: source identity, exact commands/results,
executed reports/counts and remaining scope; add artifact/runtime/lifecycle
identity when that claim needs it. Recover missing report metadata from
existing artifacts before considering a repeat. Identify manual visual, co-op, usability, or
clean-room gates that remain. Say `automation-complete`, `feature-complete`, or
`product-validated` only when evidence supports that exact level. Deployment
and destructive world reset require the separate server-operations workflow.
