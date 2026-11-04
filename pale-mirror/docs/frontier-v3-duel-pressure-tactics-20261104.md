# Local duel tactics and repeated frontal pressure

## Accepted result

A melee-capable actor can change its approach after repeated received blows or
missed exchanges. This is common combat behavior, not guard policy. Settlement
security still owns threat/target/duty; encounter coordination owns group sectors.
The existing geometry, movement interruption, UAE and shared goal navigator own
reachability and locomotion. No second mover, future-player-input prediction,
knockback immunity, damage or physical reach buff is introduced.

## Implementation

- `MeleeExchange` is bounded canonical memory for exact execution/weapon/target.
  Owner-supplied dated harm observations are idempotent; misses come from exact
  completed strike outcomes. It retains consecutive evidence, expiry, consumed
  pressure/cancellation watermarks and a stable side, not a position history.
- `MeleeDuelPolicy` is a pure common arbiter over those facts and explicit role
  capabilities. New pressure can prefer a lateral maneuver ahead of an immediate
  ordinary strike. Weapon recovery permits footwork but not another weapon hit;
  pending/committed strikes retain exclusive effect ownership.
- Stable `SIDESTEP_LEFT/RIGHT` maneuver tags extend the existing maneuver lifecycle.
  Short candidate goals break the frontal line and preserve group-sector/height/
  body/engagement constraints. Known failed preferred side can consider the other;
  unknown route knowledge remains a refusal/wait. The selected fixed destination
  is not changed every observation. Ordinary contact/approach resumes afterwards.
- Hashed duel rules own the finite pressure window, thresholds, geometry preference,
  lateral distance/pace/preparation/travel/recovery and cooldown. Handled pressure
  cannot trigger another step without a new cause. Existing effort and cooldown
  retain their real cost even when a move is obstructed or interrupted.
- Native reversible preparation can withdraw only through the existing positive
  unused-operation cancellation. No receipt/preimage guess permits withdrawal
  after contact commitment. Creating the same unused declaration when absent
  retains cancellation authority durably before the exact CANCELLED confirmation;
  queued canonical writes and restart cannot restart the old attack.
- Existing BACKSTEP visual cue is reused; actual movement is lateral through the
  common navigator. Optional combat recording additionally exposes maneuver kind,
  selection/launch/closure, pressure evidence and refusals. No client protocol
  change is required.

Schema329 deliberately rejects older snapshots. This format change also removes
obsolete quick-distance/reach-ratio/retreat-selection hints; actual weapon reach
remains authoritative. A fresh disposable329 world is deployed; the old328 world and human trace
remain diagnostic evidence.

## Verification and delivery

Production commit69452a5ca implements the coherent change. Final production
successor ebb2d443a makes canonical launch use the same kind-specific preparation
as native presentation (lateral1tick, ordinary4ticks). No second timing rule.
Intermediate cb1aa3fce/f223d738a/fc0f7ab99 only repair/add checked-in pilot fixtures.

Guardrails,24CombatModule+11GuardDuty+4codec and targeted architecture/adapter/
animation/operation checks passed. Native strikes18required pass43.64seconds;
local navigation46required pass3.488seconds, including real low-roof refusal and
shared navigation to the reachable raised diagonal. Native withdrawal verifies
unused-operation cancellation/no damage/wear/replay, journal hydration and refusal
once actual contact opens. The non-flat fixture exposed an erroneous3D-distance
use for a horizontal tactical radius; fixed while retaining independent declared
step/drop bounds. Initial fixture owner collision was repaired with a unique
owner, not weaker product checks. Final one-line launch correction reuses these
unchanged native boundaries; fresh35combat/guard unit checks and guardrails pass.
Clean detached ebb2d443a JAR/package checks pass; focused tests reused from cache.
Logs /tmp/pm-combat-duel-{final-check,native-final,navigation-final,launch-check,
release,release-final}.log retain exact commands/results.

