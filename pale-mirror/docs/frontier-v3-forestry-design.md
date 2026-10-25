# Forestry: tree knowledge and COLD execution

Status: delivered R265/r28/schema306, 2026-10-25. Native intervention, HOT/COLD,
normal-stop closure, partial warehouse delivery and reserve cutoff are evidenced;
full-pack preflight passed. The accepted design is implemented in the
ledger-named checkout. Scope and actual evidence are recorded in
[PM-FORESTRY-20261025](work-orders/PM-FORESTRY-20261025.md) and the source repository's
`docs/forestry-r264.md`. This document defines the accepted boundaries; its design
statements alone are not runtime evidence.

## Accepted compact tree knowledge

Capture the trunk-position set already assembled by Minecraft's tree generator
after successful placement, both during initial generation and subsequent tree
growth. Keep ordinary native blocks. Persist compact tree records in chunk data:
explicit tree identity/generation, origin, bounded footprint and packed relative
log positions. Cross-chunk trees need one owning record and bounded links, not
independent harvestable copies. No leaves, per-log block entity, unique tree
BlockState, global resident tree registry or permanent log-to-tree reverse map.

Load records for the bounded planning territory into a limited cache. Tree
selection uses origins/footprints; only selected work needs its full log set.
Do not tick or rescan every tree. A successful new growth creates a new tree
generation even at a previously used origin. Failed growth creates no record.
External generators require a declared compatible adapter; arbitrary connected
logs are not automatically a natural tree. TreeChop is not a required dependency.

The captured record describes generation provenance, not perpetual live state.
Before extraction validate the selected current blocks and relevant mutation
evidence. Same-material replacement cannot be distinguished by BlockState alone:
the common mutation ledger must fence replacements in affected footprints.
Avoid rewriting tree membership for each edit; use existing change evidence and
current-state validation. Never restore historical logs over player changes.

Ordinary natural leaves use Minecraft's native distance/decay behavior. Decay
is gradual in naturally ticking loaded areas. Other surviving logs can support
leaves; persistent leaves and nether fungus blocks need separate semantics.
No claim that vanilla ticks decay leaves in an unloaded region.

Engine evidence: pinned MC1.21.1 compile snapshot
ac5901effb63c8add2e7c49e51ec16803ded5b4fa4ce09c982f62b9aa90c1075,
TreeFeature.place collects trunk positions before returning; LevelChunkSection
stores paletted BlockStates; LeavesBlock updates distance and decays nonpersistent
distance-7 leaves. No runtime forestry benchmark or implementation exists.

## Proposed COLD execution

Use the same tree, actor, work progress, tool condition, extraction accounting,
resource custody and hauling as HOT. COLD integrates labor/travel to causal
boundaries; it does not run native block ticks, scan every trunk every tick or
load chunks to make work possible. Existing execution semantics remain binding.

1. The settlement selects known trees against actual resource demand. Captured
   world generation data is not automatic settlement knowledge. Unknown terrain,
   trees or extraction preconditions need an explicit evidence/inspection policy.
   A coordinate/seed is not permission to invent a not-yet-generated forest.
2. The shared work mechanism reserves the selected tree scope. Navigation uses
   known reachable work access. Tool capability, work norm, actor modifiers,
   schedule and interruptions retain their ordinary independent owners.
3. Retain incomplete work and process only due causal boundaries. Completion
   must use a supported COLD extraction capability with sufficient retained
   block/loot/tool context. A generated log list alone cannot certify arbitrary
   modded loot or authorize unconditional output. Unsupported context remains
   an explicit local wait/alternative, not fabricated resource production.
4. A permitted canonical extraction commits actual resource custody, tool wear
   and its versioned block aftermath exactly once. Transport still takes time
   through the existing hauling/resource mechanisms; wood does not appear in
   a warehouse merely because cutting finished.
5. On natural chunk load, shared reconciliation realizes the current logged
   footprint against its original generation and subsequent mutation evidence.
   Do not replay historical chopping animation or mint another native drop.
   Persisted pending/ambiguous physical effects retain existing recovery fences.
   Reconcile the bounded footprint before exposing it for managed interaction;
   no region-wide replay or synchronous full-forest scan.
6. At an interrupted HOT/COLD handoff preserve the same actor, partial work,
   cargo and reservations. COLD cannot advance a scope still held by an active
   physical owner merely because there is no viewer. HOT resumes only remaining
   work from the current canonical/physical checkpoint.

## Resolved implementation choices and remaining evidence

- Supported initial content is oak. An exact whole-tree batch is admitted only
  within the common64-item cargo bound. Source loss, partial player destruction,
  labour and tool failure retain ordinary shared fencing and custody semantics.
- Authored regional geometry is lazy; actual generation/growth uses compact
  chunk provenance. Neither path force-loads native chunks for COLD work.
- Leaf aftermath belongs to vanilla. COLD log depletion does not claim a fully
  vanished canopy before native decay, and neighbouring trees can sustain leaves.
- Real native HOT/COLD/restart, new growth and external replacement receipts
  passed. The ordinary live world has separately manufactured two axes and deposited125
  earned logs. The native campaign observed HOT shutdown with pending
  effects and successful resumed extraction, then proved actual partial deposit
  and reserve cutoff after an ordinary player supplies additional stock. Initial tools/stock in that fixture are explicit;
  it is not an autonomous manufacture-to-full-reserve acceptance claim.
  Performance receipts retain warm-up outliers; no absolute latency claim.

## Connected corrections found during native verification

A normal stop must close an owner's exact unused native intents while the live
attempt permission still exists. Forestry uses the shared prepared-operation
withdrawal before source shutdown. An attempted or ambiguous operation remains
fenced; recovery cannot infer non-application from matching preimages. This is
owner lifecycle integration, not another journal or relaxed replay policy.

A named support block does not promise that Minecraft centers the worker on it.
The shared work-access envelope includes the block's lateral extent and half the
producer-declared body width. Candidate selection and validation use that same
conservative envelope; the actual native hit radius remains unchanged. The
observed failing position was4.504blocks from a log despite a legal nominal
cell center inside4.5. The correction applies to all users of BlockWorkAccess,
not a list of alternative tree positions. Vertical assumptions are unchanged.
