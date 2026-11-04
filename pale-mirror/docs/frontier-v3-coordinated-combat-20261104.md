# Coordinated pressure and flanking

User accepted the five-point group-combat follow-up. Implementation690476586;
related observed meal-route crash correction2dea7fb73. No new actuator or role AI.

Existing SecurityDispatch owns whom the settlement releases. Its configured
respondersPerThreat team can respond near a confirmed target; a threat within
home observation range may recruit home patrols within the existing pursuit
margin. Distant deployments retain percentage reserve; fresh permission and
first-responder fairness across threats remain. Explanation uses the same pure
quota, without issuing routes or appointments.

Existing CombatEngagement owns membership, stable tactical bearing, exact role,
preferred role and roleSince. Initial free roles favor the nearer side. Shared
CombatFormation is a pure assignment strategy inside that module, independent
of GuardDuty. A confirmed unavailable approach must persist20ticks and respect
40tick assignment commitment before SUPPORT; route/space waiting and unsettled
hits/maneuvers cannot trigger suspension. Preferred flank remains explicit.
After60ticks, a support fighter outside real contact can retry a different less
covered side. Outstanding route/space waiting is retained. Reassignment clears
only that execution's local position/rejection history, preventing immediate
UNREACHABLE from the prior attempt; shared navigation facts and pending effects
are untouched. Current-format codec persists the declaration; schema331 rejects
old worlds, without inferred migration.

MeleePosition remains the sole combat station-claim owner. Final flank claims
must lie in the assigned sector; outer candidates must improve the existing
arc objective over supported, permitted geometry. Ordinary geometry, route and
body claims still decide availability. Native contact can still yield an
opportunistic strike without completing an orbit. Pressure contact suppresses
optional lateral withdrawal while its partner is repositioning; cadence,
recovery, effort and body/journal fences remain.

Encounter waves prioritize a ready partner on a different observed side. At
least60degrees separation permits same-opening short windows, retaining
individual windups and at most2live pending strikes. Other slots use2tick spacing;
no fighter waits indefinitely for its partner. No damage/reach buff or future
player input. Trace now records encounterRole/preferredRole/roleSince and actual
approach status/station. Guard diagnostics expose the retained assignment.

Verification:29CombatModule,35SettlementSecurity,8SecurityDispatch,11GuardDuty,
4FrontierPersistenceCodec cases; guardrails PASS. Native19required strike cases
PASS43.73s, process70s (/tmp/pm-group-combat-native.log), including full maneuver
presentation/codec and existing native effect/replay/cancellation checks. Final
retry-negative regression29cases PASS6s; final code/package guardrails PASS.
Receipts /tmp/pm-group-combat-{focused,final,retry-final,release-final}.log.

Before deployment the previous330server was discovered already stopped at20:58:37
by an unrelated COLD meal-progress exception: a private-only candidate route was
classified as illegal invariant by PedestrianLocalDeparture.publicContinuation.
Full log retained /tmp/pm-group-combat-before-deploy.log and old world retained.
The shared query now returns Optional.empty for no public continuation. Selection
skips such candidates (including start==destination), while accepted receipt
validation still rejects an illegal private-only route. Known missing routes
retain the existing typed unavailable/wait path. Relevant existing route tests
plus the private-only negative case PASS (/tmp/pm-group-combat-private-route.log).
This does not repair the separately retained individual-storage adoption issue.

Live preflight preparation used lowercase task identity pmcombatgroup with temporary
operator, two ordinary donations to an adopted secondary chest,20FPS/full-pack/
privateXvfb. Equipment diagnostic2482376a-cace-41e9-a3c2-92248c5ae62c passed9actions
with NoraBell1-16/EliStone1-22actually armed. This is donated fixture equipment,
not natural production. No human rd state is edited.

First battle diagnostic formed2participants, then exposed a recording defect:
new generic body annotation requested CombatCapabilities.current for PRESENCE,
whose strict closed registry correctly rejects unsupported combat authority.
Capture0167b44d-8ca7-4bac-8388-a30bd2e693c9 ended CAPTURE_ERROR/incomplete; it is
not combat evidence. Correctiond96b66330 adds explicit read-only supports discovery
for bystander recording, retaining require/current failure for unsupported
execution on authoritative paths. Same candidate also moves the checked-in
ordinary walking circuit out of obstructed town buildings; the first circuit
traversed only1.46blocks and failed its unchanged16block semantic threshold.
No deadline/threshold relaxation or fabricated motion.

Correction guardrails/compile PASS9s; detachedd96b66330 gates/JAR/package PASS9s.
Native saved world/JVM+ports exit before republish/install/restart. Final runtime
wrapper2684821,start1793810804, same331world/seed139; deployment verify PASS.
SHA512c512a5c76fefd8671fd6707ac6503ddccdd8a145ee6c51e72865cbb56fc2170844654030d0aeca87a63add7d85440063e39fb6777ebaeb31db153611917198bb.
Receipts /tmp/pm-group-combat-{trace-fix,release-bystanders,preflight-final,publish-final,
install-final,runtime-final}.log. Old330world/failed capture preserved. Final live
capture1e35ef3c-90f5-4582-bea1-93e78ae4f1ff retained34198events over180seconds with
complete footer/no loss. Both declared battle scenario invocations FAILED METHOD:
scripted task-player movement hit town buildings (second walk stopped at
-1494,64,-1361); the circling/graphical obligations were not completed and no
requested frames were captured. The strict checked-in obligations and failed
receipts are retained, without lowering the unchanged16block travel threshold.

The final recording nevertheless supports narrower real physical group results:
1368sampled frames have both guards within12blocks of the task player; in524the
angle between their observed positions around that player is >=60degrees, peaking
at125.63degrees. Nora records94and Eli34actual damage-applied hits (376and136damage)
on the1000HP diagnostic player. Stable roles include pressure plus right flank.
This is actual positioning/hit evidence, not merely role labels, but does not
establish effectiveness against active human circling. Summary and source receipts
are under IMPL/build/combat-coordination-20261104/observed-group-summary.json.

Stopped the blocked task client, removed its temporary operator and verified no
client/private display remains. Final settlement1@16948statusok/NORMAL/0activeCases,
2ready guards and0contacts/responders/maneuvers. Final runtime health verify PASS
(/tmp/pm-group-combat-final-health.log), source/release clean; pack has only the
preexisting untracked.f0v-baseline/. No automatic push. Manual difficulty and
active-kiting acceptance remain for the user's playtest.
