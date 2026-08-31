# Frontier v3 simulation architecture audit

Status: accepted defect register and remediation input.

Audit date: 2026-08-31. Audited revision: `5ca5291`. Scope: the pure v3
kernel/model, persistence codecs, NeoForge lifecycle and physical execution
boundary. This is a static architecture audit; it does not replace the annual,
JFR, restart or player-visible gates in the implementation plan.

## Classification

- **Confirmed defect** means current code contradicts an accepted invariant or
  permits a state/format path that the invariant forbids.
- **Structural debt** means current behavior is correct, but another domain or
  scene cannot be added safely without first changing the shape of the code.
- **Measured risk required** means the code suggests a scale risk, but no
  performance defect is claimed until a reproducible profile proves it.
- Explicit exhaustive dispatch in a sealed wire codec is not itself a defect.
  The defect is using unstable tags or repeating lifecycle policy outside its
  owner.

## Evidence summary

| ID | Severity | Classification | Evidence at audited revision |
|---|---:|---|---|
| V3-AUD-001 | P0 | Confirmed defect | 48 `LogisticsSceneCause`/`SettlementAssaultSceneCause` type tests in 22 main-source files; generic `SceneLease` exposes logistics-only accessors that throw for assault scenes. |
| V3-AUD-002 | P1 | Confirmed defect | `FrontierDevelopmentScenarios` and 15 development configuration entry points are in `src/main`; `FrontierV3ServerLifecycle` can select 13 profiles from JVM properties whose only guard is a nonblank run ID. |
| V3-AUD-003 | P1 | Structural debt | `FrontierWorldRuntimeDefinition` is 947 lines: its command planner has 35 type tests, scheduled planner has 41 string cases, and reducer has 104 cases. |
| V3-AUD-004 | P1 | Structural debt | `FrontierV3ServerLifecycle` manually invokes 22 physical executor `tick` methods in a causally significant source order with no declared phase/dependency contract. |
| V3-AUD-005 | P1 | Structural debt | 38 direct positional `new FrontierWorldState(...)` calls occur in 18 production files; many adjacent arguments have the same `Map` type. |
| V3-AUD-006 | P1 | Confirmed defect | 223 persisted enum encodes/decodes use `ordinal()` or `values()[tag]` in 19 v3 codec files. Reordering or inserting an enum can reinterpret an existing snapshot/WAL without a decoding error. |
| V3-AUD-007 | P1 | Structural debt | Balance and cadence rules such as review intervals, radii, infection gain and COLD step sizes are private process constants and are not identified by a persisted ruleset/version. |
| V3-AUD-008 | P2 | Structural debt | Pilot profile names are repeated in Gradle, lifecycle selection and scenario tooling rather than owned by one test-only catalog. |
| V3-AUD-009 | P2 | Measured risk required | Most aggregate transitions copy immutable maps and most non-infection transactions run the complete cross-domain audit. Correctness is strong, but allocation and validation cost at complete Wave 5/6 load is not yet measured. |
| V3-AUD-010 | P2 | Measured risk required | One globally ordered due-action queue and one physical tick budget have bounded work, but there is no complete-domain pressure/fairness proof for simultaneous fronts and hundreds of bodies. |
| V3-AUD-011 | P1 | Structural debt | The stable interface defines separate `model`, `process` and `persistence` ownership, but 33 `*Process` and 23 `*Codec(s)` source files currently reside in `frontier.v3.model`. |
| V3-AUD-012 | P1 | Confirmed defect | `FrontierPayload` is open and `FrontierWorldPayloadCodecs.create()` manually enumerates codecs; no gate proves every process input/output payload has a wire codec. This mechanism already omitted `ResourceSiteHarvestObservation` once. |

## Follow-up finding during closure

| ID | Severity | Classification | Evidence discovered during remediation |
|---|---:|---|---|
| V3-AUD-013 | P1 | Confirmed defect | Seven v3 GameTest calls used `ServerLevel.getChunkAt` to force-load canonical scene/field chunks; two test-only v3 helpers also remained in `src/main` and were not rejected by packaged-JAR verification. A GameTest level is intentionally placed outside the finite canonical 1024×1024 world, so treating it as a real remote-scene integration world either forces chunks or creates invalid canonical positions. |

