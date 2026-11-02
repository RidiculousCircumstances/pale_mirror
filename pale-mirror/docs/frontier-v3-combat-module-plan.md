# Reusable HOT melee module

Accepted 2026-11-02. Implement and integrate with guards in one pass; no COLD
combat, civilian combat, ranged weapons, convoy policy or distant reinforcement.

- Generic combat values and one CombatState own pending strikes, readiness,
  observed weapon profiles and bounded rejected attack stations. Security keeps
  hostility, sightings, response allocation and patrol/search bookmarks.
- A closed execution-kind capability registry is the composition root. A guard
  adapter supplies an exact current CombatOrder, validates retained ownership,
  nominates its navigation knowledge and supplies family continuation/target-end
  feedback. Generic combat code imports no GuardDuty, settlement or security policy.
- CombatOrder is a read-only capability view of the current role order, not a
  second persisted target or actor controller. It names exact execution, owner
  revision, weapon, dated target, permitted envelope and current approach station.
- Shared tactical planning chooses a legal reachable melee station using existing
  geometry/navigation, weapon reach, peer stations and native blocked-position
  feedback. It retains a valid station and bounds alternative search; no A* per
  poll and no synthetic arrival or relocation. Guard movement remains ordinary UAE.
- Generic native melee executor observes actual weapon properties, LOS/reach and
  failed attack stations, then uses the existing exact operation journal/hit/wear
  protocol. Sensors and political target admission remain security-owned.
- Common Prepared/Completed/weapon/station facts use a dedicated registered
  process/codec. Shared inventory fence, capability checkpoints, retirement,
  death and restart refer only to the sole CombatState. Fresh schema319, no migration.

Trace the connected lifecycle before focused gates: command, movement checkpoint,
position selection/reselection, native hit, exact recovery/wear, target loss/end,
owner pause/death/retirement. Relevant negative/recovery and a generic non-guard
capability fixture establish reuse without enabling another production role.
Use one finite existing client carrier, known supported setup/exit and actual
player hit/health, restart. Preserve earlier failed receipts and avoid repeating
unrelated full matrices. Finish scoped commit, clean detached package, MAIN deployment
and current runtime binding. No push, subagents or API goal.

## Integration API

`CombatCapability` supplies read-only current orders and validates exact retained
pending effects independently of contact expiry. It declares interruptible approach
legs and family-owned wake/terminal facts. Missing/duplicate registrations fail
closed. First production registration is GuardCombatCapability. Orders do not grant
body, route, inventory title or another execution generation. Generic admission
independently requires actual actor equipment custody and usable condition.

`MeleePositions.rank/reachable` are pure ranking/query APIs. Runtime selection uses
existing KnownPedestrianRouteKnowledge and keeps a valid station. The bounded budget
counts outstanding route dependencies, not completed negative answers; otherwise
an accessible ninth candidate could be hidden forever by the first eight failures.
Actual missing LOS/reach at the selected station publishes exact bounded rejection
feedback. `MeleeTactics` selects attack/approach/wait/end; the native executor alone
applies the admitted effect through the existing operation journal.

Registry weapon definitions permit COLD approaches without requiring materialization.
They never grant native hit authority; actual HOT stack properties, custody, body,
LOS, hitbox reach, readiness and opponent incarnation are checked for every attack.
`CombatStatus` explains retained waits without geometry or route calculations.

## Pre-native checkpoint

Connected guard/core/codec/reference/custody/retirement cut passes guardrails and
72 focused Frontier tests, including the role-independent mechanics, bounded query
completeness, COLD-definition/no-hit boundary, stale owner/wear/snapshot cases and
architecture dependency rule. NeoForge48 focused confirmation/parser tests pass;
physical-executor registry tests pass separately. Node scenario tests56/56 pass.
Logs/receipts: build/combat-module/focused.json, executor-registry.json and
/tmp/pm-combat-final-focused.log. Compiled native semantic-station feedback reuses
FrontierV3SemanticMovement.at rather than defining another feet/arrival rule.
Native outcomes and final delivery belong in canonical governance after execution.
