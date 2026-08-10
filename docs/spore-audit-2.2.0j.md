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
| `pale_mirror:spore_infected_human` | `spore:inf_human` | FOOTHOLD | supported |
| `pale_mirror:spore_braiomil` | `spore:braiomil` | INFESTED | supported |

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

Accordingly, PM-owned Spore actors are presentation forms only:

- persistent, provenance-tagged native entities;
- `NoAI`, no target, and invulnerable;
- reasserted by a bounded adapter tick over registered PM references only;
- never used as the controller or clearance condition;
- discarded by PM cleanup rather than killed, so native death/remains code is
  not invoked.

The PM vanilla anchor remains the unique controller.  It alone converts an
observed destruction into `RECOVERING`; Spore actor observations cannot alter
canonical threat state.

## What this does and does not prove

The Spore GameTest profile boots the checksum-pinned JAR and proves:

1. an explicit `pale_mirror:spore` facility selects the Spore scenario;
2. PM applies a provenance-safe vanilla fungal palette, never Spore terrain
   conversion;
3. both native registry forms materialize exactly once at PM tiers and retain
   dormant restrictions;
4. source identity survives a SavedData round trip;
5. a forged observation from the wrong source is rejected; and
6. controller clearance removes native forms through PM cleanup.

It does not yet provide combat-capable Spore actors, native Spore terrain,
organisms, raids, hiveminds, or a full content catalogue.  Those require an
isolated follow-up design that controls each native side effect before it is
enabled.
