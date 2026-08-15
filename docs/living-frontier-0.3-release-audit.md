# Pale Mirror 0.3 release audit

Audit date: 2026-08-15.

## Decision

The implementation is a technically verified `0.3.0-alpha` completion
candidate. The mechanical scope agreed for Living Frontier is present, but the
product release gate is not yet closed because the required unaided human
evidence has not been recorded.

The audit also found and fixed one P0 bootstrap invariant: an unvisited healthy
authored settlement could consume its entire reserve because its canonical
baseline route remained `PLANNED` until every physical railway chunk had been
loaded. An authored genesis manifest now supplies persistent initial topology
evidence. Unknown chunks no longer stop healthy off-screen flow; a missing or
disconnected loaded graph still blocks the contract immediately.

## Automated evidence

| Gate | Status | Evidence |
| --- | --- | --- |
| Pure domain and module tests | PASS | 148 JUnit tests, 0 failures, 0 errors |
| Core server behavior | PASS | 45/45 required NeoForge GameTests |
| Visual/materialization behavior | PASS | 25/25 required Visuals GameTests, including 216 public-realm combinations and grounded/readable mine infrastructure |
| Build and architecture | PASS | `check`, architecture guardrails, Java style and source isolation |
| Distribution | PASS | Core and Visuals production JAR verification |
| Restart/crash safety | PASS | Packaged core restart/crash harness and two-start Visuals harness |
| Multi-region identity | PASS | Independent region/community/site/route identities are covered; fresh authored genesis supports a bounded configurable region count |
| Baseline freight | PASS | Authored vanilla topology starts healthy off-screen, commissions from dry rails and stable supports without claiming speculative decoration, loaded damage blocks it, connected cross-chunk repair/reroute restores it, and Create evidence still expires independently |
| Combat recovery | PASS mechanically | Threat gate, controller, recovery, flow restoration and physical cleanup are covered |
| Evacuation | PASS mechanically | Prepared shelter validation, population displacement, representative camp and autonomous fallback are covered |
| Refusal/autonomy | PASS mechanically | Policy progresses without Narrator and uses an audience-aware intervention window before irreversible displacement |
| Positive aftermath | PASS mechanically | Funded staged storehouse project changes physical depot state and canonical capacity/prosperity |
| Delayed consequence | PASS mechanically | Development and resettlement facts are re-derived from history into later Narrator candidates |
| Narrator v2 | PASS mechanically | Deterministic candidate scoring, novelty/intensity penalties, cooldown retry and explicit `NO_SCENARIO` are covered; recovery investment requires a real stabilized crisis and never captures ordinary prosperity |
| Presentation contract | PASS mechanically | Atlas is actionable, Ledger is causal detail, JourneyMap is navigation-only, and opaque IDs are excluded from normal Atlas cards |
| Fresh-world bootstrap | PASS | Seed `6149572031884207731` authored six isolated regions; after two simulation steps every healthy baseline delivered 16 IRON against consumption 8 (`netFlow +8`, stock 79/87), with no crisis, rationing, scenario or active threat |

`PASS mechanically` means that the canonical and physical state transitions are
implemented and tested. It does not mean an unaided player has understood or
enjoyed the path.

## Human product gates still open

| Gate | Current evidence | Required closure |
| --- | --- | --- |
| Clean-room comprehension | Not recorded | Five unbriefed players; at least 4/5 explain event, cause, choices and consequence |
| Choice discoverability | Not recorded | At least 4/5 independently discover two responses; combat, logistics and evacuation all occur across the sessions |
| Real Create logistics | Adapter and synthetic tests only | One real scheduled Red Valley → Ironhill train proves the same vehicle at both endpoints and restores supply |
| Cooperative audience | Not recorded | One 2–4 player session with shared story, single outcome and no duplicate actions |
| Full refusal path | Not recorded | One player deliberately declines/ignores and correctly understands the fair delayed consequence |
| Visual/audio quality | Previous visual reviews found defects; the latest authored-surface redesign is not yet accepted | Fresh-world pass over settlement, mine, baseline rail, infection, recovery, camp and storehouse |
| Session pacing | Algorithmic tests only | Two active regions in a live session demonstrate selection, quiet time, novelty and recovery/development pacing |

## Requirement-by-requirement status

### Discovery and understanding

Natural proximity and interaction discovery exists for settlement, depot,
baseline route, Mine17, Red Valley and refugee site. The first infection is
knowledge-gated and delayed after the settlement, depot and route are known.
Atlas distinguishes a mine with no production from a damaged route and shows
stock, net flow, reserve, defence and the intervention window. This satisfies
the implementation requirement, while actual comprehension remains the main
open product gate.

### Four crisis responses

- Combat restores Mine17 production and the persistent vanilla baseline flow.
- Industrial logistics uses a separate expiring Create traversal proof from
  Red Valley; it is not confused with the baseline minecart corridor.
- Evacuation requires an operational shelter capability and moves the canonical
  population group rather than merely changing a scenario flag.
- Refusal leaves the autonomous settlement policy running and produces a
  durable consequence after its fair grace window.

All four paths exist. Only combat has strong repeated live-user evidence from
development sessions; parity and readability of the other paths remain to be
shown in the agreed clean-room run.

### Visible and delayed consequences

Threat overlays, controller/gate actors, route carrier state, refugee camp and
storehouse upgrade all have physical projections. Development and return-home
opportunities originate from canonical aftermath history. Their correctness is
automated; the final visual hierarchy and emotional readability are not.

### Repeatability and stability

Authored region manifests are deterministic, persisted before admission,
chunk-local in materialization and isolated by region identity. The project
contains a resettable exercise workflow, supports multiple regions in one
world, and passes packaged restart checks. Fresh-world visual generation remains
the intended 0.3 workflow; backward compatibility is intentionally not a gate.

## Release conclusion

No additional simulation subsystem is needed to finish 0.3. The remaining
critical path is:

1. verify the first natural discovery and infection sequence in the deployed fresh world;
2. perform the latest visual acceptance pass;
3. run one real Create route and one cooperative session;
4. run five clean-room sessions and record the 4/5 comprehension result.

Until those steps pass, the accurate label remains **Pale Mirror 0.3.0-alpha —
feature-complete Living Frontier awaiting product validation**.
