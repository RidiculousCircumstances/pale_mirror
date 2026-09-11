# PM-F02C-COLD-ADMISSION-OBSERVATION-25: prove the real COLD admission bound

Specification revision: 1. Status: `CHECKPOINTED_METHOD_BUILD`.
Parent slice: F0.2C. Risk: critical-code. Engineer: supervising root.
Executor: one fresh `gpt-5.6-terra`, reasoning `high`.

## Bounded context packet

Start from exact clean WIP
`e5284b782c68af29319e7c11ba75947421dc0254`, parent
`90eafbb08de7ef3a0192ad3725601992e29d959c`, tree
`24326cc32cf5d13068aa204898634b6fffe13339`. Read scoped `AGENTS.md`, canonical
`CONTINUITY.md`, this order, `docs/engineering-agent-operating-model-v4.md`
sections `Builder context discipline`, `Bounded product order` and
`Event-driven lifecycle`, and architecture entries
`frontier-v3-settlement-assault`, `frontier-v3-process-execution-ownership` and
`frontier-v3-hot-cold-scene`. Do not read predecessor chat or ingest the order24
debugging narrative beyond the accepted facts below.

Accepted facts that must not be rediscovered:

- one focused JVM reproduction captured the expensive real chain
  `HiveSettlementAssaultProcess.planProgress` -> `coldAvailable` ->
  `reservedByOtherThanSettlementAssault` -> physical settlement-assault
  candidates -> graybox/route surface compilation;
- WIP `e5284b78` replaces that candidate-derived COLD exclusivity check with a
  bounded scan of the canonical nonterminal assault-owner map; this production
  result is useful but not yet accepted because its first test was not
  defect-sensitive;
- exact catalog and foreign-material leaves passed on that immutable WIP. Their
  retained XML hashes are respectively
  `0123c00b51ff1003d95c729beb4aed388634a0717a332d4943509c1f050da654`
  and `8927d6aa755b50b4c951a67a5d2733f09c81950f2133823929e1f6bf848e5836`;
- rejected descendant `99863bc3` is evidence only and must not be inherited.
  Its counter was disconnected from `planProgress`, and its overlapping
  foreign-defender fixture weakened recovery, target and assignment semantics.

## Sole outcome and semantic-change budget

Deliver one deterministic production-wiring proof: an ordinary due
`HiveSettlementAssaultProcess.planProgress` call must complete its COLD
eligibility decision without deriving a physical settlement-assault battlefield
provider/candidate, while emitting the same exact retained-attacker advancement
and next scheduled action. The registered scene selector remains the sole owner
of its physical provider derivation.

The exact production entrypoint under observation is
`HiveSettlementAssaultProcess.planProgress`. The test observation seam must be
reachable from that invocation. A plausible restoration of the old
candidate-derived eligibility must cross the same counted/forbidden seam and
fail deterministically. Calling an instrumented `reservationAdmission`
separately before or after `planProgress` does not observe the subject and is
rejected. No wall clock, timeout, loop completion, source-text assertion or
test-only shortcut is an oracle. Terra owns the smallest production seam and
test design.

Frozen semantics: do not change `SettlementAssaultStatus`,
`UNKNOWN_AFTER_RESTART`, target uniqueness, attacker or defender ownership,
`HumanAssignmentProjection`, ordinary worker/route/field admission, scene or
ambient lease custody, exact identities, approach/combat events, aftermath or
recovery. Do not construct canonically overlapping assaults that existing
validators reject. Order24's foreign-defender counterexample is superseded as
invalid framing, not converted into a new product rule. Any need to change a
frozen rule is an `ARCHITECTURE` exception and terminal return.

## Verification, custody and stop

Run only the smallest changed focused JVM discriminator. It must retain exact
test discovery/execution, command/duration/exit, candidate/parent/tree, changed
paths, production call-path wiring, counterexample, source/output identity,
receipt hashes and zero task-owned process state. Reuse the two immutable
receipts above if their production inputs remain unchanged; otherwise rerun
only the invalidated named leaf once.

Commit one coherent private candidate and return `METHOD_ADMISSION`. Stop for a
fresh read-only challenger before any full gate. No aggregate suite, GameTest,
Minecraft, package, native/R14, CI/provider, publication, deployment, terrain,
navigation/tactics, F0.3 or confidence rerun. A rejected method closes this
successor and forces seam simplification before further code.

Use branch `terra/f02c-cold-admission-observation-25`, worktree
`/home/rd/proj/pm-f02c-cold-admission-observation-25` and task root
`/home/rd/proj/pm-f02c-cold-admission-observation-25-tmp`. Preserve all prior
worktrees/evidence and unrelated services. The liveness epoch begins at direct
assignment; check after each ten complete minutes of silence, never sooner. The
first economy audit is due one hour later if execution remains active.
