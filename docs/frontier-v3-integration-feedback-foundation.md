# Frontier v3 integration-feedback foundation

Status: accepted normative implementation brief for mandatory slice `F0.V`.

This slice pauses, but does not discard, the current `F0.1` harvest work. It
must complete before implementation resumes `F0.1` and the remaining F0
foundation. Its purpose is to move ownership, hand-off and crash-window defects
into a fast deterministic feedback loop while retaining real Minecraft evidence
for the physical boundary.

The required order is:

```text
F0.0 complete -> F0.V (including mandatory F0.VA) -> resume preserved F0.1 -> F0.2 ... F0.6
```

No MAT breadth, new scene family or unrelated gameplay feature is admitted
during `F0.V`.

The accelerated verification loop in
[`frontier-v3-accelerated-verification-loop.md`](frontier-v3-accelerated-verification-loop.md)
is now mandatory substage `F0.VA`. After the current process reaches a safe
boundary, preserve its evidence and interrupt further expensive matrix closure
to implement F0.VA. Then resume this document's remaining native/crash matrix;
F0.VA does not replace or weaken any F0.V requirement.

## Non-negotiable boundaries

- Preserve every useful uncommitted `F0.1` source, test and scenario change.
  Do not reset, revert, replace or hide that work to obtain a clean baseline.
- The pure harness exercises production process aggregates, reducers,
  schedules, registries and codecs. It may supply typed physical observations;
  it may not implement a second simulation or claim Minecraft evidence.
- Fault injection and fixture selection are pilot/test-classpath capabilities.
  They must be absent from the production JAR and unreachable from a normal
  world, JVM property, player command or operator command.
- Native evidence still uses a fresh isolated world, natural chunk demand and
  ordinary player actions. It never force-loads, edits world files or mutates
  canonical state through diagnostics.
- Do not make a test green with retries, a longer timeout, a reused mutable
  evidence world, a server-selected player target or weakened terminal
  assertions.
- At most one visible native client may exist. A persistent pilot client may
  reconnect across an intentional server restart, but it owns no world state.

## F0.V.0 — measured baseline

Instrument the existing PMV3 manifest/JSONL boundary so every isolated run
reports, at minimum:

- source/build identity resolution;
- Gradle/configuration or launch preparation;
- server JVM boot to exact run-ID readiness;
- client JVM boot and connection;
- fixture readiness;
- evidence-action execution;
- frame capture when present;
- graceful save/port close or exact abrupt stop;
- recovery-server boot and client reconnect;
- terminal assertions and cleanup.

Record a same-host baseline from a checked-in lite smoke and one checked-in
graceful-restart scenario. Use existing attributable traces where complete;
otherwise use an isolated clean detached `HEAD` without changing the active
worktree. Report median phase and total times from three identical runs. A
timeout is failure evidence, never a timing sample to retry away.

## F0.V.1 — pure HOT/COLD process contract harness

Add a reusable pure-Java contract harness driven by the closed duration-process
registry. For one registered process it must exercise the production owner,
events, reducer, scheduler and persistence codecs through this matrix:

1. never-loaded COLD progress;
2. COLD -> HOT acquisition at two distinct retained checkpoints;
3. typed HOT checkpoint -> COLD release -> continued COLD progress;
4. repeated HOT/COLD hand-offs without identity, claim or cursor drift;
5. duplicate observation and stale process revision rejection;
6. simultaneous COLD/HOT authority rejection;
7. intervention owned by the affected process;
8. snapshot and WAL recovery at each complete semantic checkpoint;
9. stale authority epoch rejection when that state exists;
10. terminal identity, custody, conservation and schedule assertions.

The harness compares exact semantic projections for identical input streams,
not unconditional whole-world HOT/COLD digests or physical trajectories.
`frontier-v3-execution-semantics.md` requires a versioned per-family comparator:
exact identities/accounting/checkpoints, exact quiet-work norms, and predeclared
statistical combat metrics. Statistical tolerance never weakens conservation
or permits a reset of partial labor, cooldowns or random opportunities.
Start with the preserved `ResourceSiteHarvestJob`; the API must be
reusable by later duration processes without adding type branches to the
harness.

Treat the result as an internal Process/Scene SDK, not as a harvest helper or a
new public mod API. A closed inventory may register stable family keys and typed
descriptors, but generic lifecycle execution, contract matrix, scenario
composition, crash-boundary control and semantic assertions must not contain a
hard-coded check, switch or whitelist for `RESOURCE_SITE_HARVEST` or any other
concrete family. Each process supplies one typed descriptor or adapter that owns
its production aggregate, legal commands/observations, semantic cursor,
invariants and physical-provider policy.

