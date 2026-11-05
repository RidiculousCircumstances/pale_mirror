# Active continuity — 2026-11-05

## Assignment and authority

Accepted combat proficiency implementation delivered, committed and deployed.
User additionally required universality for other mobs. Sole main, no agents,
no goal or push requested. Standing authority for reviewed commit + verified
deploy; disposable worlds allowed. Never mutate human rd state. Preserve
identified diagnostic worlds/traces, unrelated WIP and services.

Task plan/report:
- docs/frontier-v3-combat-proficiency-plan-20261105.md
- docs/frontier-v3-combat-proficiency-20261105.md

Protocol/release/server-ops skills + operations map read. Applied pm-engine-source
for NeoForge incoming damage API and pm-client-investigation for full-pack pilot.
Old long ledger archived at docs/archive/continuity-20261105-before-combat-proficiency.md.
No further implementation or runtime task is pending in this cut; human combat
balance and unsampled graphical repertoire remain for actual playtesting.

## Implementation boundaries

Schema335/RBO5, network5: CLIENT UPDATE REQUIRED. Persistent actor CombatProfile
owns strength/agility/endurance/perception/toughness and explicit skills0..100.
Genesis/birth assigns variation; codecs and all mutators preserve it. No XP,
training, new temporary hunger/injury/load/fatigue modifiers, parry or COLD damage.
Existing effort/cooldowns remain finite execution constraints.

A registered CombatCapability supplies exact profile, weapon skill, permitted
technique set, learned fallback and area-target permission. No resident/species/
role inference in common combat. A foreign mob cannot gain unsupported humanoid
moves merely through skill. Hashed bounded cached catalog owns23stable attacks
and finite noncyclic stages; sword ladder0/10/20/30/40/50/60/75/90. Independent
evasion10/25/40/55/70/85; weapon block10. CombatMechanics alone calibrates stats.
Styles rank valid opportunities independently, granting no stat bonus.

Known supported3Dfootwork uses sole UAE/navigation, never constantY invention.
Exact strikes retain actor profile and every bounded permitted area victim before
native intent. Existing reach/LOS/arc, current body/weapon/target permission,
wear and no-replay apply. Block is finite active cue + facing + usable real weapon,
partial melee reduction, no invulnerability. Toughness scales native knockback,
never removes it. Distinct render gestures include kick leg/rising/circle/feint;
HumanEra/fallback rig, compact card and read-only diagnostics/trace wired.

## Source and checks

IMPL /home/rd/proj/pm-f06r3-facility-lane-recovery/pale-mirror (Gitroot parent),
branch feat/baker-carry-orders-20260926 clean62835a3b0. Implementation3267f8b3c,
correction6c4b81ee8; later commits own faithful ordinary pilot swing and moving
patrol fixture. RELEASE /home/rd/proj/pm-r242-economic-release/pale-mirror clean
 detached62835a3b0. PACK /home/rd/proj/minecraft trackedclean888ed90 with existing
.f0v-baseline/. GOV /home/rd/proj/pm-governance retains extensive unrelated WIP;
only task ledger/plan/report/archive + exact owned combat_proficiency_boundary
line are staged/committed. Do not stage whole working architecture.yml.

Relevant integration /tmp/pm-proficiency-integration2.log PASS121frontier +7animation.
Native /tmp/pm-proficiency-native2.log PASS25 including actual multi-target circle,
revoked target untouched and no replay. Test-ID collisions/obsolete exact damage
and attack-type expectations fixed without relaxing production fences. Native25
precedes final observation/repertoire correction; unchanged hit/effect dependencies
reused, no final-ref native rerun claimed. Final focused capability, restricted
non-resident repertoire/admission, delayed observed reaction, profile/codec and
stepped/blocked footwork checks PASS (see report/log links). Clean detached
/tmp/pm-proficiency-pinned-release.log guardrails/JAR/package PASS9s.
No affected red remains; no unrelated broad campaign.

## Live evidence and installed runtime

Equipment4aba294f PASS9: ordinary2sword donation/equipment. Supplementary normal
op/creative/give setup corrected an initially wrong pilot name; this is controlled
equipment evidence, not natural sword-production acceptance.

Battle6d879927 PASS28; native fe60518c closed14617/14617complete/no loss,14guard
hits vs5taskpunches. Profiles retained after ordinary dimension departure;
contacts/responders/maneuvers0. Actual card/human rig frames reviewed. The pilot
then gained the missing normal swing packet after attack. Witnessed-swing
e4ec1f16 PASS28 but optional recorder explicitly failed16capture retention;
no native-event claim for that run. Two completed task-owned failed probes were
archived under MAIN/debug/pale-mirror/combat/proficiency-probes-20261105; no human
capture deleted. Earlier fixture probes failed on blocked feet or actual moving
patrol range; corrected fixture, not product leash or timeouts.

Final e81704f8 PASS28, manifest IMPL/build/combat-proficiency-20261105/defence-battle-manifest.json. Native fe73901c closed5893/5893complete/no loss;24real player
swing samples,31guard damage events vs7taskhits. Actual diagonal evasion and BLOCK
cue/lifecycle34841..34853 observed. No incoming hit overlapped block active window:
partial damage reduction was not established by this live run. Profiles unchanged
through departure, terminal responders/contacts/maneuvers0. Actual final frames
reviewed human sword/limb attachment and strike pose; not all23animations/high
chains sampled. No HUMAN_CANDIDATE/M3/manual-balance claim.

MAIN /home/rd/far-frontier-server far-frontier-v3-live.service active,
world combat-proficiency-schema335-seed139-20261105 seed139/rootr31.
Final start1793824916, wrapper437944/nativeJava22PID437969. SHA512:
e8f118b1bca763e273ae5ea9901222f1c2dd934cb8e7c6acef16c92038131c7e6d64290138d0a5feb6378fefec489753e01013f5118eeb8114a2b855201f74d6.
/tmp/pm-proficiency-pinned-{preflight,publish,install,verify}.log PASS. Saved
 dimensions + old nativeJVM/port exit preceded install. Same335world/equipment
retained; earlier334and diagnostic worlds preserved. Read-only restart inspections
match both exact combat profiles; settlement NORMAL/0cases/0responders/0contacts,
no new gameplay exception. All task clients/private Xvfb exited (20FPS active);
PMTestPilot2 and pmcontacttest deopped. Human rd state unchanged. No push.

BuildJDK21 /home/rd/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2;
runtimeJava22 /home/rd/.local/share/far-frontier/java/temurin-22.0.2+9/bin/java.
