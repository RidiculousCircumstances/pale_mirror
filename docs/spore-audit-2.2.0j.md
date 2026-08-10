# Spore 2.2.0j integration audit

## Pin and boundary

- Target: `spore` / Fungal Infection:Spore `2.2.0j` for NeoForge 1.21.1.
- Artifact: Modrinth version `DwE3w8IX`, SHA-512
  `e9830e70d02aeea60eaf0a0484ca153bb8e5f30f10a78e3191819ff093108c07664afaf6625a5e1d3f514c9f67fdd15b`.
- PM references only the public entity registry at runtime.  No Spore class,
  mixin, reflection, access transformer, or Spore SavedData enters domain,
  generic materialization, persistence, or scenario code.
- The version-specific implementation is entirely
  `internal/integration/spore/SporeSandboxAdapter`.

## Audited first roster

| PM profile | Pinned registry id | PM tier | State |
| --- | --- | --- | --- |
| `pale_mirror:spore_infected_human` | `spore:inf_human` | FOOTHOLD | constrained stationary combat |
| `pale_mirror:spore_braiomil` | `spore:braiomil` | INFESTED | constrained stationary combat |

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

Accordingly, PM-owned Spore actors remain constrained forms:

- persistent, provenance-tagged native entities;
- `NoAI`, no target, stopped navigation and zeroed movement, reasserted only
  over registered PM references;
- a bounded PM executor selects only a nearby player inside the same registered
  site and applies a static, profile-owned vanilla damage event on a persisted
  cooldown; actors never move, navigate, summon, infect, evolve or break
  terrain;
- incoming player damage is converted to PM-owned persisted combat HP before
  Spore's `hurt` path runs; all non-player/out-of-site damage is rejected;
- a lethal PM combat hit discards the actor and emits one typed presentation
  observation, instead of calling native `die` and its remains path;
- never used as the controller or clearance condition. The PM anchor remains
  the unique recovery condition.

The PM vanilla anchor remains the unique controller.  It alone converts an
observed destruction into `RECOVERING`; Spore actor observations cannot alter
canonical threat state.

## What this does and does not prove

The Spore GameTest profile boots the checksum-pinned JAR and proves:

1. an explicit `pale_mirror:spore` facility selects the Spore scenario;
2. PM applies a provenance-safe vanilla fungal palette, never Spore terrain
   conversion;
3. both native registry forms materialize exactly once at PM tiers, retain
   their constrained restrictions, and use PM-owned health/cooldown state;
4. source identity survives a SavedData round trip;
5. a forged observation from the wrong source is rejected; and
6. only a player in the site may damage a constrained form; external damage is
   rejected, a lethal approved hit leaves no Spore remains, and a defeated form
   never respawns on a later desired revision; and
7. controller clearance removes remaining native forms through PM cleanup.

It provides only static PM-controlled attacks, not native Spore combat AI,
movement, terrain, organisms, raids, hiveminds, or a full content catalogue.
Each additional capability still requires its own isolated side-effect audit.