## Remediation status

The evidence table above remains the immutable record of revision `5ca5291`.
This table records only completed corrections and the next owned work; a
finding is not silently removed merely because its ceiling no longer grows.

| ID | Status | Evidence / next owner |
|---|---|---|
| V3-AUD-001 | CLOSED | `e9bc0d0`: closed pure and NeoForge `SceneBehavior` registries own every generic lifecycle decision; duplicate/missing registration is rejected. The focused scene slice passes 30/30 and the complete critical gate passes 250/250. |
| V3-AUD-002 | CLOSED | Fixture builders/catalog are test-fixtures only; a pilot-only bootstrap selects the already-built configuration before the normal lifecycle. Production bootstrap is property-independent, and JAR verification rejects every former fixture entry point, catalog and bootstrap. The catalog/lifecycle negative tests, pilot smoke and complete critical gate pass. |
| V3-AUD-008 | CLOSED | One test-only declarative properties catalog is parsed and validated by Java, Gradle and the Node pilot. Unknown/duplicate profiles fail before a scenario starts; Gradle no longer owns a copied profile list. |
| V3-AUD-003 | OPEN — H0.3 | Scheduled routing is a closed process-catalog map and `FrontierWorldRuntimeDefinition` is now a 62-line `runtime` composition root with no command/reducer branches. Its 34 trusted-command branches and 104 reducer cases remain in separately bounded pure planners; the root now gets its finite bootstrap schedule only from the catalog. The registry still rejects unowned command/schedule/event input before dispatch; those two finite dispatchers remain explicit ratchets until they are split into registered process modules. The architecture ratchet tests pass 10/10 and the critical gate passes 248/248 GameTests, build and packaged-JAR verification. |
| V3-AUD-004 | CLOSED | The server lifecycle now invokes one closed staged registry. Every executor has a stable ID, explicit non-ordinal stage, dependencies, exclusive writes and a one-invocation bound; pure cycle/duplicate/reversed-causality tests and the NeoForge scene slice prove admission. Read-only `execution` diagnostics expose the final order. The manual-call ceiling is 0. |
| V3-AUD-011 | OPEN — H0.3/H0.4 | The composition root is now in `runtime`; model has a zero-tolerance source guard against imports from `runtime`, `process` or `persistence`. The explicit catalog remains a composition foundation only: move behavior modules next, then codecs/persistence without reverse dependencies. |
| V3-AUD-012 | OPEN — H0.3 | Descriptor/catalog startup now rejects duplicate ownership, missing stable codecs and unregistered command/schedule/event input. Emission admission is now bounded to each process's declared domain collaborations (no global world-payload fallback); full pure-v3 coverage exposed and recorded the required `physical → population/economy/strategy`, `hive → physical`, `infrastructure → population`, `strategy → population`, `economy → strategy` and `logistics → hive` paths. Finish exact type-ID contracts and representative codec/WAL round trips before closing. |
| V3-AUD-005 | CLOSED | `FrontierWorldStateUpdate` is the sole typed named replacement set for process/support transitions: duplicate declarations fail before aggregate construction, and every undeclared component retains the identical previous object. A declared no-op remains legal for revalidation. The source ratchet now permits full construction only in the aggregate, fresh bootstrap and versioned hydration (38 sites → 14); property coverage proves each unrelated component retains identity. |
| V3-AUD-006 | CLOSED | Every snapshot/WAL codec now uses explicit `tag → enum value` pairs in `FrontierWireTags`, never enum declaration order; unknown tags fail closed. The historic company-registration byte fixture plus stable-tag tests cover retained tags, and debt ratchets reject codec `ordinal()`/`values()[tag]` use and positional registry derivation. |
| V3-AUD-007 | OPEN — H0.5 | Add a persisted, hashed, explicitly selected `FrontierRuleset`. |
| V3-AUD-009 | OPEN — H0.6 | Instrument then measure complete-domain allocation/validation pressure. |
| V3-AUD-010 | OPEN — H0.6 | Prove deterministic fairness and bounded physical-stage pressure at multi-front scale. |
| V3-AUD-013 | CLOSED | Test-only fixture helpers and the resource-site GameTest now live only on the pilot source set; `FrontierV3GameTestSceneLeases` and `FrontierV3ResourceSiteGameTests` are rejected from the production JAR. The fast debt validator enforces zero `getChunkAt` calls in every v3 GameTest. The focused scene slice passes 28/28. Fresh visible native runs on `DISPLAY=:0` passed both canonical-coordinate flows with a graceful restart: `disposable_hot_scout_sighting_restart` retained the exact observed carrier/intercept operation after its scene closed, while `disposable_settlement_assault_restart` reloaded a 27-member assault with its confirmed strike and durable ownership. These scenarios, not an out-of-bounds GameTest surrogate, prove physical canonical coordinates. |

