# Native melee contact repair — 2026-11-03

User observed orbiting and occasional windups but very few hits even while
standing still. Baseline source f2de641e5 on MAIN world138. Repair is committed as 3fb952955889ee136e6864cbd24daf768ec5dc8d and deployed
to the same MAIN world138; no reset or new protocol/schema was needed.

## Source findings and ownership

* FrontierV3CombatExecutor started its finite native cue before creating the
  deferred MELEE_ATTACK operation. Journal admission began at the intended
  impact, so asynchronous durability could spend the entire 3/4-tick contact
  window. The real effect predicate then correctly rejected that expired cue.
  Preparation now uses the existing NativeEffect preparation boundary, after
  durable intent and the canonical earliest-impact admission. Only then does
  the full native windup start. No synchronous disk wait, enlarged reach,
  removed LOS/cone, changed invulnerability or late damage permission.
* Native stance and attack eligibility required ideal formation alignment.
  A flanker already beside the target could therefore be ordered away instead
  of taking a real opportunity. Position choice still owns its assigned side;
  observed physical contact independently permits holding and a coordinated
  attack. An optional gap-closing burst no longer preempts an available attack;
  permitted defensive backsteps remain.
* Forced angular signs turned a slight overshoot of the assigned flank into
  nearly another whole orbit. The destination side remains stable, but the
  approach takes the shorter correction toward it.
* User additionally requested intermittent fast movement. Ordinary pursuit
  and flank approach now use WALK/close CAREFUL. RUN is limited to a pending
  lunge or the existing bounded maneuver. Existing maneuver policy supplies
  finite travel, recovery and cooldown; no second clock or role-local speed
  writer. UAE/shared navigation still actuate all movement.

No durable schema, payload, world rules or protocol version changes. Shared
combat owns mechanics; guard permission adapter and engagement owner retain
membership, target authorization, slots and body epochs. The change applies to
registered combat capabilities, not a guard-specific damage shortcut.

## Verification and delivery

Focused CombatModule regression adds flank overshoot and absence of permanent
sprint. Native melee boundary tests cover delayed admission -> full visible
windup -> one actual hurt -> no replay, cancelled and expired cues, plus the
existing vanilla player/weapon hitbox comparison. Final scene-strikes11/11 PASS, including earliest canonical admission,
/tmp/pm-contact-final-game.log (1m10s). Guardrails/focused combat-security54/
NeoForge904 PASS, /tmp/pm-contact-final-check.log (3m11s); final release
guardrails/jar/verifyPackagedJar PASS14s, /tmp/pm-contact-release.log.

Checked-in retained-stationary-melee uses the real previously saved seeded
combat world with two manufactured guards and remembered player hostility.
Only ordinary player setup, read-only fixture, actual client observation and
player health assertions; no NPC arrangement or WAL edits. It checks repeated
health loss, followed by escape and encounter retirement. Warm-world evidence
must not be described as a fresh-world/full-pack/M3 acceptance.

Native retained-stationary-melee8/8 PASS after ordinary graceful save/restart,
IMPL/build/melee-contact/native-3.json, run38fec2e0-63e3-49d6-973f-5e2dd7288e6d.
Over240 ticks the real stationary player lost36 health (1000->964), six native
hits by resident1-16 with target.hurt=true. One other declared participant was
in the encounter; this is not proof it hit as well. After escape, health stayed
964 and contact/responders/engagement retired. Exact same hostile player UUID
and manufactured weapons persisted; body physical epoch advanced after restore.
The prior scoped observation similarly recorded seven hits1000->958. Its runner
receipt failed because the terminal health action produced no diagnostic for an
assertion tied to that action; adding explicit terminal inspect corrected the
method, without changing product code. Initial setup lacked pmresponse operator
permission in the reused private runtime (only PMTestPilot had it); ordinary op
setup corrected that before the successful run. No product outcomes fabricated.

The linked player-view frame was reviewed: actual armed guard at close contact;
this lite profile intentionally has vanilla appearance, with a large test-health
HUD. No HumanEra/full-pack appearance or M3 claim. One20FPS task client used,
then exited; private server saved normally15:43 and25583/4 plus Xvfb stopped.
Prepared-4 source fingerprint33ff44b6c9df34e18c35704300d02a45ffd9dccfdcba515b8901733ef73e4452
matches committed source (committed-source-reuse.json). Native artifactSHA256
29ec0cdcc7047f093be029c3c30434f8271f56b8d3b45be7fbc837ae7ffa21b0.
Detached release matches all6690 ZIP entries except runtime identity properties
(release-class-reuse.json); source proof, scopes and byte dependency retained.

PACK164 indexed inputs verified, PM artifact published with unchanged Visuals.
MAIN saved all dimensions15:45:20, old JVM3094242 and ports closed before shared
installer. Same world item-ingress-schema324-seed138-20261103; new service started
epoch1793702763, wrapper3263895/actual Java22PID3263929. Exact release SHA256
3d9e8e589f3aac9152f3eb643687f4b6730730fd4f96f1ab98cb38189f4af0ea;
SHA512b3394aeeed7845dcb6bec423f45e22968c84340b882d301c0280cba94591ce1c88e1ef8df6db23a5bb599dd4f42ecd45ad51d7ea1114629372f087c082e717c9.
Preflight/install/fresh post-port deployverify PASS, /tmp/pm-contact-*.log.
First verify was invoked before the socket opened and rejected correctly; the
post-ready verification passed. MAIN summary51630/rev137645 green, required/
scene/inventory conflicts0, incidentIndexSize0. This is fresh startup/state
validation, not an automated full-pack combat playtest. No new client/network
dependency. No push. IMPL/RELEASE clean; PACK tracked clean with pre-existing
.f0v-baseline/. GOV unrelated WIP preserved, only own map line/ledger/note staged.
