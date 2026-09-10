# Pale Mirror engineering operating model v4

Status: binding for all work after `PM-F02C-AFTERMATH-SEAM-03`. This document
supersedes `engineering-agent-operating-model-v3.md`. The detailed safety,
liveness and economy rules remain in `engineering-agent-protocol.md`.

## Objective

Optimize accepted player-facing capability per hour. A builder is autonomous
inside a bounded product order; the supervising engineer owns the contract and
acceptance, not the builder's implementation sequence. Machine evidence, not an
agent transcript, carries claims between fresh contexts.

## Roles and write ownership

### Supervising engineer

- owns the player promise, architecture, invariant set, work-order boundary,
  proof adequacy and final acceptance;
- writes only normative Markdown and declarative architecture;
- reviews at explicit event boundaries, never watches implementation in
  progress or prescribes helpers, algorithms or file-by-file edits;
- consolidates findings into one outcome-level return and decides evidence
  reuse, expensive-run value and context replacement;
- never writes production code, tests, scripts, build/CI or executable config.

### Builder

- is one fresh `gpt-5.6-terra` agent with reasoning `high` per bounded slice or
  successor and is the sole executable-code writer;
- owns diagnosis, design, algorithms, files, focused tests, ordinary fixes,
  command sequencing, task cleanup and a coherent private commit;
- receives outcomes, owner seams, invariants, exclusions and acceptance facts,
  not an implementation recipe;
- returns only a named exception, a requested method-admission packet or one
  complete terminal packet. It does not ask permission for ordinary in-scope
  repair/test transitions.

### Challenger

- is fresh, read-only and independent of the builder's reasoning transcript;
- inspects stable source and existing receipts only; it never edits, runs broad
  tests, contacts the builder or proposes a competing implementation;
- attempts to falsify owner ordering, recovery, negative controls, carrier
  construction and boundedness, then reports findings to the engineer;
- is used once at terminal review for critical code and once before expensive
  evidence only under the method-risk trigger below.

There is one writer. Do not fan out competing implementations, let reviewers
mutate the candidate or make the engineer a hidden second coder.

## Workspace and context isolation

- Every new slice and every successor after a semantic return uses a fresh
  builder context and dedicated branch/worktree at an exact checkpoint.
- Engineer, builder and challenger do not share mutable output, worlds or
  process custody. The builder owns its complete task process tree.
- A failed candidate may be committed privately as `WIP checkpoint`; it is not
  accepted, merged or published evidence. A successor may retain useful bytes
  while re-proving only changed or previously missing claims.
- Governance is committed before a builder starts. Accepted builder commits
  retain authorship when integrated.

## Builder context discipline

- Start every new builder and successor with a fresh agent context. Do not fork
  the full engineer conversation or replay predecessor debugging transcripts.
  The spawn message is only a routing note to the active work order.
- The builder context packet is bounded to: the scoped `AGENTS.md` and current
  `CONTINUITY.md`; one active work order; the exact baseline commit/tree and
  worktree/resource custody; the specifically named contract/architecture
  sections; reusable receipt identities; and the still-open outcomes,
  exclusions and stop conditions. Historical orders and raw logs are references
  to inspect only when the active order names an unresolved fact from them.
- Do not make a critical inherited constraint depend on a vague pointer such as
  "reuse the prior approach". When correctness depends on an authoritative
  artifact property or an accepted reference contract, the packet records the
  exact fact and its evidence handle (for example path/hash/dimensions), and
  names the exact symbol or assertion shape that must remain equivalent. Keep
  this to the minimum facts needed for the outcome; it is not permission to
  copy predecessor reasoning or prescribe the implementation.
- A successor inherits bytes and machine evidence through one clean private
  checkpoint, not the predecessor's reasoning. Its order states which facts are
  accepted and must not be rediscovered, which claims remain open, and which
  prior executions must not be repeated. It owns the new diagnosis and design.
- One consolidated semantic correction is the maximum for one builder context.
  A second rejected method or terminal semantic return closes that context even
  when the remaining code change appears small. Preserve a clean WIP checkpoint,
  verify task-process cleanup and start a fresh successor with a shortened order.
- Treat context accumulation as engineering debt when the next instruction
  needs a history lesson to be understood, the builder confuses evidence tiers
  or environments, or accepted facts are repeatedly re-proved. Resolve it by
  shortening the order and replacing the context, not by adding more narrative.
- Context replacement is not micromanagement: the fresh builder retains full
  freedom over implementation, helpers and focused verification inside the
  bounded outcome. Do not add heartbeats or intermediate design approvals to
  compensate for context risk.

## Bounded product order

A work order contains exactly one product outcome, its canonical/physical
owners, observable invariants and failure/recovery behavior, explicit
exclusions, a cheapest-faithful claim/evidence map, authority/resource bounds,
and terminal identities. Expected paths are navigation only.

If two or more owners participate, name the seam. Its admission test must begin
before either owner has manufactured the desired intermediate state and execute
the real production composition/order. Static registry assertions, direct
helper calls and preinstalled claims/receipts remain component evidence.

