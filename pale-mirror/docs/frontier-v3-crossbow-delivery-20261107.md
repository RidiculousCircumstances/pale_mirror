# Crossbow guards and ammunition — 2026-11-07

Implemented in source 472bcccb4, schema344, persisted ruleset
frontier-v3-ranged-guards-r33. Implementation checkout is
`/home/rd/proj/pm-f06r3-facility-lane-recovery/pale-mirror`; clean detached release
checkout is `/home/rd/proj/pm-r242-economic-release/pale-mirror`.

## Product and ownership

Forge crossbow:4 sticks,1 oak plank,2 iron ingots,2 cotton string→1 crossbow.
Forge arrowheads:1 iron ingot→8 heads. Arrow assembly:4 heads,4 sticks,
1 cloth→4 vanilla arrows. Existing multi-input attended stations, combustion,
input supply, reserve planning and goods market own production. No free ammo.
Crossbow durability465 matches the actual Minecraft registry; repair restores232.

Security coverage plans staffing; r33 allocates40% of guards to crossbows while
retaining at least one melee appointment. Every appointment declares CombatMethod;
shared combat consumes its typed CombatUse. Existing actor execution/navigation
owns approaches and retreats. Ranged positioning keeps distance, avoids service
areas, shares spatial claims and scores allied fronts to favor an offset lane.
Native LOS/solid ray and actual living-body clearance prevent firing through
cover or allies. Reload/aim precedes vanilla projectile emission. Personal arrows
remain ordinary resource accounts; stock is acquired in real32-arrow loads.

One common physical journal owns emission, arrow debit and weapon wear. The
actor retains a deterministic emission witness. Projectile flight/hit itself is
vanilla transient state: absence after restart cannot authorize replay/refund.
COLD performs production and logistics; it does not simulate combat.

## Native defects found and repaired

- Equipment issue/return previously confirmed its family and whole storage in
  separate commands; pending persistence could become a fatal error and work
  could resume before storage closure.2028b57cc adds one common proof-bound
  PhysicalIntentStorageConfirmed command. It dispatches the explicit registered
  lifecycle and appends exact closure or isolated foreign-image conflict.
-5a24fb7c1 corrects a phase-name error introduced in that integration; full intent
  IDs are no longer passed as the bounded CommandIds.physical phase.
-0d987d397: guard ammo discovery admitted PREPARING/unbound stock that its reducer
  correctly refused. Read-only replay of the actual failed world reproduced64
  arrows, PREPARING epoch6, no bindings and the bad Selected. Selection now uses
  the same shared canReserve fence. No ownership or physical checks were weakened.
-ad9d2a07f: new load/abort and shot-completion payloads lacked the durable flag
  required by the shared physical outbox. Fixed all affected outcomes, verified
  actual file-journal recovery, then recovered the already-applied transfer on
  the same world. No reset or duplicate32-arrow transfer.
- The pilot formerly retried no menu clicks after explicit unchanged-image
  refusal. It now retries only a server-acknowledged unchanged preimage, not
  an ambiguous outcome. Actual Minecraft suppresses correct prediction echoes.
- Full settlement diagnostics exceeded their bounded response budget; a separate
  small settlement_security view exposes guard/ammo state without raising it.
- The first combat fixture teleported relative to an old semantic route origin,
  leaving the personal defence envelope. Corrected the carrier to keep the
  player present while the shooter retreats. No pursuit policy was loosened.

## Verification

Focused canonical cut:116 checks passed before the final ammo additions;
9 RangedGuard cases and13 native OperationLedger JVM cases passed for the final
changed boundaries. The earlier canonical cut includes three actual station
manufacturing transforms and codec/replay/negative guard/combat checks.56 pilot
Node checks pass. Java pilot selector parser48 checks passed.56 native local
navigation checks passed, including four new ranged boundary checks. Five native
reference conflict/restart checks passed. Guardrails, packaged JAR and clean
release build pass. Detailed local logs: `/tmp/pm-ammo-binding-gates.log`,
`/tmp/pm-ranged-durable-ledger-gates.log`, `/tmp/pm-crossbow-exact-detached.log`;
earlier cut logs are referenced by CONTINUITY/archive.

Checked-in real-client scenarios use PMBakeryAudit, the existing full pack with
EMF/ETF and a private20 FPS display. World remains
crossbow-guards-fixed-seed139-20261107, seed139; no reset through the last repairs.

- Equipment recovery:08dfbf63-d80c-4e54-83e3-16d6dc30a0be, same resident:1-4 with
  actual32 arrows after interrupted transfer. Manifest
  `build/crossbow-native/durable-outcome-recovery-manifest.json`.
- Support fire:6179dd55-6a01-41fe-b5b1-28ab5b1fa836,32 → 31 arrows, first emission.
  Manifest `build/crossbow-native/local-defence-fire-manifest.json`. Its separate
  combat capture failed at the16-retained-capture limit; no full-trace claim.
- Exact reload/shot:ee94e4ad-b727-48b0-92fe-b8c598fada2b, read-only fixture 31 arrows
  after native restart, prepared shot, terminal 30 arrows/no pending shot.
  Manifest `build/crossbow-native/exact-shot-manifest.json`; complete capture
  aaaa7020-4a2a-463d-9f7e-a16991d33668 has1104 events, no loss/export truncation.
  At simulation20950, Leon Lake's arrow deals 4.5 damage to PMBakeryAudit. Native
  weapon damage changes 1 → 2. Frames under build/frontier-v3-scenarios use the
  same run ID and names crossbow-visible-reload/crossbow-exact-shot. Reviewed:
  visible held crossbow; reload view partly occluded by a sword guard.

Normal final stop saved all dimensions at03:27:49. Read-only saved snapshot/WAL
recovery verifies30 carried arrows,62 total owned arrows and crossbow damage 2:
64 original arrows less2 real emissions. `/tmp/pm-crossbow-final-save-offline.log`.
Final unchanged-artifact restart verifies30 carried arrows/no pending shot again,
without a client or replacement stock. Deploy verify PASS, service wrapper2787562,
invocation fbb3c0e7b8774088b77ebdb736de6324, notBefore1794004089.
`/tmp/pm-crossbow-final-verify.log` and `/tmp/pm-crossbow-final-actor.log`.
Summary has0 inventory/scene conflicts and0 preparing replicas. It deliberately
retains two historical incidents: UNKNOWN equipment intent at9862 (that exact
intent is now CONFIRMED) and the repaired ammo-selection kernel failure at11207.
Thus its retained-conflicts verdict is blocked; this is not a new runtime
quarantine or an active unresolved source. Incidents were inspected, not erased.

## Limits and delivery

No whole-suite-green or M3/player-comprehension claim. Earlier full native460
had18 patrol/reference/adoption/production fixture failures, not all classified;
clean previousce872 reproduced six old failures in a focused19-case comparison.
Whole native manufacturing, economy balance and prolonged coordinated combat
remain outside this targeted validation; canonical/common-production evidence
is not a substitute for observing a whole native manufacturing chain.

Published/installed core SHA512:
`b9513a2f219551dcf37139705227683648b94b0a69496fdda58cd6da940a18c29158dbd19f360b904332549ec7014a3fcd6c49297fa4c6873cc0f9e2dfc2970a`.
Visuals JAR unchanged. New custom arrowhead content requires the ordinary client
updater. Pack index170 files verified; no tracked pack change. No push or agents.
