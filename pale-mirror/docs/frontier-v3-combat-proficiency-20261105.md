# Reusable combat proficiency and learned repertoire — 2026-11-05

## Accepted behavior

An actor owns permanent strength, agility, endurance, perception and toughness,
plus explicit weapon/evasion/weapon-defence skill levels0..100. Personal style
remains an independent preference. Genesis and birth assign stable variation;
loading, training for civilian work, role change and relocation preserve it.
There is no combat XP process or new hunger/injury/load/fatigue modifier system.
Existing finite effort/cooldowns remain action execution constraints. Parry and
COLD damage are deliberately outside this cut.

Sword levels0/10/20/30/40/50/60/75/90 add basic and overhead cuts, quick/side cuts,
lunge/kick, rising/advancing strike, lateral/retreating cuts, double/counter,
circle/flank, feint/lunge-chain, evade-counter/directional chain.23stable attack
identities include the finite continuation stages. High levels retain basic
attacks. Skills admit techniques; attributes calibrate bounded timing, damage,
impulse and cost once; style ranks valid opportunities. Observed target motion,
swings and missed directed swings supply facts, never future player input.

Evasion10/25/40/55 adds retreat/side/diagonal/advancing side steps;70 allows exact
checkpointed evade-to-contact transitions;85 permits earlier subsequent defensive
response under the retained shared cooldown. Weapon defence10 admits a finite
stationary block with skill-dependent partial reduction, actual weapon, current
body/permission and committed facing. No random invulnerability or parry.
Toughness supplies bounded native knockback resilience, not health duplication.

## Ownership

CombatProfile/CombatAttributes/CombatSkillId have no resident/species dependency.
An actor producer owns their retained values; GuardCombatCapability is the current
adapter and explicitly supplies profile, weapon skill, supported techniques,
a learned fallback and permitted area targets. A high skill alone does not grant
a humanoid technique to a different species; unsupported moves fail admission.
Other actor families implement the same registered capability. Common combat
never queries population, resident role, name or settlement politics.

CombatTrainingCatalog is bounded immutable resource data included in the world
rules hash; parsed numeric policy and ordered definitions are cached. It rejects
missing techniques, duplicate properties and cyclic/incompatible continuations.
CombatMechanics is the sole calibration point. CombatTechniqueSelection owns
learned/contextual choice. Common CombatState/engagement/maneuver/UAE remain the
owners of clocks, tactical commitments and movement. Shared MeleeFootwork supplies
bounded sector-respecting goals over supplied known supported3Dsurfaces,
including steps, to existing LocalGoalCorrection/navigation. Missing/blocked
geometry produces a wait/refusal, never a made-up constant-height destination.
There is no second body controller or movement driver.

Each exact MeleeStrike snapshots its producer profile and weapon skill; canonical
admission rejects a foreign profile or unlearned technique. A continuation retains
one exact next technique/body/weapon/target incarnation and expires; the second
stage cannot reopen a chain. Actual reach, LOS, arc, native defence, wear and
cancellation/no-replay remain binding. A kick has shorter real reach and no sword
wear. Circular attack captures at most4additional explicitly permitted victims;
all physical participants enter the durable operation declaration before effect.
Revoked secondary permission is checked again at impact. The native operation
protocol accounts all participants and prevents replay of the combined effect.

Rendering owns distinct procedural cues/poses, including kick leg, rising cut,
wide circle, reversed chain cuts, feint and block. HumanEra/fallback rigs receive
semantic joint poses. Rendering cannot decide damage. The10line resident card,
actor inspection and opt-in combat trace show preparation and attributes.

## Verification and delivery

Implementation3267f8b3c plus correction6c4b81ee8; final source62835a3b0 includes
faithful pilot input and the checked-in live-combat-proficiency.json fixture.
Schema335/RBO5 rejects older disposable worlds; network5 REQUIRES CLIENT UPDATE.
The correction retains bounded witnessed swing evidence after its animation ends
and allows delayed defensive reaction only with actual recent harm. Restricted
non-resident repertoires are checked at selection, canonical admission and native
permission. Known stepped footwork cannot invent an unsupported destination.

Relevant integration /tmp/pm-proficiency-integration2.log PASS121frontier checks
plus7animation checks. Final focused capability/closed-repertoire/footwork checks
PASS: /tmp/pm-proficiency-final-capability.log,
/tmp/pm-proficiency-closed-repertoire.log,
/tmp/pm-proficiency-final-footwork2.log. They cover codecs/profile retention,
independent unlocks/stat scaling, finite chains, non-resident restricted moves,
forged admission, delayed observed reaction and stepped/blocked geometry.
Final clean detached guardrails/JAR/package:
/tmp/pm-proficiency-pinned-release.log PASS9s. No affected red remains.