## Findings and required corrections

### V3-AUD-001 — scene behavior is not owned by one registry

The accepted contract requires one closed `SceneBehavior` registry. Current
scene admission, validation, ownership, state transition, diagnostics and
NeoForge execution independently ask whether the cause is logistics or a
settlement assault. `FrontierV3SceneExecutor` also invokes the assault executor
before running its logistics path. A third scene kind would therefore require
editing generic lifecycle code in many places and could accidentally call
`SceneLease.operationId()`, `cargoId()` or `logisticsCause()`, all of which
throw for a non-logistics cause.

Correction: introduce closed pure-domain and NeoForge behavior registries with
exactly one behavior per sealed cause. Generic lease code may know only common
identity, members, handoff, status and recovery. Cause-specific cargo,
engagement, assault and effect facts remain inside the registered behavior.
Sealed codec tags may retain an explicit exhaustive switch. Read-only
diagnostics consume a registered descriptor rather than recreating lifecycle
policy. No bomber, siege or third scene kind is admitted before this closes.

Exit evidence: duplicate/missing behavior registration fails at startup and in
a unit test; logistics and assault normal/negative/restart scenarios pass
through the same generic lifecycle; the debt ratchet has no scene branches
outside behavior registration, sealed codecs and explicitly allowlisted
read-only formatting.

### V3-AUD-002 — disposable fixtures are production-selectable

The nonce/property convention prevents accidental selection by the ordinary
scenario runner, but it is not a security or classpath boundary. Any normal JVM
can set the same two properties, and the fixture builders/configurations are in
production main sources. This contradicts the claim that fixtures are
unavailable to a live start and makes it possible to launch a plausible but
non-autonomous scripted world.

Correction: production `initialConfiguration` always creates the world
profile. Move scenario state builders, profile catalog and the selecting
bootstrap provider to a dedicated moddev/test source set that is absent from
the packaged JAR. The runner may select only a catalog entry provided by that
test classpath. A run ID remains correlation evidence, not authorization.

Exit evidence: packaged-JAR inspection rejects fixture classes, profile
properties and fixture configuration entry points; a production-start test
proves arbitrary JVM properties cannot select a fixture; every declared pilot
profile is validated from one test-only catalog.

### V3-AUD-003 — the runtime definition is a monolithic dispatcher

Explicit exhaustive dispatch is desirable, but one 947-line composition root
currently owns configuration variants, command policy, scheduled-kind routing,
event routing and many cross-domain validations. It is already close to the
repository's 1000-line hard limit. Adding domains here increases merge risk and
makes ownership and completeness hard to review.

Correction: split deterministic closed process modules. Each module declares
the command payloads, scheduled kinds, event payloads and reducers it owns. It
also declares the stable codec for every payload it can consume or emit.
Composition validates duplicate and missing registrations and retains stable
ordering. Unknown kinds still quarantine; dynamic plugins and ambient
classpath scanning are not introduced. The ordinary runtime configuration and
limits have one composition path; test fixtures inject only initial
facts/schedules.