MAIN /home/rd/far-frontier-server runs duel-tactics-schema329-seed139-20261104,
seed139/rootr31. Delivery uses the root preflight/publish/install/verify scripts;
native all-dimensions-saved and JVM/ports exit precede both installations. The
second installation preserves the329 world. Final receipts:
/tmp/pm-combat-duel-correction-{preflight,publish,stop,install,final-preflight,
verify}.log. No human rd state edits; no client protocol update is necessary.

Fullpack20FPS/privateXvfb equipment setup9bdfb2c9-aabe-4ad8-b0aa-884992a93199
passes7ordinary actions, donating two diagnostic swords through real container
UI. This is not natural sword-production evidence. Nora Bell resident:1-16 is
armed; Leon Lake resident:1-4 was still equipping at that observation.

Real-player pressure probe66c72ce8-dceb-4d28-9ed2-62d5a3d4bbab passes17actions on
69452a5ca: two actual punches, close response,400ticks ordinary circling, native
player damage, ordinary dimension escape with no further damage and terminal
contact/responders/maneuvers released. Capturea91c5f8c-67b2-4ed3-a790-d27e9e207bec
is7249/7249complete/no loss. Nora delivers18hits/21visible attacks, with2range
misses/1window miss; one exact unused preparation is cancelled for tactical
evasion. Ten lateral requests are rejected by known destination constraints.
The fight was beside a production apron. A read-only frozen-snapshot diagnostic
finds all four ranked lateral candidates for a nearby sampled origin inside the
active bakery service region; those cannot become combat parking. No claim of
successful physical lateral motion follows from this first probe. Both actual
frames reviewed: real armed body and changing scene; no new lateral cue sampled.

An initial open-ground probe used a fixed settlement-centre destination despite
the guard's new remote patrol position; it failed because the actor left its64block
observation radius. This is an invalid fixture, not evidence of broken pursuit.
The corrected checked-in scenario derives an8block player offset from the actual
actor diagnostic and retains real punches/circling/escape/terminal assertions.
Only the test player travels; no actor/canonical state is forced.

Final fullpack probe1175a34c-d6ae-4580-b0b3-4e5c9e0bd127 PASS21actions on exact
ebb2d443a. Capture2c242f04-f66f-4499-81f4-6bf96d15612a is4898/4898complete,
no loss/cap. Nora's first SIDESTEP_RIGHT is selected at27189, launched27195,
closed27212; sampled horizontal displacement1.836blocks. Following LUNGE hits
at27232 (native1788). A second fresh miss-pressure response selects27399,
launches27405 and closes27425, displacement2.394blocks; following LUNGE hits
native1998. Closure can mean arrival or declared expiry; displacement plus renewed
native hit proves real maneuver and re-entry, not just a selected intent. Stable
side retained, pressure watermarks advance27167→27389. Two BACKSTEP render cues
are sampled for actual lateral motion;15hits/20visible weapon attempts give60
native guard damage. Four range misses/one window miss and one unused preparation
cancel retain their honest outcomes. No claim that every evasion avoids a hit.
Both rendered frames personally reviewed on open ground; frames alone cannot
prove attack timing or difficulty. Evidence in IMPL/build/combat-duel-20261104/
combat-open-{final-manifest,summary}.json. Client/private display stopped;
recording SAVED/inactive. Final statusok/NORMAL/activeCases0, contacts/responders/
maneuvers0. A Netty connection reset coincides with disposable client exit, not
a tick failure. Production world remains running for human check.

The offline analyzer initially crashed on ordinary sunlight damage/death with no
entity attacker. Diagnostic-only commitd83054940 accepts an absent source as
null, preserving environmental outcomes without crediting them to guards. The
same actual capture now analyzes completely and asserts null-source damage and
death. No runtime/JAR/protocol changed after ebb2d443a.

Human balance, visual readability and resistance to a skilled player's repeated
knockback remain manual playtest questions. The test pilot has survival mode and
ordinary movement but1000maxhealth so it can finish the diagnostic sequence;
this is not a difficulty or mortality test.
