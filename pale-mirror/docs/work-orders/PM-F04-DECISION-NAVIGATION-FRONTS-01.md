# PM-F04-DECISION-NAVIGATION-FRONTS-01: make autonomous operations think, move and divide coherently

Revision: 1. Parent slice: F0.4. Risk: critical-code.
Status: ACCEPTED_LOCAL at 2026-09-12T01:14:35Z.
PM / architect: Sol. Senior tech lead and sole coder: Terra,
gpt-5.6-terra, reasoning high.
Workflow: [PM/TL protocol](../engineering-agent-protocol.md), revision
2026-09-11-PM-TL. This assignment supplies one complete stage outcome, not a
sequence of implementation approvals.

## Product outcome and acceptance

Make settlement and Hivemind action a traceable autonomous system rather than
scene-local or unit-type logic. Each side decides only from its perceived facts,
turns one retained strategic purpose into a tactical plan and bounded actor
directives, and executes one exact composite operation across COLD simulation
and naturally HOT Minecraft fronts without replacing actors, cargo, targets or
history. A player returning to an operation sees its current survivors,
positions and result, not a reset encounter.

Complete the existing F0.4 outcome in
[seamless foundation](../frontier-v3-seamless-foundation.md), including its
additional [execution-semantics](../frontier-v3-execution-semantics.md) row:

- exactly one canonical `DecisionAuthority` for each of the twelve settlements
  and one for the whole Hivemind. It owns perceived knowledge, doctrine,
  commitments, reconsideration epoch, stable policy ID/version and bounded
  decision provenance, but references rather than duplicates objectives,
  tasks, rosters, resources or custody;
- one closed common decision protocol with distinct registered settlement and
  hive policies. Missing, duplicate or incompatible policy ownership fails
  closed, and simultaneous reconsideration is deterministic while preserving
  the sides' asymmetric knowledge and physiology;
- one operation-owned `TacticalPlan` for every coordinated operation, retaining
  phase, bounded objectives, roles, formation constraints, permitted local
  behaviour, rendezvous, fallback and retreat without becoming a second roster;
- closed tactical and individual-behaviour policy registries behind stable
  descriptors. Generic operation, scene, entity and movement code executes
  traceable directives and typed observations but cannot originate or silently
  replace strategic purpose, formation, reinforcement, retreat or semantic
  destination;
- every objective/task/order names exactly one decision authority and epoch;
  every actor directive names exactly one tactical plan, front and current
  lease. Stale authority, plan or directive epochs fail closed across restart;
- canonical movement retains capability, semantic origin/destination and port,
  ordered checkpoints, formation constraints and a bounded navigation envelope.
  A HOT local-navigation lease may use real collision, doors, stairs, slopes,
  avoidance and combat motion only inside that envelope and advances only from
  observed checkpoint arrival. It cannot leave the envelope, choose another
  port/route, skip a checkpoint or mutate strategic intent;
- known obstruction becomes canonical evidence. Persistent blockage follows a
  bounded declared replan or abort policy; unknown route/target preconditions
  use the family's bounded evidence/approximation/abandonment rule, never hidden
  Minecraft state, force-loading or an invented successful path;
- one exact strategic operation owns purpose, global phase, complete roster,
  resource reservations and terminal result while deterministic typed children
  own only disjoint allocated actors/cargo/effects, local topology/cursor,
  objective and at most one physical lease. The complete operation is never one
  unbounded scene;
- child creation, join, cancellation, loss, partial success, rendezvous,
  compensation/return and terminal aggregation are canonical. The parent cannot
  complete while any child or allocation remains live, ambiguous or unaccounted;
- a bounded canonical coordinator orders and deduplicates cross-front projectile,
  blast, pursuit and cargo effects by exact cause, source/target and authority
  epoch across HOT/HOT, HOT/COLD and reversed-arrival cases. Scene boundaries do
  not shield targets and retries/restart do not duplicate consequences;
- use the same composite-operation/front contract for a settlement patrol and a
  Hivemind expedition. Do not introduce a combat-only parallel architecture.

Required evidence is exactly the existing F0.4 programme:

- deterministic simultaneous settlement/hive decisions from their own perceived
  facts, policy-specific bounded traces and negative ownership/hidden-knowledge
  cases;
