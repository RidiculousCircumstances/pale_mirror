# Visible personal combat styles

Implemented production commit `f70feb0ec`; checked-in longer native observation
scenario follow-up `60ebe26a0`. Sole executor; no push or subagents.

ResidentProfile owns an explicit CombatStyleId assigned deterministically at
birth/genesis and retained through role, capability, characteristic and settlement
changes. Both state and birth codecs carry it (schema332/RBO4). Unknown definitions
fail before constructing a valid resident. The immutable bounded properties catalog
is loaded once and included in the existing FrontierRuleset fingerprint. This
version intentionally requires a fresh world, without inferred compatibility.

The role adapter resolves the definition into common CombatOrder. Generic melee
never discovers style from human names, profession, job or owner prefixes. The
existing encounter owner still assigns PRESSURE/FLANK/SUPPORT independently.
Style influences feasible action preference, effort reserve and desired station
distance. Shared preparation, committed recovery, exact body/weapon/target,
permission, contact, team pressure, navigation and physical damage remain hard
constraints. No style grants extra damage, weapon reach, invulnerability, movement
speed or a shorter cooldown. Expensive decisions do not load resources per tick.

Three shipped definitions deliberately differ:

| Style | Behavior |
| --- | --- |
| Pressing | Closer preferred station, frequent runs/bursts, little optional retreat, no proactive decorative sidestep. |
| Cautious | Wider reachable station, larger effort reserve, more backsteps under an observed threat and less expensive pursuit when tired. |
| Mobile | Strong proactive lateral preference, moderate reserve and runs, with the existing group-side and navigation constraints. |

Weighted legal choices use actor/style and retained accepted-action clocks, not
fresh randomness per evaluation. Inspecting or retrying an unchanged choice cannot
reroll it. A real committed action advances the existing canonical lifecycle.
The native executor consumes APPROACH explicitly instead of accidentally replacing
a style-selected walk with an attack. MeleePositions and holding both consume the
same profile distance; the obsolete uniform holding-gap API/field was removed.

The normal resident card displays Combat style; actor diagnostics and combat trace
include its exact ID. New definitions/weights are data changes under the hashed
rules boundary. No additional admin canonical editor or renderer protocol was added.

## Verification and deployment

`/tmp/pm-combat-styles-final.log`: guardrails,99 focused frontier checks and8 focused
NeoForge checks, jar and packaged-jar verification PASS32s. The frontier selection
includes CombatStyles/CombatModule, security, population birth, role assignment,
identity and current persistence. Tests compare the same facts across styles,
low-effort behavior, stable retries, real station ranking/short-weapon cap,
committed cooldown, reference preservation and unknown/invalid catalog rejection.
Existing native scene-strikes19required PASS42.96s in
`/tmp/pm-combat-styles-verify.log`; retained for unchanged strike/animation/replay
boundaries. Later retirement of unused uniform holding API has final focused gates.

Clean detached release f70feb0ec: guardrails/jar/package PASS11s.
SHA512:
`213de1905741b48b3f3a466dd9b8a40819ad555ecf1acdcbb1cf809f69a7c6e9fe50c3447c6123e626aa356e8896d3f73cdce5723bc7ecaa222be3f32122a77c`.
Published/installed through pack scripts; final preflight and deploy-verify PASS.
Runtime `/home/rd/far-frontier-server`, service `far-frontier-v3-live.service`,
wrapper3141107/native JVM3141150, start1793814020, seed139/rootr31,
world `combat-styles-schema332-seed139-20261104`. The earlier331diagnosis world and
its identified traces remain. One premature startup before installation completed
loaded old classes; it was normally saved/stopped, only that empty selected disposable
world was recreated, and final startup used the verified332artifact. Receipt:
`/tmp/pm-combat-styles-rejected-early-start.log`. No human player joined/was edited.

## Actual graphical full-pack evidence

Single task client pmstyletest at20FPS/private Xvfb, current source composition;
all required mods loaded. Ordinary player donated two swords into an already
adopted secondary chest. Setup run ec885da6-8e77-4995-9de6-d0afbde367cd PASS9actions;
this is diagnostic equipment donation, not natural sword production acceptance.

Initial combat run a2469308-91dc-4b47-8473-8287dd493e46 PASS14actions, actual card,
damage and target-loss retirement. Capture39b80d04-78cf-4f49-9096-7021edfbb17b complete
3141events/no loss. Its short15second window did not let the distant second guard
reach contact, so it cannot compare their physical action mixes.

The checked-in scenario now observes600ticks after the second ordinary punch,
retaining finite60s watchdog and all assertions. Run
fe26ab2c-70a1-41e6-9db7-59f6f24b7704 PASS14actions on that exact deployed production
artifact. Capture35308e9f-fd5b-4bcf-974d-4d298117a819 complete7746events/no loss.
Nora Bell resident:1-16/Cautious made an actual BACKSTEP gesture,3QUICK and2BURST
presentation starts;2confirmed damage hits. Eli Stone resident:1-22/Pressing made
7BURST and6QUICK presentation starts;5confirmed damage hits, no BACKSTEP gesture.
Both reached actual contact. These unequal observations establish executed distinct
maneuvers, not a controlled statistical ranking: starting positions/team duties differ.
Same-fact tests provide the controlled comparison. Mobile is covered by those common
policy tests, not this particular two-guard physical sample.

Reviewed both actual frames: readable Cautious card, and two upright equipped
physical guards during the fight with populated terrain. Diagnostic1000HP task
player's heart rows obscure part of the first frame; the style text remains readable.
Frames/manifest and compact observed-pair-summary are under IMPL/build/combat-styles-20261104
and build/frontier-v3-scenarios/live_combat_styles-fe26ab2c-*.png.
After ordinary player departure:0contacts/responders/maneuvers; personal style
references retained; both guards resume ordinary duty/return. Final settlement
instant7250statusok/NORMAL/0activeCases/2ready. No new gameplay exception/quarantine;
connection reset log entries correspond to runner client shutdown. All task client
and private-display processes exited; task op removed;0players. No rd mutations.

This delivers personal tactical profiles and graphical lifecycle acceptance.
Difficulty/readability against an actively circling human still needs manual
judgement; stationary scripted punches do not establish that balance. Old separate
storage-adoption diagnosis remains open and was not claimed fixed here.
