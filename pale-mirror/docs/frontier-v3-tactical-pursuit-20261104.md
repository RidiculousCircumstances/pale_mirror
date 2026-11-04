# Tactical pursuit and maneuver admission

User accepted five changes following human capture6e4b5245: finite pursuit/flank
runs, bounded observed-motion interception, contact handoff, legal evasion before
withdrawal, and separation of transient access from parking restrictions.

## Responsibility and implementation

Production4f6137ef4. Schema330 intentionally requires a fresh disposable world.
No client protocol/gesture vocabulary changes: PURSUIT_RUN uses the existing
BURST presentation; actual kind is explicit in canonical state and combat trace.

- Common `MeleeDuelPolicy` selects a PURSUIT_RUN outside real contact when its
  explicit role permission, shared readiness and retained cooldown permit it.
  Short attacking BURST and its weapon-reachable endpoint remain separate.
- Existing `MeleeManeuver` owns the exact fixed goal/lifetime/body/target and new
  independent runReadyAt. Other maneuvers, early contact, hydration and body
  handoff cannot refill it. Retiring the combat owner removes its history.
- Hashed `PursuitRules`:6horizontal blocks,1tick preparation,30tick travel,
 2tick recovery,70tick cooldown from selection,12tick shared cooldown,
 3000permille walking pace. For an ordinary2block/sec resident this requests6.
  Existing35readiness cost is reused; weapon cadence is unchanged. Cooldown
  forbids another run, not walking or striking.
- `MeleePursuit` ranks actual supported candidates for pressure or the existing
  encounter flank sector. Observed velocity leads by6ticks, capped at2blocks
  and0.35blocks/tick; no future input or guessed surface. The selected goal is
  fixed for the short run, so a player can change direction and evade it.
- Fresh native contact can checkpoint/close this exact run or attacking burst
  before Prepared, preserving actual position/cooldown. It cannot award arrival,
  interrupt another movement owner or prepare a duplicate weapon effect.
- `CombatCapability.walkingPace` supplies the role's unaccelerated gait;
  maneuver movement applies its multiplier exactly once, avoiding dependence on
  an already accelerated intercept gait. The common native navigator calibrates
  owner-issued distance pace under1.0safety modifier, while unpaced/ambient
  fallback remains0.7. This removes a cap which truncated the requested6block/sec
  run even for an ordinary0.5movement-attribute body. It does not edit attributes.
- Shared service access allows an unclaimed public waiting apron as a finite
  landing while rejecting actual service boundary, active egress and indexed
  body/destination claims. Rest/idle placement remains stricter. Physical
  clearance, private passage permissions and routing remain with shared geometry.
- Shared `MeleeManeuverPlanning` assesses destination/alternative side/route for
  both actual admission and optional reversible-attack withdrawal. The preview
  also requires an exact movement checkpoint; failed/pending geometry keeps
  the existing attack. Positive unused MELEE_ATTACK journal cancellation remains
  mandatory. Preview is not a spatial reservation or guarantee against later
  world/target changes; actual maneuver admission revalidates current facts.
- Optional recording includes runReadyAt and detailed rejection-stage counts.
  No extra polling process, actuator, scheduled family or native COLD hit.

## Focused evidence

/tmp/pm-pursuit-focused-final.log PASS38seconds:
25CombatModule,35SettlementSecurity,11GuardDuty,4FrontierPersistenceCodec,
10OperationConfirmation,2NavigationPace,5MeleeAnimation (92cases), guardrails.
Existing contact/recovery tests now cover both BURST and PURSUIT_RUN; maneuver
lifetime covers BACKSTEP and PURSUIT_RUN. New regression obligations: independent
cooldown hydration, no refill through another maneuver, one base pace, bounded
observed lead, contact without destination arrival, service versus transient
apron/body claims, and native calibration beyond the old walking cap.

