# Ravents progression design

Ravents `3.0` is retained as a dormant future materialization adapter. Pale Mirror
is the sole authoritative progression and story director. Ravents' generated JSON
model is per-player, but it has no dependable settlement-context or PM-causality
hook; therefore the default profile configures no automatic raids or events.

| Tier | Durable trigger to validate | Event posture |
|---|---|---|
| 0 — Frontier | None | No automatic Ravents encounter is enabled. |
| 1 — Exploration / Nether | Future PM-authored milestone | No automatic Ravents encounter is currently enabled. |
| 2 — Industrial / Air Age | Future PM-authored milestone | No automatic Ravents encounter is currently enabled. |
| 3 — Endgame | Future PM-authored milestone | No Blood Moon provider is installed. |

## Disabled Tier 0 prototype

The former `Frontier First Contact` raid was removed after live play showed that
its generic `Wave N` boss bar and player-relative spawning looked like an unexplained
attack on a PM settlement. It bypassed regional defence, Narrator pacing and event
history, so `singleUse` was not sufficient to make it product-correct.

Ravents itself creates `division_zombie.json` and `division_skeleton.json` when they
are absent. They are committed unchanged so first boot is reproducible. They are
only unreferenced templates. A valid baseline therefore reports
**2 mobs, 0 raids, 0 events**.

## Event constraints

- No global HP or damage multiplier.
- Events require cooldowns/probability only where Ravents' actual schema provides
  them; otherwise they remain disabled rather than simulated with command hacks.
- Enhanced Celestials and its Blood Moon events are not part of the pack.
- Village, Millénaire, caravan and siege context is not assumed. It is enabled only
  after reliable settlement detection exists; ordinary villages must survive normal
  nights.

Future tiers must name every JSON file, counter, threshold, cooldown and test result
here before they are enabled.