Exit evidence: every registered payload/action has exactly one owner; omitted
or duplicate ownership fails a focused test; the composition root contains no
domain logic and is materially below its debt ceiling.

### V3-AUD-004 — physical executor order is implicit source order

Observation-before-effect, projection-before-observation and scene-last choices
change causality. Today those choices are 22 adjacent method calls. A new call
can be inserted at a visually convenient but causally incorrect location, and
there is no machine-readable dependency or per-stage pressure diagnostic.

Correction: use a closed staged physical-executor registry. Each executor
declares a stable ID, phase, dependencies, owned intent/observation kinds and a
bounded budget. Startup rejects duplicate IDs, cycles, missing dependencies or
multiple writers for an exclusive kind. The resulting topological order is
stable and exposed in read-only diagnostics.

Exit evidence: order/cycle/duplicate tests plus one negative causal test where
reversing an observation/effect dependency is rejected before world mutation.

### V3-AUD-005 — aggregate reconstruction is positional and scattered

The immutable aggregate is appropriate. The unsafe part is reconstructing its
22 components positionally from 18 files. Several components share the same
erased Java type, so a same-typed argument swap can compile. Adding a component
also requires editing unrelated process support classes, which makes omissions
likely.

Correction: `FrontierWorldState` owns a named copy/update boundary (builder,
copy spec or domain-owned `with...` methods). Process/support classes return
their owned sub-aggregate or call a named update; direct aggregate constructors
remain only in initial-state assembly and versioned hydration. Transition
validation consumes an explicit changed-domain set rather than inferring all
ownership from object identity.

Exit evidence: source guardrail rejects direct construction outside the
state/initial-state/codec owner; property tests mutate each component and prove
all unrelated components retain identity and value.

### V3-AUD-006 — persisted enum tags depend on source order

Bounds checks prevent an invalid array index, but they cannot detect a valid
tag whose meaning changed after enum insertion/reordering. Snapshot schema
versioning does not help if the source enum changes without an explicit tag
migration. This is a latent save/WAL compatibility defect.

Correction: every persisted enum has an explicit stable wire tag (integer or
string) and a total fail-closed decoder. Tags are never reused. Changing
meaning requires a schema migration; adding a value adds a new tag without
changing old bytes. Replace the existing 223 positional uses in bounded codec
families, with golden old-byte decoding tests before each schema bump.

Exit evidence: source guardrail permits no `ordinal()` or `values()[tag]` in v3
codecs; golden fixtures decode identically after enum declaration reordering;
unknown tags fail before state mutation.

### V3-AUD-007 — world rules are not versioned data

Capacity bounds and schema maxima correctly belong in code. Balance rules do
not: changing a patrol interval, assault step, infection gain or perception
radius currently changes the future of an existing world without recording
which rules produced its prior state. Development fixtures can also silently
drift from production composition.

Correction: add immutable `FrontierRuleset` data with a stable ID, schema and
content hash in the world manifest/snapshot. Processes receive it explicitly.
Keep safety maxima and algorithmic invariants in code. A ruleset change is an
explicit new-world choice or a versioned migration; recovery rejects a missing
or incompatible ruleset rather than using current constants silently.

Exit evidence: same state/seed/ruleset is deterministic; a changed ruleset has
a different manifest hash; recovery with an unavailable ruleset fails closed;
fixture composition reuses the production ruleset unless a scenario declares a
test-only override in its catalog.

### V3-AUD-008 — pilot profile catalog is duplicated

Correction: one test-only declarative catalog owns profile ID, fixture
provider, allowed runner, required assertions and source profile. Gradle and
Node/Java parsers validate or derive their allowlists from it. Unknown,
duplicate and production-packaged entries fail the fast gate.

### V3-AUD-009 and V3-AUD-010 — scale is not yet disproved

Immutable copies and complete validation buy valuable correctness, and the
ordered global queue buys determinism. This audit therefore does not prescribe
mutable shared state, parallel canonical writers or per-domain queues. First
instrument transaction planning/reduction/validation/allocation and each
physical stage. Then run the same-seed complete-domain pressure route with 12
settlements, simultaneous fronts and increasing exact body counts.