Native strikes18required pass39.75seconds (/tmp/pm-pursuit-native-strikes.log),
including unused preparation cancellation/recovery, actual hits/no replay,
interposition and native contact windows. Local navigation46required pass3.684
seconds (/tmp/pm-pursuit-native-navigation-final.log), including low-roof refusal,
raised lateral movement and existing non-flat/common navigation cases.
One initial navigation invocation used unsupported slice scene-local-navigation;
no tests ran despite Gradle exit0. Correct registered local-navigation was run
and its actual required-case totals verified. Initial compile/style corrections
were scoped import visibility/line formatting, not weakened behavior checks.

Final JAR/package PASS3seconds; clean detached release guardrails/JAR/package
PASS10seconds (/tmp/pm-pursuit-{package,release}.log). Human combat balance is
not established by these tests. User will assess the new behavior manually.

## Deployment

Root preflight/publish/install/verify receipts use /tmp/pm-pursuit-*.log.
New selected world: tactical-pursuit-schema330-seed139-20261104, seed139/rootr31.
Prior329world and human trace remain diagnostic evidence. No rd player state
edits; no new client update required. Runtime Java22PID1811874/wrapper1811850, start1793801273. Installed SHA512:
7296d5295b1e6bcce43a2a73e233dd2122eeeb05763d2a2369156bc7d615bb646a32c867e4bbeaa4b4b410fa450d506c5726ab450475489f8f25386ac1e9deef.
Native old329all-dimensions save/JVM+ports exit preceded install. Old329world
retained. Fresh-world preflight and post-start/final verification PASS. One early
verify correctly refused the not-yet-listening startup socket; the ready runtime
was checked again. PACK tracked clean888ed90 with old untracked.f0v-baseline/;
RELEASE clean detached4f6137ef4. IMPL clean successor a161996ef adds only the
checked-in equipment setup scenario. No push/goal/agents.

## Manual-test preparation and separate storage finding

One20FPS fullpack client at a time on task-privateXvfb, all stopped afterwards.
Final equipment diagnostic09665c13-00f8-488b-a9fc-b365a47fe940 completes9ordinary
actions: first sword donation is confirmed as canonical occupiedCount1 before
sending the second; actual runtime appoints Nora Bell1-16and Eli Stone1-22.
At27945Eli ON_DUTY/armed, Nora EQUIPPING. This is diagnostic donated equipment,
not proof of natural production or combat balance. No battle run in this delivery;
user will assess difficulty. Current settlementstatusok/NORMAL/activeCases0,
contact/responders/maneuvers0. Actual PMTestPilot2 temporary operator removed;
no human rd state edited. Offline profile lookup initially granted lowercase
pmtestpilot2 instead of the connected case-sensitive offline UUID; ordinary op
selector plus explicit console setup corrected only that task player. This
supplemental prelude is retained in server log; don't mislabel the run as an
unassisted repeatable graphical product preflight.

Earlier original equipment fixture fails a click in1-depot with
FUNGIBLE_LAYOUT_MISMATCH:stock. A newly placed chest was then filled before its
adoption became authoritative; its two swords became fungible stock, not usable
individual equipment. A later managed click observes both old untagged swords as
new individual arrivals and raises one-click-produced-multiple-individual-arrivals
at FrontierV3PlayerClickObservation43. Native PLAYER_CONTAINER_CLICK operation
2fcd143b-414f-45fe-8604-6fa25fcc8c9f becomes AMBIGUOUS/EFFECT_EXCEPTION, blocking
further clicks by PMTestPilot. This is a real separate existing storage/adoption
boundary problem, not a combat regression. No forced completion or erased receipt.
The final diagnostic uses a clean already admitted chest at(-1468,64,-1343),
awaits its ACTIVE/ACQUIRED state and uses a separate task player; no claim that
this repairs the earlier storage problem. Affected failed manifests retained in
IMPL/build/combat-pursuit-20261104/; separate underlying storage repair remains
open. Failed predicted client UI movement is not canonical acceptance evidence.
The earlier invalid place-in-setup declaration and absent private process root
were fixed before proceeding; they launched no misleading acceptance.

