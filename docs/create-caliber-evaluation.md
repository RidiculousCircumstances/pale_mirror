# Create: Caliber evaluation

## Decision: OPTIONAL (disabled)

Pinned candidate: Create: Caliber `0.2.0` for NeoForge 1.21.1, released 2026-07-10.
It is explicitly beta/WIP and has no demonstrated stability history comparable to
the rest of the core. It is retained as a Packwiz optional option solely for research;
it is not downloaded in the intended core server profile.

## What the mod appears to provide

It advertises Create-integrated kinetic firearms and automated ammunition production.
That could create a meaningful mid/late Create supply chain, but the present evidence
does not establish mature multiplayer safety, Better Combat interaction, or acceptable
boss time-to-kill.

## Promotion gates

1. Clean dedicated-server boot with Create `6.0.10`.
2. Assemble a complete ammunition chain and document inputs, throughput, storage and
   failure behaviour.
3. Measure firearm damage and range against Simply Swords, Mowzie and Cataclysm;
   reject it if it trivialises encounter design.
4. Gate acquisition to a documented mid/late milestone using a supported data/config
   mechanism, not an opaque command hack.
5. Test save/reload, multiplayer and contraption interaction.

Until all gates pass, choosing the option is an experimental profile decision.