- scene/movement rejection of strategic, tactical, port/checkpoint or stale-epoch
  substitution, including restart retention of authority/tactical epochs;
- bounded non-flat graybox/provider fixtures for slopes, stairs, doors, harmless
  local avoidance, envelope escape, checkpoint skipping and persistent blockage;
  this is not a natural-terrain campaign;
- two disjoint fronts of one exact operation progress independently without
  actor/cargo overlap, while projectile, blast, pursuit and cargo interactions
  cross front and mixed HOT/COLD boundaries exactly once under retry, reverse
  arrival, stale epoch and restart;
- one expedition retains the same parent, roster, allocations and child
  identities through assembly, travel, contact/work and return or retreat across
  COLD/HOT hand-offs and restart. Partial success/failure cannot duplicate,
  strand or silently return an actor/resource;
- leader or Overseer/Relay loss changes or degrades the same retained plan by a
  canonical decision transition, never scene-local replacement;
- a native return scenario proves current survivors/positions rather than reset;
  M0 canonical, M1 physical endpoint and M2 continuous execution are reported
  separately and no task-private Xvfb result is called human/M3 acceptance;
- a reproducible same-seed JFR demonstrates the current F0.4 path within existing
  budgets before any limit increase. No new limit or numeric performance promise
  is implied if the current budgets pass.

This closes the F0.4 foundation only. It is not final combat balance or content
breadth, natural-terrain/Foundry hardening, Create/rail integration, generalized
F0.5 recovery/failure ownership, F0.6 scale/observer-neutrality, human tactical
readability, deployment, public release or v2 removal.

## Context and continuation

- Canonical governance and ledger:
  `/home/rd/proj/pm-governance/pale-mirror`; protocol revision
  `2026-09-11-PM-TL`.
- Implementation worktree root:
  `/home/rd/proj/pm-f02c-projection-provider-snapshot-30`; branch naming is
  historical. Start from clean accepted structure candidate
  `2cbb17ef7d3a161f4587d83d1880c283cd1ee127`, tree
  `bab933dfd6531d51b4f94583418b14decbe75dc0`, parent accepted F0.3
  `aa2783b8`.
- Previous writer is the same Terra and returned a safe terminal packet. No
  task-owned process/port remains; unrelated PID `2330125` on `:25565` and all
  original-workspace WIP remain preserved.
- Normative owners are the contract's autonomous-humans/Hivemind and HOT/COLD
  sections; architecture invariants `frontier-v3-first-class-ai-authority`,
  `frontier-v3-operation-fronts`, `frontier-v3-local-navigation-envelope` and
  the existing process/scene SDK. Close the F0.4 portions of V3-AUD-036,
  V3-AUD-038, V3-AUD-047 and V3-AUD-048 truthfully; do not lower a debt ceiling
  or relabel incomplete materialization.
- F0.V through F0.3 evidence remains valid only for its recorded identities and
  scopes. Reuse the prepared-runtime, persistent-client, causal milestones,
  four-slot complete-matrix acceleration and exact custody model; do not re-prove
  their infrastructure or weaken their invariants.
- The flat graybox is a provider fixture, not real-terrain proof. Existing
  operation, patrol, mobilisation, actor/cargo and scene code is context to
  converge, not an instruction to retain a disproved implementation.

Terra owns current-path investigation, architecture-consistent technical design,
decomposition, algorithms, schemas/codecs, source/test/harness changes, test
adequacy, diagnosis, technical self-review and integration through this whole
working result. Related in-stage runtime or harness repairs and replacement of a
disproved approach are autonomous. There is no METHOD_READY, file allowlist,
per-run approval or PM technical review.

## Authority and resources

- Writable boundary: F0.4-related source, tests, build/harness wiring and
  technical implementation documentation in the implementation worktree, plus
  isolated task-owned temporary/runtime/evidence roots. Required fresh-world
  schema/envelope change is authorized; no compatibility reader for rejected
  development worlds.
- Private coherent commits are allowed. No push/publication, production or live
  server deployment, v2 removal, history rewrite, unrelated service/data
  mutation, broad repository cleanup or destructive evidence deletion.
