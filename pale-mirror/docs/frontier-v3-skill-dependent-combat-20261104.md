# Readable, skill-dependent guard combat — 2026-11-04

## Authorized scope

Five agreed improvements: distinct readable attacks, punishable commitment and
miss recovery, observed defensive response, bounded group pressure, and shared
readiness costs. Player speed/agility must be accounted for. No new combat role,
COLD hit resolver, new movement owner, automatic difficulty scaling or raw damage
increase. Human playtest determines enjoyment/balance, not an isolated test.

## Implementation and ownership

- Shared MeleeAttack/Techniques: QUICK at close range, LUNGE against separation,
  SWEEP against observed close lateral movement. Native sampling retains only a
  bounded previous sighting; no future player input. Native weapon reach remains
  authoritative; attack approach allowance never becomes damage range.
- Presentation sends explicit stable gesture tags and technique-specific cues.
  Lunge tracks early at bounded angular speed, then freezes before contact.
  Its cone is narrower than the stationary sweep. Successful dodge allows14ticks
  recovery before the separately telegraphed six-tick FOLLOWUP; continuation is
  still exact and one-shot.
- Visible, directed native swings can invite BACKSTEP. Ordinary geometry/UAE
  admission must prove a safe destination. It grants no invulnerability; original
  hurt, shared and individual cooldowns remain.
- Existing retained encounter roles own pressure/flank/cutoff. Single-use waves
  allow at most two live pending attacks, including across consecutive waves.
  An interposed physical body blocks the actual contact segment.
- CombatState owns one effort account per retained combat owner. Attacks and
  maneuvers spend only at canonical admission. Native refusals/cancellation do
  not refund committed effort. Recovery is lazy in canonical ticks, persisted,
  and survives retarget/body handoff. Duty retirement removes its bounded account.
  Exhausted heavy attacks may fall back to an affordable close QUICK; otherwise
  wait/ordinary movement. No per-tick refill events or second native clock.
- Existing short WALK/CAREFUL travel and bounded RUN/BURST remain; no permanent
  sprint. Need actual fast retreat/circling check, not stationary target alone.

## Verification plan

Focused model cases cover effort across actions/recovery, exact codec/restart,
wave overlap, sweep/retreat selection and committed cones. Native strike slice
checks real contact, windup, expiry, interposed body, directed visible swing and
sweep dodge. Existing client animation tests cover distinct warnings/retraction.
Then exact detached JAR, fresh schema328 test world and a bounded full-pack
ordinary-client combat probe with reviewed frames. No native acceptance claim
until actual probe results. Record limitations and source/runtime identities.

## Evidence

Implementation644c08fb4; protocol fence25597c736; local-only attack counting
d1b5fa885e8650276ed6e7fead4265203ae68f8f. Source/RELEASE clean. Protocol3 refuses
old clients before play; update client is required. State328 deliberately rejects
prior disposable schemas; first deploy selects a fresh world and leaves the old
world intact. The local counting successor iterates at most64 encounter members,
stopping at the two-strike cap, rather than all world pending attacks.

- /tmp/pm-skill-combat-check.log: guardrails,19model cases,5animation tests and
  all16native strike cases PASS. The native slice includes journal single-hit and
  failure/recovery cases, visible-swing direction, interposed bodies, and a sweep
  dodge. Test-injected storage interruption/quarantine is expected and asserted,
  not a MAIN failure. Earlier attempts failed only source formatting.
- /tmp/pm-skill-combat-final.log: guardrails,19CombatModule+11GuardDuty+4persistence
  tests,5animation tests, JAR/package PASS38s. Prior-schema rejection is retained.
- /tmp/pm-skill-local-admission.log:19model cases PASS after local-count change.
- /tmp/pm-skill-release-final.log: clean detached guardrails/JAR/package PASS14s.
- MAIN skill-combat-schema328-seed139-20261104, runtime
  /home/rd/far-frontier-server. FinalactualJava22PID3497141, wrapper3497095,epoch1793774074 (prior deployment; see final successor below).
  /tmp/pm-skill-final-{preflight,publish,stop,install,verify}.log retain root gates.
  SHA5125df8d210d49ab3bf27770a10a749c6fca5485284edc7b3989ced3f4789c55ab64721940f99be0e761d0dbb7a25e03567427598cda44daf3331d37ef4e0a07660.

