# Graybox runtime profile

The source-graybox test world must have one authoritative living-world model:
Pale Mirror. The normal Far Frontier pack intentionally contains optional
content for broader play, but those independent simulations would make a
graybox test ambiguous.

Apply the profile separately to the disposable server and to the matching
audit-client runtime while both are stopped:

```bash
scripts/set-graybox-runtime-profile.sh --target /home/rd/far-frontier-server
scripts/set-graybox-runtime-profile.sh --target pale-mirror/pale-mirror-neoforge/build/runs/railway-client
```

It reversibly disables Alex's Caves, Born in Chaos, Hostile Tactics, Cataclysm,
Naturalist, Ravents and RPG Timeline. These are the non-PM sources of creatures,
infection, events or time-line authority. No separate Blood Moon JAR is
installed in the current runtime; this profile covers the actual competing
sources instead.

The server application also sets `spawn-animals`, `spawn-monsters` and
`spawn-npcs` to `false`. PM materializes its residents and bioforms explicitly,
so those properties do not suppress PM actors. Existing third-party entities
are not deleted: their removal would be an untyped simulation mutation. They
are allowed to despawn naturally or are handled through a separately auditable
operator action.

The profile does not remove Pale Mirror, Pale Mirror Visuals, Create, player
items, rendering/performance mods or inert dependency libraries. In particular,
Pale Mirror Visuals declares an exact hard dependency on Villager Overhaul, so
the profile retains that bridge while PM owns every managed resident's identity,
movement, combat and mortality. It also retains Crimson and Spore: PM's built-in
sandbox makes them visual carriers with no native spreading, spawning or actor
lifecycle, and the current registered datapacks reference their content. Mowzie's
Mobs is also retained as a saved-data compatibility carrier: existing living
entities have its attachment keys, whereas all natural spawning is disabled by
this profile. Startup proof caught these dependencies explicitly. Rerun the
profile after Packwiz updates, which may restore the JAR files.

To return a runtime to the full pack, stop it and run:

```bash
scripts/set-graybox-runtime-profile.sh --target RUNTIME --restore
```