Native /tmp/pm-proficiency-native2.log:25required scene-strikes PASS, including
rising/kick/circle durable effects and no replay, actual multi-target circle with
revoked victim untouched, retained reach/cancellation/body replacement. This run
preceded the observation/repertoire correction; unchanged effect dependencies
are reused, not claimed as a final-ref native rerun. Initial native failures were
fixture operation-ID collisions and an obsolete fixed6damage expectation; neither
production permission nor durability was weakened. Old exact-QUICK selection
assertions now check semantic contact/continuation obligations. A level100evader
style assertion retains its cooldown while accepting the new contact handoff.

## Ordinary full-pack evidence

Equipment4aba294f-103d-45b9-9514-518b1af0e7bd PASS9: ordinary chest donation and
actual sword equipment. Fixture uses PMTestPilot2; supplementary ordinary operator/
creative/give setup repaired an initially mistaken pilot name. This proves
controlled equipment, not natural sword production. PMTestPilot2 was deopped.

Battle6d879927-3b94-4dc2-8abc-87d2031fe7f7 PASS28, manifest under
IMPL/build/combat-proficiency-20261105/near-post-battle-manifest.json. Native trace
MAIN/debug/pale-mirror/combat/combat-fe60518c-ad17-4ca5-a4c4-9af941189a9b.jsonl
closed14617/14617complete, no data loss. Eli8+Nora6hits versus5taskpunches.
Both different profiles survived combat and ordinary dimension departure unchanged;
responders, contacts and maneuvers retired0. Actual card/human rig frames reviewed.

Carrier correction: an ordinary attack also sends a swing; the old pilot omitted
that packet, so defensive observation was not exercised faithfully. Source57fb2a39b
adds it. Final scenario also rendezvous with the current patrol, does not teleport
away after harm, and uses ordinary sprinting/circling plus a short3punch sequence.
 Earlier fixture probes used a blocked feet cell, out-of-contact teleport or a
patrol beyond the received local range. The fixture was corrected, not the product
leash/timeouts. These probes are not acceptance evidence.

Witnessed-swing battle e4ec1f16 PASS28, but its optional flight recorder explicitly
failed the16capture retention bound; do not infer native-event coverage from that
run. Only2completed task-owned failed probes were moved into the existing trace
subdirectory proficiency-probes-20261105; no human capture was deleted.

Final defence battle e81704f8-7963-454a-aab3-5950edada053 PASS28:
IMPL/build/combat-proficiency-20261105/defence-battle-manifest.json, exact scenario
SHA256 c2cd5169d56d4d154a2cb7f677bf54b27c6c45976a52a7c1c38c067b488609eb.
Native combat-fe73901c-542f-4c41-9eeb-f1d7efa5979c.jsonl closed5893/5893complete,
no loss;24actual swinging-player samples. Nora26+Eli5damage events versus7player
hits. Actual diagonal evasion and BLOCK were selected; block launched34841 and
closed34853 with native cue. No incoming hit coincided with its active window:
this live run does not prove the partial-damage reduction. Profiles remained
unchanged after departure; final contacts/responders/maneuvers0. Linked actual
frames show correct attached human limbs/sword and distinct strike pose. Not all
23animations or high-skill chains were graphically sampled; no HUMAN_CANDIDATE,
M3 or manual combat-balance acceptance is claimed.

## Installed build and recovery

MAIN /home/rd/far-frontier-server, far-frontier-v3-live.service active;
world combat-proficiency-schema335-seed139-20261105 seed139/rootr31.
Final source/release62835a3b0 clean detached, start1793824916,
wrapper437944/nativeJava22PID437969. Installed SHA512:

```
e8f118b1bca763e273ae5ea9901222f1c2dd934cb8e7c6acef16c92038131c7e6d64290138d0a5feb6378fefec489753e01013f5118eeb8114a2b855201f74d6
```

/tmp/pm-proficiency-pinned-{preflight,publish,install,verify}.log PASS. Graceful
save and nativeJVM/port exit preceded each install. Final upgrade preserved335world
and equipped swords; earlier334and diagnostic worlds retained. Read-only actor
inspections after restart match both exact pre-restart combat profiles. Settlement
NORMAL/0cases/0responders/0contacts. Fresh startup has no new gameplay exception.
All task clients/private displays exited (20FPS while active), both task operator
grants removed. No human rd state changed. IMPL and RELEASE clean. PACK tracked
unchanged888ed90, preexisting.f0v-baseline/ retained. GOV unrelated WIP preserved;
only this report/plan/ledger/archive/exact owned boundary are committed. No push.
