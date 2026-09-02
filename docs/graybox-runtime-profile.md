# Graybox runtime profile

The source-graybox test world must have one authoritative living-world model:
Pale Mirror. The normal Far Frontier pack intentionally contains optional
content for broader play, but those independent simulations would make a
graybox test ambiguous.

Apply the profile to the disposable server while it is stopped:

```bash
scripts/set-graybox-runtime-profile.sh --target /home/rd/far-frontier-server
```

It reversibly disables only Hostile Tactics, which is declared server-only by
the Packwiz manifest. A mod declared `side = "both"` must stay installed: an
ordinary player client uses the standard pack and NeoForge requires the same
network-mod set on the server. PM's entity-admission and world-side ownership
boundaries, not asymmetric JAR removal, suppress shared-mod activity in the
graybox. No separate Blood Moon JAR is installed in the current runtime.

The server application keeps `spawn-animals`, `spawn-monsters` and `spawn-npcs`
enabled. These server properties are not a graybox isolation mechanism:
vanilla discards existing Villagers when `spawn-npcs=false`, which would also
delete PM residents. Instead, `SourceGrayboxEntityAdmission` rejects every
non-PM `Mob` at the entity-join boundary of the source-graybox dimension. This
keeps canonical residents and bioforms alive under normal Minecraft lifetime
rules while allowing ordinary world behavior outside that test dimension.

The profile does not remove Pale Mirror, Pale Mirror Visuals, Create, player
items, rendering/performance mods or inert dependency libraries. In particular,
Pale Mirror Visuals declares an exact hard dependency on Villager Overhaul, so
the profile retains that bridge while PM owns every managed resident's identity,
movement, combat and mortality. It retains Spore only because the current
disposable world's saved `far-frontier-spore-zones` datapack registers `spore:*`
values and NeoForge refuses to load that world without the registry provider;
native spawning remains disabled and PM owns the Spore sandbox lifecycle. Rerun
the profile after Packwiz updates, which may restore the JAR files. If Packwiz
has recreated one beside its previous `.graybox-disabled` copy, the profile
archives that old copy under `.far-frontier-graybox-profile/packwiz-replaced-jars/`
and then disables the freshly managed JAR; it never silently deletes either copy.

To return a runtime to the full pack, stop it and run:

```bash
scripts/set-graybox-runtime-profile.sh --target RUNTIME --restore
```