- Use task-private loopback ports and Xvfb if physical client evidence needs a
  display; physical `:0` remains excluded and private Xvfb is not human evidence.
  Preserve PID `2330125`, accepted F0.3 native evidence and original workspace
  WIP.
- Reuse existing caches and retained evidence. Local iterations use the cheapest
  faithful tests; one terminal critical gate follows a coherent candidate. A
  genuinely necessary newly started complete native matrix uses the established
  four isolated workers, but focused/native reference flows need not be inflated
  into a complete matrix. Useful diagnostic/recovery retries are autonomous;
  confidence-only reruns and infrastructure speed proof are excluded.
- Retained raw evidence stays within the project 64 GiB cap. External provider,
  runner provisioning, public repository, deploy or destructive cleanup needs
  separate authority.

Terra selects proportionate faithful verification and may repair/retry ordinary
failures without asking. A real product, public/persistent-contract, external
resource or authority conflict is `NEEDS_DECISION`; a red test is not.

## Chronology and supervision

Whole blocker begins at explicit F0.4 dispatch. F0.3 and structural convergence
are accepted parent stages, not part of its active duration. Record meaningful
implementation/build/focused/native/JFR intervals and results, not a heartbeat
diary. While EXECUTING, PM performs one bounded liveness check after each ten
minutes of Terra silence and one hourly whole-blocker product/cost audit. These
checks do not inspect intermediate implementation or approve technical method.

Two consecutive hourly audits without meaningful progress or uncertainty
reduction trigger the protocol's impasse review before another equivalent cycle.
Terra owns technical resolution; PM resolves only product, priority, resource or
authority decisions.

## Delivery and next boundary

Return one coherent F0.4 result mapping every existing criterion above to its
automated, integration, native, restart and JFR evidence. Include exact candidate,
schema/envelope and packaged identities; architecture/debt status; technical
self-review; materialization levels and unproved human claims; remaining risks;
worktree/WIP and owned-process state. Do not return a stream of implementation
checkpoints for PM permission.

PM accepts product/architecture conformance or returns one consolidated existing
contract gap without prescribing the fix. F0.5 cannot start before F0.4
acceptance.

## Acceptance record

PM independently accepted the F0.4 product/architecture boundary at clean
candidate `c8be658f01ac5adb1a26a489c8b89648427a758f`, tree
`8cc658a8c4fc1b014153e637e709193407278c0c`. The packaged NeoForge JAR SHA-256
is `45fb5d4bf2e1ba9d9131033550c64cfd3f45fe25f2153208dcb0a008a64096ce`.

The retained terminal gate contains 310/310 GameTests and 1,337 JUnit tests in
264 reports with zero failures/errors. Its focused evidence closes exact
thirteen-authority ownership, fail-closed tactical/individual policy binding,
bounded door/stair/local-avoidance navigation, disjoint parent/child fronts and
four-kind mixed HOT/COLD cross-front once-only/restart behavior. One retained
Hivemind parent covers assembly, travel, HOT contact, restart and return.

The native receipt
`build/f04-native/hive-return-c8be658f-20260912-0604.json` has SHA-256
`f90199be4757dcab08646975ecf5e3d05b470d1085386469771a5c76d4c9b922`;
its bound manifest SHA-256 is
`d46afa170e57ae86e49bcdf039cd68b8ab28c1945d1e60d0dfbe8e2a560cc9c7`.
It retains the same four exact bioforms and their physical/canonical positions
across graceful same-world restart while advancing the return cursor from zero
to five. This proves M0, M1 and bounded M2 for the retained return path, not
human/M3 comprehension.

The same-seed JFR SHA-256 is
`fe835fff8e30481aece10519c292b3d6f8c418fec73c96d4406456a8be753974`.
It has no `jdk.DataLoss`; the fourteen active pre-shutdown samples remain within
the existing budget, and the later 104.5 ms samples are graceful-save shutdown,
not an increased runtime limit. No task-owned process/port remains; unrelated
PID `2330125` on `:25565` is preserved. Schema 147/envelope 58 are accepted as
the authorized fresh-world F0.4 cut. Natural terrain, broad combat content,
human/M3, deployment/publication, F0.5/F0.6 and release remain outside this
acceptance.
