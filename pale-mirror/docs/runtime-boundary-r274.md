# R274: environmental ingress and unused equipment departure

The retained R272 world exposed two independent technical failures on R273.
Hearthvale had 29 starving residents, 192 bread (164 free), and one farmer
occupying warehouse access behind a prepared equipment transfer. The worker's
body had already retired to COLD, while the transfer still required its old HOT
incarnation. Separately, loaded-world random grass updates produced repeated
geological invalidation transactions and 90–356 ms native ticks. Simulation
clock debt grew from 45 to 6,803 ticks while Minecraft's sampled TPS stayed near
20; these are different measures.

## Ownership-preserving repair

- Field equipment declares its pending interaction to the existing common body
  fence. Scene release also checks the whole job's physical obligations. A new
  incarnation cannot supersede an unresolved inventory interaction.
- The field adapter processes exact saved body departures in the existing
  OBSERVATION stage, before actor admission. The common Minecraft operation
  owner proves the prepared action was unissued, or positively cancels its unused
  attempt. An attempted or uncertain effect is never inferred to be unapplied
  from inventory appearance. Existing cancellation tickets close through the
  common canonical-confirmation protocol rather than leaking journal records.
- The field owner accepts that typed abort only for its retained preparation,
  exact execution/body/scope (including the exact retired departure incarnation).
  A body owner's durable RESUME_COLD retirement is sufficient positive departure
  evidence for an unissued action after its transient native receipt was pruned;
  it does not require another entity census or a player visit.
  No inventory changes occur. The next preparation has a new storage revision.
- Cross-owner constructor audits are deferred only within unpublished planning /
  reduction scopes. The mandatory pre-WAL publication, direct construction and
  complete bootstrap/recovery checks remain. Terrain-only publication still
  validates its complete changed geometry/exclusions read set.
- Optional native random samples, including all their ingress work, share one
  soft runtime allowance per actual native tick. An admitted sample finishes;
  exhausted samples are not queued or replayed. Explicit player interactions
  retain their existing causal protocol. This is cooperative admission, not a
  hard deadline capable of interrupting one indivisible native action.

## Evidence

`build/r274-runtime-repair/` contains the 45-second same-process JFR, full-depth
CPU extraction, scoped checks and native receipts. JFR identifies
`SpreadingSnowyDirtBlock.randomTick -> ManagedBlockWrites -> geology invalidation`
and repeated unpublished constructor audits. Owner-loop scheduled work is also
substantial; no complete-tick speedup is claimed before comparable live checks.

Focused Frontier/NeoForge checks passed (29 + 43 tests); guardrails, JAR and
packaged verification passed. The final native shared-storage/body/field slice
passed 54 required tests. Its deliberate attempted-effect negative emits an
expected ambiguous-operation warning. An initial incorrect reference-lane
invocation did not run tests and is not counted. A stale coarse-soil assertion
was aligned with the already-adopted separate earthworks predecessor.

Mossbrook's 47-coal shipment was not stalled: sealed snapshots at 171252 and
195852 prove route progress and an ETA near 217056. Payment at source is not
home delivery. Hunger while that cargo is in transit is not repaired by
teleportation or a fictitious receipt. Live recovery, movement and performance
results are recorded in the canonical continuity ledger after deployment.
