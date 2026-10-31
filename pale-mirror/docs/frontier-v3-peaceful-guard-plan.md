# Peaceful settlement guard

Accepted 2026-11-01. Two equipped guards per enabled settlement; no combat AI,
free swords, new residents or simulated weapon wear. Main alone; no subagents.
MAIN remains stopped under its existing explicit stop request. Verify/deploy in
an isolated disposable runtime; no automatic push.

Ownership:

- Hashed settlement staffing policy declares two guard places. Guard owns retained
  resident assignments and equipment-service continuation, independently of meals
  or the presence of a sword. Unfilled places already generate equipment demand.
- Common equipment demand distinguishes minimum serviceable units from optional
  forecast wear. Capability requirements do not require a fictional work operation.
  Weapons initially use local manufacture; missing inputs may be traded normally.
- Existing recipes/stations/material logistics manufacture individual iron swords
  from two ingots and one stick. Existing heat/fuel policy applies to the forge.
  Inventory owns item identity/title/custody and common service owns warehouse access.
- Common UAE owns execution, interruption and body; common navigation owns every
  equipment approach. Guard supplies its exact safe checkpoint, resumption, death
  and equipment-transfer validation. No assault/patrol owner is activated.
- Existing shared native equipment handoff and COLD actor-item custody perform
  the same exact issue. Pending or possibly-applied effects cannot be abandoned,
  duplicated, overridden by a player edit or replayed after restart.
- The assigned armed resident remains an ordinary independently hungry resident.
  Meals and spatial yielding do not erase assignment or transfer weapon title.
  Loss/death/unavailable storage lowers readiness and reopens the proper shortage.

Implementation sequence: general equipment demand/capability boundary and content;
retained guard owner + current-schema codecs; closed process/UAE/service/movement
wiring; presentation/diagnostics; connected negative/recovery checks and ordinary
HOT/COLD production-to-issue evidence. Verify no source-genesis sword stock and no
competition with essential staffing. Commit reviewed coherent work and deliver a
checksum-pinned private test build. No claim of combat behavior or ongoing weapon
consumption; two durable swords create an initial finite production demand.

Implementation and verification record:

- Schema314 retains exact guard appointment, home, resident execution, weapon and
  selected storage. All enabled r31 layouts have two places; initial stock has no swords.
- Equipment demand now expresses capability and optional operation wear separately.
  Swords have finite availability demand, use existing forge inputs/fuel and shared
  individual-item custody; production, logistics and resource title have no guard copy.
- Connected tests cover manufacture/issue/two ready, meal/resume, codec recovery,
  source withdrawal, damaged return, exact physical transfer fencing/confirmation,
  delayed route result, death while approaching and while a meal suspends the guard.
  Their event helper applies the production schedule-capability, state and reference
  publication checks; it does not skip schedule declarations.
- Full guardrails/check passed before the final runtime corrections. Subsequent
  focused kernel+guard tests passed126; shared native economy55/55 and navigation45/45
  passed. Final packaged artifact and native terminal receipt are recorded separately.

Runtime findings closed in this change: production had no stationary body admission
at a COLD-arrived contact; shared HOT actor movement cached an unresolved route hint
permanently; guard selection propagated ordinary PLANNING; guard continuation lacked
coalescing and schedule-subject registration. A general kernel defect retained future
ready work across a shorter execution horizon after fast-forward: bounded index
reclassification and horizon-aware dispatch now preserve its schedule without executing
it early; future read-only preparation may be withdrawn, effects may not.

Native carrier: `tools/frontier-v3-test-pilot/scenarios/disposable-peaceful-guards-restart.json`.
It starts with finite forge inputs and zero swords/appointments, performs ordinary
zero-player production, observes equipped guards, restarts and visits out of/into the
settlement. It is deliberately COLD-manufacture coverage; a previous full HOT run
witnessed one manufactured/delivered sword before exposing a registration defect,
and is not a successful two-sword continuous HOT acceptance receipt.
Use the ordinary isolated runner with `FRONTIER_V3_PILOT_GRAYBOX_DATAPACK_DIRECTORY`
pointing to the pack's agricultural-flat datapack whose `biomes.ruleset` explicitly
matches `frontier-v3-simulation-baseline-r31`. The runner records its digest. A stale
vanilla-flat companion pack is not compatible with this profile. Private Xvfb evidence
is automated graphical coverage, not human M3/visual approval.

The COLD prelude has6000 simulation ticks. Its450s finite wall bound covers an ordinary
20TPS interval plus initial planning; the earlier180s expired at4398 with no server
stall and was an invalid bound for this populated fixture, not an accepted result.
No performance improvement or stable80ms maximum is claimed by these feature checks.
MAIN stays stopped; deployment uses a separate fresh-schema private world, retains
previous benchmark worlds and does not publish a new client pack or push Git refs.

A later HOT delivery observation identified an additional connected navigation gap:
service waiting discarded the retained journey and attempted a distant native-only
buffer goal. The same shared, authority-checked actor route cache now supplies the
advisory approach for any service visitor with an owned movement. The buffer route
branches toward that visitor's selected pocket before the common throat, ends at its
own temporary target and never permits the occupied service zone. Grant changes
invalidate the cache so actual waiting position becomes the next route origin.
No service admission, resource transfer or owner goal is awarded by a staging leg.
Focused navigation checks and45 native local-navigation checks passed; the new
far-buffer test proves retained route/order, separate visitor destination and hard
service exclusion. This correction is generic, not a forge/depot/guard route.
The shared service-clearance owner also publishes one ordinary body inspection on
witnessed buffer arrival when canonical position differs. This fixes distance-based
admission retaining the remote origin; it neither records each step nor completes the
work/service order. Native local-navigation45/45 and focused navigation12 passed again.