The harvest adapter must execute the production aggregate, events, reducer,
scheduler and persistence codecs. A synthetic counter, mirrored state machine
or fake codec may test the harness itself, but cannot satisfy the reference
process gate. Before closing F0.V, instantiate the same SDK extension point for
one structurally different existing duration process as a narrow conformance
proof. This second proof must reuse production ownership and codec vocabulary
without copying lifecycle/recovery/runner logic or adding a concrete-family
branch to generic code. It does not authorize new gameplay, mark that family
`ENFORCED`, close its later F0 slice or claim Minecraft/M2 evidence.

### F0.V.1a — SDK registration, bounds and observer demand

Every physical-capable canonical owner must declare exactly one execution
archetype in the closed SDK inventory:

| Archetype | Required execution primitive |
| --- | --- |
| `DURATION_WORK` | paired COLD/HOT drivers over one work cursor |
| `COORDINATED_TRAVERSAL` | paired COLD/HOT drivers over one retained route/formation cursor |
| `COMPOSITE_OPERATION` | canonical parent phases plus disjoint bounded child fronts |
| `ATOMIC_INTENT` | durable-before-effect intent and exact postcondition |
| `SPATIAL_FRONTIER` | retained distributed cells/cursors, not a fictitious worker |
| `AMBIENT_CUSTODY` | presence/custody lease without process progress |

The descriptor has a stable non-reused family ID and schema version and names
its canonical owner, command/observation and codec vocabulary, driver/provider
bindings, lifecycle transitions and hard maxima for actors/cargo/effects,
affected local chunks or navigation envelope, observations per tick, retained
records/bytes, cadence and work weight. It also declares comparison policy/version,
knowledge evidence/validity and unknown-precondition policy, physical-custody
capabilities, interaction boundaries and recoverable confirmation semantics;
unsupported capabilities remain explicit and cannot certify a family.
Composition rejects a wrong or multiple
archetype, missing/duplicate registration or codec, unknown descriptor version
and any unbounded adapter. An incompatible retained descriptor fails before
recovery/execution; changing retained meaning requires an explicit migration
and golden old-byte proof rather than silent rebinding to the current adapter.

Player demand is one server-side aggregate input, never one lease per player.
Two or more observers of the same process/front must retain one lease, one set
of physical identities, one schedule and unchanged canonical progress rate. A
departing player cannot trigger drain while another valid observer still
demands the area. Zero aggregate demand plus bounded hysteresis permits a
release attempt only after independent physical activity and outstanding effects
are safely checkpointed/fenced. A live container or mechanism may retain its
own physical custody without a viewer; COLD cannot mutate that same scope.
Separate players may activate disjoint
fronts of one operation only when their actor, cargo, effect and local-space
allocations do not overlap and their combined descriptors remain within budget.

Add pure composition negatives for every archetype/descriptor/version/bound
failure. Add a focused physical demand test with two observers joining and
leaving in opposite order; it must prove one lease/body set, no acceleration,
no premature drain and exactly one final safe release. Add an observer-free
physical-activity case and its release/restart negative under F0.2/F0.3; SDK
declaration alone does not close those physical gates. This is infrastructure
evidence, not a multiplayer product-comprehension claim.

Install seeded negative tests proving that the harness fails for a missing
driver, a second cursor, duplicate scheduled continuation, unregistered emitted
payload, omitted codec and an observation accepted at a stale version. The
focused contract suite must be suitable for the per-edit loop and complete in
no more than 15 seconds on the current development host after warm compilation.

## F0.V.2 — deterministic crash-window controller

Add a test-only crash controller that can be armed for one exact run ID,
process/intent owner and expected revision or authority epoch. It must stop only
the nonce-announced disposable Minecraft JVM at a named boundary; broad process
matching is forbidden.

Cover these durable boundaries where applicable:

- lease or intent recorded, before physical materialization/effect;
- physical effect visible, before its typed observation is accepted;
- typed observation durable, before the next process checkpoint/schedule;
- HOT checkpoint durable, before drain/release;
- release durable, before resumed COLD execution.

The boundary signal must be deterministic and attributable. Do not approximate
it with a sleep followed by `SIGKILL`. After restart, assert exactly one of:
current binding reclaimed, safely checkpointed lease fenced and COLD resumed,
or the smallest ambiguous non-replayable effect isolated. Prove absence of a
duplicate actor, item, effect, schedule and terminal event.

## F0.V.3 — generated semantic scenario matrix

Introduce one declarative vertical contract that names:

- process/owner and exact actor/resource identities;
- retained cursor/checkpoint vocabulary;
- ordinary player setup and intervention actions;
- terminal domain invariants;
- permitted restart points and relevant physical evidence.

Generate or compose the standard variants from that contract instead of
copying independent scenario logic: never-loaded, arrive-mid-process,
unload/return, intervention, graceful restart and abrupt restart. Generated
scenarios remain checked and reviewable, and their evidence actions remain
ordinary client actions. A generic terminal semantic assertion replaces
process-specific waits where the same assertion vocabulary is sufficient.

