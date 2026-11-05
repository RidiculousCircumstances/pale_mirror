# Manual passages and warehouse access — 2026-11-05

## Diagnosis

The three iron swords contributed by the player were recognized correctly. All
three guards remained EQUIPPING outside the selected warehouse from about
15:01 until the first service arrival15:03:55; subsequent arrivals followed
15:04:00 and15:04:07. Snapshot43200 and live45115 confirm all three actual sword
custodies and ON_DUTY state. The exact historical incumbent for that whole wait
is UNCONFIRMED: earlier snapshots were compacted and the previous log lacked a
complete turn-holder history. Retained read-only evidence is in implementation
`build/guard-equipment-inspect/receipts/`.

A concrete shared source contradiction was found: an idle body occupying the
service boundary queued/searched its final parking position before leaving the
access throat. `FrontierV3ServicePointClearance` now first requests supported
egress through the existing navigator and publishes its actual arrival through
the sole body controller; only then can parking search occur. No timeout,
teleport, equipment recognition shortcut or separate actor writer was added.
`ServiceAccessCoordinator` remains sole arbitration owner. Its pure bounded
decision now exposes allowed/reason/current actor through container diagnostics
and change-only movement wait logs, so the next incumbent can be identified.

## Connected latency found during live verification

The first live438099a82 run supplied three swords through ordinary container
packets16:16:24. First guard ready16:17:06 (42seconds), others followed. New
turn diagnostics identified the incumbent FinnWood/resident:1-15. His input
pickup was recorded16:16:39, but other visitors saw CURRENT_OCCUPANT until his
subsequent station route finished around16:17:03. Static tracing established
the cause: generic HOT `observeDirectedArrival` asks
`witnessedActorMovementExit`, which previously returned false unless the leg
had an explicit `clearancePoint`. Ordinary `StationWorkLeg(STATION_LOAD)`
correctly declares no separate clearance job, so it failed to publish the
intermediate physical exit. The old production scene hook did not cover this
common movement path.

Follow-up04fdc48e2 fixes the common physical observation, not a bakery-specific
exit. The existing indexed operational boundary declarations are queried at
the actor's retained body column; actual first exit triggers the sole body
inspection and existing service wakes. Current task, order and carried goods
remain unchanged. Destroyed/nonoperational points do not fence departure.
The existing station lifecycle regression now also proves HOT first-exit
recognition for an ordinary leg, no release from phase/route acceptance alone,
no repeated release, unchanged cargo/job, COLD checkpoints and recovery.

## Door/gate ownership

Before this cut, merely enabling vanilla door flags was insufficient: resident
vanilla AI is suppressed and the custom movement boundary had no common manual
interaction owner. Source438099a82 adds the following shared integration.

- `UnitNavigationProfile.operatesPassages` is explicit, persisted and defaults
  false for a generic profile. Villagers declare true; zombies/donkeys do not.
  A group envelope uses the declared capability of its participants.
- Actual `NavigationCell.shape` remains the physical truth. Optional
  `passageShape` supplies a native-compiled open alternative only to capable
  transit. Exact stations, body placement and collisions use the actual shape.
  Authored building certificates carry the same compiled alternative.
- `FrontierV3PassageNavigation` consumes the common navigator's current sealed
  permission and nearby path nodes. It uses vanilla hand-operable paired doors
  and fence gates, reopens if closed during pursuit, and closes after passage
  when nearby living actors are clear. Already-open doors are not claimed.
- Close claims are bounded to256 per weak native level, retain native preimages,
  weak users and passage side, and are revoked by external writes. No chunks
  are force-loaded, and stale closes are never replayed after unload/restart.
  Async mutation callbacks revalidate the exact current permission/preimage.
- These reversible native properties use existing managed-block write/knowledge
  fencing. They are not inventory consumption or canonical task completion; no
  new nonreplayable resource-operation journal was invented.
- Manual reach1.5blocks is in hashed `PedestrianTraversalRules`. Iron doors
  remain blocked. Trapdoors/unknown mod closures have no invented support.

No role-specific guard/bakery mover exists. HOT work, patrol and combat all enter
the same navigator; COLD geometry uses the same explicit transit capability.
Wire337 deliberately rejects336; the disposable live world was recreated.

## Verification

- Full integration:2333 Frontier cases exposed one obsolete production test
  expectation (source approach is valid before container write authority);
  NeoForge920 passed. The corrected test preserves the exact input claim and
  proves no premature physical production.
- Final focused integration:130 Frontier +99 NeoForge passed, guardrails and
  packaged JAR checks passed.