## Event-driven lifecycle

`FRAMED -> CHECKPOINTED -> BUILDING -> SEAM_READY -> METHOD_ADMITTED ->`
`EXPENSIVE_READY -> DELIVERED -> CHALLENGED -> ACCEPTED`

1. **Frame.** Engineer defines outcome/invariants and names a plausible defect
   that must fail each expensive claim.
2. **Checkpoint.** Preserve an exact clean baseline or explicit private WIP
   checkpoint; start a fresh isolated builder.
3. **Build.** Builder autonomously implements and iterates cheap deterministic
   component and owner-seam tests.
4. **Seam-ready.** Builder produces a compact method-admission packet mapping
   each claim to its real subject, initial control history, production owners,
   oracle, defect-sensitive negative/recovery case and evidence tier.
5. **Method-admitted.** Builder normally self-admits. A fresh read-only
   challenger is mandatory before expensive work only when the immediately
   preceding terminal review rejected that same carrier/oracle as synthetic or
   non-faithful, or the new carrier introduces a previously unreviewed
   cross-owner proof boundary. This is one event review, not WIP supervision.
6. **Resource-admitted.** Before JVM/native/CI work, exercise create, bounded
   write, fsync, atomic rename and delete on every actual temp/evidence/runtime
   filesystem. `df`, free inodes and configuration text are not substitutes.
7. **Verify.** Run one complete local milestone gate for the final candidate.
   Native/CI follows only for claims unavailable below that tier.
8. **Deliver.** Wait for owned processes, keep compact receipts, clean owned
   disposable state, commit coherently and return one complete packet.
9. **Challenge and decide.** A fresh terminal challenger reviews critical code;
   the engineer verifies minimum identities/receipts and accepts or returns one
   consolidated invariant-level correction.

## Evidence ledger and reuse

Every receipt binds exact commit/tree, relevant content identity, command/task,
start/end or elapsed time, exit, result counts, artifact hashes, evidence path
and process cleanup. A green subtask remains valid for the same immutable
candidate unless its inputs were changed or concrete evidence contradicts it.
An aggregate green count does not prove a changed test ran. A receipt for a
new or modified test must retain a target-specific discovery/execution marker
(exact test or unique batch identity and count) and compare it with the prior
selection when that selection can change. A missing target, reduced count or
namespace/filter mismatch is a failed method, not a smaller green suite.

When a composed gate fails after an earlier leaf completed, classify the first
failure and retain the completed leaf. After an infrastructure correction on
the same candidate, run only the failed or not-yet-executed leaf where task
ordering permits; do not repeat GameTests, packaging or native work merely to
obtain one monolithic green console. Acceptance may compose exact-identity
receipts, and must state that composition explicitly.

An exception is not a terminal packet unless it contains the available exact
identity and receipts. Required fields are:

- checkpoint, HEAD, parent and tree; clean/dirty state and changed paths;
- full commands, durations/exits and the first causal failure;
- full artifact and compact receipt hashes, not prefixes;
- claim-to-receipt mapping and which claims remain unexecuted;
- owned process/job/port/display/world state and cleanup result;
- resource probe result and every retained task root.

Missing fields classify the handoff as `DELIVERY_INCOMPLETE`; the engineer may
read existing artifacts to recover them, but does not infer success.

## Resource and storage discipline

- Each builder declares a task-private temp/evidence root on a filesystem whose
  write probe passed. Global `/tmp` is not an implicit build resource.
- Tests and launchers receive that root explicitly where their runtime supports
  it. A host quota error is an infrastructure exception, not a product failure.
- At most one current local-heavy root and one provider root survive per order.
  Reviewed raw worlds, caches and logs are disposable after compact receipts.
- A resource failure never authorizes broad deletion. Cleanup is limited to
  exact task-owned or separately authorized reviewed-stale targets.

## Semantic-return and cost circuit breakers

- One ordinary consolidated semantic return may resume the same builder.
- A second terminal semantic return ends that builder context at a safe WIP
  checkpoint. A fresh successor receives a short order containing the still-
  missing outcome, not accumulated debugging instructions.
- If a successor repeats the same proof-category failure, stop implementation
  and simplify/redesign the owner seam or test boundary before more code.
- Every repeated expensive failure first returns to a cheap discriminator. If
  the failure cannot be represented cheaply, it is a method/architecture
  boundary, not permission for another physical run.
- Two hourly audits or four execution hours on one heavy claim force a
  method/value reset. Provider queue time is recorded separately.

## Supervision cadence

- Builder silence receives exactly one bounded liveness check after ten
  minutes: collaboration state plus its exact owned handle/progress marker.
- One hourly economy audit reads operation metadata only and asks what product
  decision changed.
- Source/diff review occurs at method-risk or terminal events, not on a timer.
- User status questions do not interrupt healthy execution.
- The engineer contacts the builder only under the protocol's named reasons.

## Progress accounting

Record only capability gained or missing, evidence tier and exact identity,
remaining product debt, expensive elapsed work and reused/avoided work, and the
next bounded slice. Commits, line count, test volume and agent activity are not
product progress.