Optimization is authorized only from measured attribution. Acceptance records
schedule lag by kind/owner, queue depth, p50/p95/p99 transition and physical
stage cost, allocation/GC, TPS/MSPT and canonical hash. Any fairness mechanism
must retain one total deterministic order and WAL replay equivalence.

### V3-AUD-011 — declared package ownership has collapsed into `model`

The implementation plan defines `frontier.v3.model` for canonical aggregates,
`frontier.v3.process` for behavior and `frontier.v3.persistence` for schemas
and codecs. Current package structure has no `process` package; 33 process
classes and 23 state/payload codec classes sit beside aggregates in `model`.
This is not cosmetic: package-private access encourages processes to reconstruct
the world aggregate directly and prevents a clean dependency direction.

Correction: H0.3 moves process modules behind an explicit process-facing state
API; H0.4 moves wire schemas/codecs to persistence ownership without exposing
mutable model internals. Dependencies remain `process -> model/api` and
`persistence -> immutable model/api` with no reverse dependency on concrete
process classes. Package moves do not justify compatibility adapters in the
runtime.

Exit evidence: source isolation rejects `*Process`/`*Codec(s)` classes in
`model`; package dependency tests reject a model import of process or concrete
persistence implementations.

### V3-AUD-012 — payload-to-codec completeness is not enforced

Duplicate codec types fail correctly, but omission is discovered only when a
specific payload reaches WAL encoding. The former missing harvest-observation
codec proves this is not theoretical. An open `FrontierPayload` plus a manually
assembled codec list cannot demonstrate that every accepted command or emitted
event is durable before the runtime handles it.

Correction: each closed process module supplies one descriptor containing its
accepted command payloads, scheduled kinds, emitted/consumed event payloads and
their codecs. Composition rejects a payload without exactly one stable codec,
a codec without one declared owner, duplicate type strings and a planner that
emits an undeclared payload. Kernel schedule effects remain their own closed
module.

Exit evidence: a negative composition test omits one codec and fails before
engine start; every process descriptor round-trips one representative payload;
the full registered payload set round-trips through snapshot/WAL recovery.

### V3-AUD-013 — GameTest must not impersonate an off-template canonical world

The GameTest server deliberately places its templates at arbitrary far-away
coordinates. A v3 GameTest that creates a real canonical scene at its original
1024×1024 coordinates must therefore force-load an unrelated chunk. Conversely,
moving the physical actor to the template without a complete test-only coordinate
transform makes canonical position evidence invalid. Both approaches make an
apparently green test weaker than the real materialization boundary.

Correction: keep GameTests inside their naturally loaded template chunks for
local executor/ownership behavior. Put scenario fixtures and GameTest-only
helpers in `src/pilot`; do not package them. Prove cross-coordinate scene
materialization, player demand, restart and recovery through the native
disposable test-pilot against a real 1024×1024 v3 world. The architecture debt
validator rejects every v3 GameTest `getChunkAt` call.

Exit evidence: zero forced v3 GameTest chunk loads; packaged-JAR rejection of
the two moved test helpers; focused GameTest slice plus the named native scene
scenarios remain green.

## Explicit non-defects

- Immutable canonical state and fail-closed complete validation are retained.
- A closed exhaustive reducer or codec switch is acceptable when it is the one
  owner and uses stable wire tags.
- Test fixtures are useful and remain supported; only their production
  packaging/selection is defective.
- A single canonical writer and one total scheduled order remain invariants.
- Current performance is not declared defective without a reproducible JFR or
  benchmark comparison.

## Remediation order

The implementation plan owns execution. In summary: stop adding scene/domain
breadth; close scene behavior and fixture isolation first; then split domain
and physical dispatch; then replace positional state updates and unstable wire
tags; introduce the versioned ruleset; finally measure and, only if necessary,
optimize complete-domain pressure. Each correction lowers the checked-in debt
ratchet. Raising a baseline requires an accepted architecture amendment with a
new finding and removal plan; it is never an ordinary implementation edit.