- Full native run:442 required cases exposed one obsolete accelerated patrol
  readiness assumption; all51 local navigation cases, including all three new
  manual-passage cases, passed. The existing bounded semantic HOT-admission
  helper replaced that tick assumption; corrected patrol4/4 passed.
- Native manual cases prove closed wooden-door traversal/reopening/closing,
  fence-gate traversal/closing, iron-door exclusion and damaged-pair handling.
  Pure coverage proves wire rejection/roundtrip and actual station/collision
  versus conditional transit separation.
- Real native building-authoring export passed and regenerated the bakery's
  four closed-gate certificate entries. Template/NBT/visual assets unchanged.
- One earlier aggregate test invocation was invalidated by concurrent compilation
  replacing its test class output; it was stopped and not classified as a
  product defect. Later coherent integration runs above supply the evidence.

Detailed local logs: `/tmp/pm-passages-final-integration.log`,
`/tmp/pm-passages-final-check.log`, `/tmp/pm-passages-final-native.log`,
`/tmp/pm-passages-patrol-final.log`. The full graphical pack scenario is
`tools/frontier-v3-test-pilot/scenarios/live-manual-passage-pursuit.json`,
SHA256cdac0f1774e7f53a8b8c4b3675ba383fee64979ba762124d9f59458c46f02796.
Final source follow-up:26 affected Frontier tests and47 pilot/parser tests
passed, guardrails and JAR verification passed. The existing ordinary player
walker gained explicit optional jump-key support and honors its declared
timeout; no NPC controller or scene repair was introduced. Earlier client
attempts were stopped after operator-profile mismatch and invalid player
corner/step waypoints. One exploratory player-only levitation command was used
to diagnose that walker limitation; that run is not acceptance evidence.
Final full-pack20FPS run `build/passages-native/full-pack-final.json`,
run0904bf0e-f306-4202-a180-bb537ef3ccf2, is a FAILED PLAYER FIXTURE, not native pursuit
acceptance. It contributed all three real swords16:27:17; first guard ready
16:27:43 (26seconds). All three were ready by the final read. Its player visit
used firstGuardPosition+2, an unsafe location next to the depot fabric, then
its straight waypoint was obstructed; the newly enforced20-second walk bound
terminated it16:28:04. No NPC was teleported or repaired. Final scenario2ea05fb78
starts the player at a fixed exterior point before provocation, uses explicit
jump key and leaves the initially open entrance unchanged until closing it
after entry. This corrected fixture was syntax checked but NOT replayed.
The 42-to26-second observations are different live episodes, not a controlled
latency benchmark or proof of instantaneous admission under contention.

Thus the door/gate mechanic is native-tested, while a complete real-client
guard pursuit through the bakery door remains unconfirmed. No human/whole-life
acceptance is claimed. Task client/private display exited and both task-created
operator identities were removed; human rd and unrelated operators preserved.

## Deployment

Verified clean detached final release2ea05fb7859243b33bc4bc7b43cee7991a9343d8;
product changes438099a82 +04fdc48e2; final commit only corrects the player fixture.
Initial438099a82 rollout was superseded after the connected native finding.
JAR SHA512:
`3efc1cc68f1ff5f02f36615a049d5760fa33c7c96fff20d99bb8d79723ca12904b826686aec7042a0b3e91d26ed3ee73e9b721e5d87c9fd293a81f1317c17885`.
Visuals unchanged:
`2fd468cb382d64fa62eaa2cb2239d7bd75c89a4a06c21ce1bf2a15f0775db02e5daf2591b91863b74d4dc87a90067ef714dd631e4174daf79c9caad78f994601`.
Old world `authored-buildings-nav-rules-seed139-20261105` saved all dimensions
16:10:17 and retained for diagnosis. Fresh world
`manual-passages-final-seed139-20261105`, same seed139/r31.
Intermediate task world saved all dimensions16:25:43 and retained for the
connected service-access diagnosis. PACK preflight,
publish/index verification, pinned install and fresh deploy verification PASS.
Server Java22, source build Java21; service `far-frontier-v3-live.service`,
wrapper452023, restart epoch1793878254. Final same-world normal save16:30:23
and restart prove current wire readmission; summary4669 green, required/scene/
inventory conflicts0, incidentIndex0, three guardsReady, quarantineNORMAL.
Progress remains NOT_ASSESSED. IMPL/RELEASE clean; PACK tracked clean with
pre-existing `.f0v-baseline/`; unrelated GOV WIP preserved.

No broad economy, TPS, combat balance or historical three-minute culprit claim;
no background monitoring and no whole-life M3 promotion.