Add a neutral-observer differential run: execute the same fixed-seed process as
fully COLD and with non-intervening HOT/COLD hand-offs, then compare identities,
claims, conservation and retained legal checkpoints exactly. Compare quiet-work
norms and terminal production exactly under equivalent conditions; compare combat
outcome distributions through the predeclared versioned calibration contract,
not per-run equality. Include frequent hand-offs during partial work/cooldown,
not only arrival and final output. Physical paths and hit sequences are not
compared. Descriptor/comparator changes invalidate affected cached acceptance.

## F0.V.4 — one-build, one-client restart runner

Prepare and identify the tested source, classpath and artifacts once per
scenario matrix. A restart segment must not recompile or resolve a different
build. Server recovery still uses a new Minecraft JVM and the same exact world;
that restart is evidence and cannot be optimized away.

Keep one native pilot-client JVM alive across the before/after halves. It must
disconnect normally, wait for the replacement server's exact run-ID readiness,
reconnect as the same declared ordinary player and clear all transient scenario
state before continuing. The client cannot retain a canonical assertion result
as authority after reconnect.

Use a Gradle daemon, a generated launch manifest or direct prepared Java launch
as implementation detail, provided the manifest proves one build identity and
packaged-JAR verification still checks the production artifact. Do not reuse a
mutable world between independent scenarios; only the two halves of one
recovery proof share their explicitly named world.

## F0.V.5 — automatic failure bundle and gate integration

Every failed native or crash-matrix run must preserve one bounded attributable
bundle containing:

- build/scenario SHA, seed, world and run IDs;
- phase timings and exact failed boundary;
- process ID/version/cursor and lease/intent ID/epoch/lifecycle;
- exact affected actor, cargo and allocation IDs;
- the bounded correlated PMV3 trace and relevant decoded WAL tail;
- relevant server/client log tails and port/JVM termination evidence;
- diagnostic snapshots and declared frames already produced by the scenario.

Do not collect unrelated player identity, full unbounded logs or whole-world
dumps. Preserve the failed disposable world for forensic recovery; successful
worlds retain the existing exact cleanup rule.

The iteration ladder is mandatory:

```text
per edit:       pure contract + focused unit/Node/codec guards
physical seam: smallest matching GameTest slice
causal claim:   one generated/declared native variant
slice commit:   complete critical-code gate + required native matrix
```

Parallelize pure/Node/build work only when outputs and ports are isolated.
Never run multiple visible clients, and do not create resource contention that
turns timing noise into a false product defect.

## F0.V exit gate

`F0.V` is complete only when all of the following are true:

- the phase-timing baseline and candidate report use the same host, seed,
  profile, view distance and commit family;
- the reference harvest process passes the reusable pure contract matrix and
  all seeded negative tests;
- the generic SDK layers contain no concrete process-family branches, and the
  harvest contract exercises production aggregate/reducer/scheduler/codec
  ownership rather than a mirrored test state machine;
- one second existing duration process instantiates the same extension point as
  a bounded production-backed conformance proof, without copied lifecycle,
  recovery or runner logic and without claiming its later F0/MAT completion;
- the closed SDK inventory rejects wrong/multiple execution archetypes,
  missing/duplicate or incompatible descriptors/codecs and every undeclared or
  unbounded adapter;
- every admitted descriptor declares stable versioned vocabulary and bounded
  actor/cargo/effect, local-space, observation, retention, cadence and work
  limits, with explicit migration proof for any retained meaning change;
- a two-observer physical test proves demand aggregation creates one lease/body
  set, does not accelerate progress, cannot drain while one observer remains
  and releases exactly once after zero demand, hysteresis and safe physical
  custody closure;
- graceful and abrupt crash boundaries are deterministic, attributable and
  prove no duplicate/lost authority;
- one vertical contract supplies the required F0.1 scenario variants without
  a canonical mutation API;
- one restart proof uses one prepared build identity and one persistent native
  client across both server JVMs;
- failure bundles are produced and parser/schema tested;
- the same-reference three-baseline/three-candidate measurement demonstrates
  reduced end-to-end restart time without weaker assertions or fewer required
  server restarts. User amendment of 2026-09-05 makes 25 percent an advisory
  target, not a blocking floor; the retained 22.52-percent result is accepted
  for this optimization. Do not rerun merely to close that numerical gap.
  Correctness, identity, fresh recovery evidence and regression review remain
  mandatory; historical timing is not promoted to current-source correctness;
- mandatory substage F0.VA passes its persistent-matrix-client,
  content-addressed cache, executable test-pyramid, exact-barrier, four-worker
  CI and immutable-development-fixture exit gate, including its measured 3x
  feedback and 2.5x CI matrix targets;
- focused tests, `guardrails`, `check`, the applicable GameTest slice, full
  critical-code gate and packaged-JAR fixture-absence checks pass;
- the architecture audit and Continuity Ledger record the evidence and name
  `F0.1` as the resumed current slice.

Pure/fake observations provide only automated contract evidence. They never
promote a process to M2 or replace its native player-height, intervention or
restart proof.