Client evidence remains pending. First checked-in live-combat-maneuvers attempt
stopped at an unarmed-fixture precondition (new world had no produced swords).
The task pilot was explicitly disconnected and exited; no combat was tested and
no failure is attributed to combat. For the discriminating combat probe, a
separate ordinary-client setup deposits two player iron swords through the real
container UI. This setup is not proof of natural economic sword production.
Raw receipts in IMPL/build/skill-combat-20261104/. No human inventory/mode changed.


Final effort-boundary correction f758331ed keeps physical contact independent of
readiness: exhaustion yields WAIT rather than marking the current valid melee
station blocked. Focused CombatModule cases/native compile PASS in
/tmp/pm-skill-fatigue-facts.log. Final delivery8c3f036a1d34a865654871554281fe0d0b5d8f6c
includes this fix and repeat-safe probe assertions. It retains full prior native
contact/window tests unchanged. /tmp/pm-skill-delivery-build.log PASS14s;
root receipts /tmp/pm-skill-delivery-*.log. ActualJava22PID3531825,epoch1793774565.
SHA5125c834d3dfb5893d4c217dc8756e2a1d28dd1e687e10ad21e8b7587577b7d7e43ef22f531667f232b09380df312fc07d467778789bb68f5b06bb64fc0c311c635.
## Final native-client result and limits

Equipment setup24a64487-94c9-45a4-a922-602bea7266da passed ordinary two-sword UI
deposit and armed-guard observation. Both swords are player contributions; no
natural forge-production claim.

Probe3143659e-5fb9-4117-9f5b-807a1c36d84d on earlierd1b5fa885 observed native
health1000→957.3334 and100+240+240 motion samples against sprint/speed1 circuits;
no NPC pursuit-stall oracle failure. Actual rendered samples include changing
QUICK/LUNGE/BACKSTEP. Both frames personally reviewed: armed human rig with
connected body, but endpoint images do not prove all warning keyframes. Its
escape was invalid: old scenario expectedY66, actualground64. It was explicitly
disconnected, not passed. Repeat probe hit an obsolete SELF_DEFENCE-only transient
assertion: prior hostility legitimately allowed AREA_DEFENCE. Scope of those
receipts remains partial, not a full native acceptance.

Final probe9ac56674-0484-4113-a1b4-9376f694b1a3 onexact8c3f036a1 confirmed player
health996, then failed its player-motion precondition atstep8: actual circuit
length14.307<16. This was not the NPC pursuit-stall check. The player was blocked
by existing built geometry; this receipt cannot prove full-speed circling in that
layout. Its final frame was personally viewed and shows road/population with a
cropped foreground actor, not a complete attack animation. Do not replace this
failure with an all-green/full-HOT claim or weaken the movement criterion.
Test-only successorcfd6eef9f explicitly fences look/visit/attack to resident1-16,
preventing repeated presentation names from choosing another subject. Scenario
validation passed; no additional native result is claimed for that selector edit.
No new SWEEP/FOLLOWUP render samples were observed in the live probes. Their
pose/cue/contact rules passed focused client-model/native tests; actual human
readability and enjoyment still need player judgement.

All task clients/privateXvfb exited. FinalCOLDsettlement1at21488:twoarmedguards,
zeroresponders/contacts/openmaneuvers,23nourished/0hungry,hostility memory1 retained.
World21492 has zero required/scene/inventory conflicts. Native server remains
running, actualJava22PID3531825,wrapper3531800; finalrootverifyPASS. No new MAIN
quarantine. Diagnostic lifetime hostmax287ms mapped actor_movement_cold_advanced
transaction179ms/reduction142ms, not combat-attributed. No sub80ms guarantee or
scale performance acceptance is claimed. Implementation/commits/verified deployment
are delivered; a full graphical combat promotion and battle balance remain unproved.
