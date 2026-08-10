# Spore 2.2.0j integration audit

## Pin and boundary

- Target: `spore` / Fungal Infection:Spore `2.2.0j` for NeoForge 1.21.1.
- Artifact: Modrinth version `DwE3w8IX`, SHA-512
  `e9830e70d02aeea60eaf0a0484ca153bb8e5f30f10a78e3191819ff093108c07664afaf6625a5e1d3f514c9f67fdd15b`.
- Domain, generic materialization, persistence and scenario code reference no
  Spore class, SavedData or state. The version-specific black box is entirely
  under `internal/integration/spore/`.
- The black box deliberately contains exact-version mixins. They are declared
  only when `spore` is present, use no reflection or access transformers, and
  suppress upstream global handlers rather than exposing them to the rest of
  PM. Any version mismatch leaves the adapter visibly `BLOCKED`.

## Audited first roster

| PM profile | Pinned registry id | PM tier | State |
| --- | --- | --- | --- |
| `pale_mirror:spore_infected_human` | `spore:inf_human` | FOOTHOLD | PM patrol + close pressure |
| `pale_mirror:spore_infected_husk` | `spore:inf_husk` | FOOTHOLD | PM patrol + close pressure |
| `pale_mirror:spore_braiomil` | `spore:braiomil` | INFESTED | PM pursuit + close pressure |
| `pale_mirror:spore_spitter` | `spore:spitter` | SIEGE/APEX | constrained ranged pressure |

The adapter verifies the installed mod id, exact version, and both registry
entries before materialization.  A missing or incompatible installation is an
explicit `ABSENT`/`BLOCKED` adapter health result; the PM site still has its
vanilla anchor and bounded PM overlay.

## Safety conclusion

Spore infected classes contain native hunger/evolution logic.  Their base
implementation can break blocks and, after starvation, can place native
remains/growth blocks during `die`.  Braiomil also has native combat/evolution
logic.  These mechanisms are not PM-authoritative and cannot run in the first
slice.

Accordingly, PM has two separate controls: a global firewall and constrained
site-local forms.

### Global firewall

- the built-in top pack `spore_sandbox` shadows both native biome modifiers,
  the Spore structure spawn modifier and therefore ambient foliage/spawn-rule
  injection;
- exact-version mixins cancel Spore's global server tick, world bootstrap,
  death conversion and native spawn handler, as well as `Infection` death
  processing;
- a server `EntityJoinLevelEvent` boundary rejects every Spore-namespace
  entity without PM actor provenance, including an entity created by an
  otherwise missed upstream path;
- all firewall code is diagnostic-only with respect to PM state. It never
  creates domain events, mutates SavedData, or selects scenario outcomes.

### PM-owned forms

- persistent, provenance-tagged native entities;
- `NoAI`, no target, stopped navigation and zeroed movement, reasserted only
  over registered PM references;
- a bounded PM executor selects only a nearby player inside the same registered
  site and applies profile-owned damage/sound/particle presentation on a
  persisted cooldown. Human/Husk forms follow a fixed safe patrol lattice;
  Braiomil takes one safe PM step toward a player; Spitter stays stationary
  and uses PM ranged pressure. No actor uses native navigation, targeting,
  projectiles, infection, evolution or terrain work;
- incoming player damage is converted to PM-owned persisted combat HP before
  Spore's `hurt` path runs; all non-player/out-of-site damage is rejected;
- a lethal PM combat hit discards the actor and emits one typed presentation
  observation, instead of calling native `die` and its remains path;
- never used as the controller or clearance condition. The PM anchor remains
  the unique recovery condition.

The encounter datapack uses exact-tier compositions (`FOOTHOLD`, `INFESTED`,
`SIEGE`, `APEX`). Selection is deterministic from world seed, object id and
desired revision, then the chosen composition id and actor refs are persisted
in `EncounterRecord`. The translator reads that record, not the live datapack,
while the job is executing; reload therefore cannot reshuffle an active site.
Schema v13 adds this pin, after v12 added independent PM movement scheduling.

The PM vanilla anchor remains the unique controller.  It alone converts an
observed destruction into `RECOVERING`; Spore actor observations cannot alter
canonical threat state.

## What this does and does not prove

The Spore GameTest profile boots the checksum-pinned JAR and proves:

1. an explicit `pale_mirror:spore` facility selects the Spore scenario;
2. PM applies a provenance-safe vanilla fungal palette, never Spore terrain
   conversion;
3. the four-form audited roster materializes only through exact PM tier
   compositions, retains constrained restrictions, and uses PM-owned
   health/cooldown/movement state;
4. source identity survives a SavedData round trip;
5. a forged observation from the wrong source is rejected; and
6. only a player in the site may damage a constrained form; external damage is
   rejected, a lethal approved hit leaves no Spore remains, and a defeated form
   never respawns on a later desired revision; and
7. unmanaged Spore entities are rejected at the server entity boundary; and
8. controller clearance removes remaining native forms through PM cleanup.

`sporeIntegrationHarness` additionally installs a clean NeoForge dedicated
runtime, starts the final packaged PM JAR next to the pinned Spore JAR,
terminates it, and starts the same world again. It verifies the packaged
mixin/dependency boundary and normal server restart independently of Gradle's
development classpath. `runSporeClient` prepares the equivalent client runtime
for visual inspection; it requires an actual graphical session.

It does not enable native Spore combat AI, native projectiles, terrain,
organisms, raids, hiveminds, or the un-audited content catalogue. In
particular, native projectile transfer needs a separately persisted
PM-provenance/effect record before it can be safely permitted through the
firewall. Each additional capability still requires its own isolated
side-effect audit.
