# Combat proficiency, attributes and techniques — implementation plan

Accepted scope: reusable actor combat profile, sword/evade/weapon-defence levels
0..100; permanent strength/agility/endurance/perception/toughness; existing
personal combat styles. Genesis/birth assigns retained variation. No XP/training,
injury/hunger/load/fatigue modifiers, parry, COLD damage or player-stat rewrite.
Current finite effort and cooldown mechanics remain execution limits.

## Delivery sequence

1. Pure common CombatProfile/CombatAttributes/CombatSkills and hashed authored
   generation/technique policy; resident owns one explicit profile. Both codecs,
   genesis/birth and mutators retain it. Role capability supplies it to common
   CombatOrder; common combat never reads population or guard records.
2. Data-authored sword unlocks at0/10/20/30/40/50/60/75/90: basic cuts, quick/side
   cut, lunge/kick, rising/advancing strike, lateral/retreating cuts, double and
   counter, circle/flank, feint/lunge continuation, evade counter/directional
   continuation. Retained exact continuation is finite and cannot loop/replay.
3. Independent defence unlocks: retreat10, sidestep25, diagonal40, advancing
   lateral55, attack transition70, chained response85; weapon block separate.
   Common policy gates actions, attributes calibrate bounded actual timing/pace,
   native damage uses exact real contact and ordinary defence, never dodge RNG.
4. Native effects/presentation: distinct cues/keyframes incl kick/rising/circle/
   block, actual common navigator footwork, bounded area participants and exact
   effect recovery. No family-private actuator or omniscient target choice.
5. Read-only resident card/inspect/trace: profile, unlocked techniques, selected
   action. Focused unlock/physical scaling/foreign actor/codec/no-replay coverage,
   relevant native strike/defence checks and full-pack ordinary battle scenario.
6. Review, scoped commit, clean detached release, pinned publish/install/new
   disposable world, runtime verification and graphical check. Record truthful
   limits and stop task clients. No automatic push.

## Boundaries

Persistent actor producer owns identity/profile. Shared catalogs own requirements
and immutable definitions; shared combat owns choice, clocks and exact attempts.
Role adapters own target/party permission. UAE/navigation owns locomotion. Native
operation protocol owns effect durability and observations. Renderer owns visuals
only. A different mob supplies the same typed profile/capability; no resident/name/
role inference in generic algorithms. Unsupported data fails before effects.

Delivered in62835a3b0; exact checks, installed build and evidence limits are in
[the delivery report](frontier-v3-combat-proficiency-20261105.md).
