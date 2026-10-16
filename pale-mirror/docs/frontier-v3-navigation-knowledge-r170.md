# R170: bounded navigation knowledge and addressed waits

Date: 2026-10-16. Scope: the confirmed R169 short-route flood and its connected
observation / continuation lifetime. Main implements alone. No hive changes.
Source commit: `961f6a5de581ca15cc4d5fbb031564dc20329703`.

Diagnosis: `server-check-20261016-r169-short-routes.md`. The unknown cell is a
missing collision fact, not evidence of a physically absent block. A player's
later torch sighting does not identify the historical block that caused it.

## Contract and implementation

- `NavigationObservation` owns typed knowledge evidence: known collision,
  pending write, unobserved/unloaded neighbour, or an explicit unsupported
  context/shape reason. It retains bounded exact neighbour reads with block
  identities. Native Minecraft shapes remain the source of collision facts;
  jobs do not infer passability from a block name.
- The native adapter reads only already-loaded chunks. Position-independent
  definitions cannot contain neighbour dependencies. Dynamic shapes which may
  inspect the concrete entity context remain explicitly unsupported; powder
  snow must not become empty air. Torch/wall-torch are known empty collision.
- A physical write fences its own affected cell and exact retained dependents
  before the write. Publication samples the final physical state, not an early
  cached post-write shape. COLD source changes invalidate captured neighbour
  identities; a replaced source cannot inherit its obsolete native sample.
- The search records the exact missing reads of its own body-clearance policy.
  Exhausting a small incoming/outgoing component with unknown cells produces
  an addressed knowledge wait, not a 4093-region flood or a false NO_PATH.
  Fully known detours, regional itineraries and shared corridor caches remain.
- The existing native observer consumes a bounded round-robin demand window
  (125 per turn; existing 8192 pending-cell ceiling). It does not load chunks.
  New facts wake their dependent queries. A known no-op resample also wakes a
  worker which finished on an older immutable view after the first publication;
  it does not journal another identical geometry event.
- Retiring the last continuation cancels its active/deferred/rebased search;
  a replacement/shared consumer preserves the query. Work, route permission,
  actor ownership and physical arrival remain their existing owners.
- Performance diagnostics expose missing cell addresses/reasons, calculation
  stage and geometry-restart count. Unknown is neither air nor a global error.

Schema **290**, explicit stable evidence tags. Fresh disposable world required;
no historical-WAL migration or hidden compatibility execution path.

## Evidence and limits

Saved R169 tick95200 geometry, same two endpoint pairs:

| Pair (support coordinates) | R169 | R170 |
|---|---|---|
| 403,60,1 → 399,59,-1 | 15,115,447 work;4093 regions;SAFETY_LIMIT | 14,546 work;0 regions;addressed UNKNOWN |
| 403,60,1 → 398,59,-1 | 15,115,468 work;4093 regions;SAFETY_LIMIT | 14,567 work;0 regions;addressed UNKNOWN |

This is about **1000× fewer work units for these two pathological queries**,
not a measured server-wide TPS gain. Offline counterfactual supplying known
empty collision at401,61,0 produces FOUND in131/156work respectively. That
counterfactual changes only an in-memory copied state, not the live world.
Receipts: implementation checkout `build/r170-snapshot-search.log` and
`build/r170-snapshot-resolved.log`; old bytes decoded only by an isolated
diagnostic loader of the old artifact.

Native local-navigation slice:42/42pass, including actual torch collision,
exact dependent pre-write fencing, publication, unsupported powder snow,
and geometry WAL/checkpoint recovery. `build/r170-release.log` retains it.
Focused pure tests cover missing-fact wake, no unchanged repeat work,
cancelled/shared/rebased consumers, collision/source precedence and codecs.
`build/r170-final-focused.log`:134/134 focused Frontier tests pass; NeoForge
740 discovered,739passed/1skipped/0failed; guardrails, build and packaged-JAR
verification pass in2m54s. The skipped test is not counted as executed evidence.
Final checks and deployment identity are recorded in the continuity ledger.

The attempted full `check` was **not green**: the existing
`ExpeditionSupplyFlowTest.actualFullPersonalInventoriesSelectAndReserveTheFiniteAnimalAndLoadItsRealCargoCustody`
fails at line172, reproduced on clean R169 in
`/tmp/pm-r170-baseline-expedition.log`. The broad run was stopped while the
whole-world `ExtractionSiteTest` was still advancing unrelated simulation;
this is incomplete broad evidence, not an asserted regression or a pass.
Neither test is deleted or weakened by this change. Narrow affected checks
and the actual Minecraft boundary provide this fix's acceptance evidence.
No full-pack graphical/player acceptance or unrestricted modded dynamic-shape
support is claimed.
