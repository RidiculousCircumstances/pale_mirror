# Crimson Curse 1.4.3.1 L1 audit

Audit target: `mr_crimson_curse 1.4.3.1` from the pinned Packwiz URL.
The downloaded JAR matched the Packwiz SHA-512:

```text
7dff835f17bca4cf2a4efbf237393ccde1c12e4c334f3b3789d36499e3653c756a0be57af18ddae6e36e40cfcf93b33c21d3f47ce02c2d0a659da39fdb7d424c
```

The audit was deliberately limited to public datapack functions, public
commands, vanilla registries, and normal entity/block observations. No
reflection, mixin, access transformer, or direct internal class dependency was
considered.

| Required Pale Mirror capability | Public L1 result | Decision |
| --- | --- | --- |
| Create exactly one local threat controller | `cc_cmd:begin_infection` writes global `Mass`; `cc_cmd:set_mass`, `set_phase`, and `set_points` write global scoreboards. No documented local-controller function exists. | Blocked |
| Bind controller to PM object/job/revision | No public function accepts a PM identifier or returns a native reference. | Blocked |
| Disable autonomous raid side effects | `cc_config:disable_raids` writes global `Raids_Enabled = 0`; Packwiz already applies this policy. | Available globally, insufficient for local controller |
| Detect one cleanup event after restart/unload | No public controller query, callback, or documented stable controller identity exists. | Blocked |
| Avoid unintended global progression writes | Every exposed infection control function changes global scoreboards. | Blocked |
| Public native encounter actor | The pinned JAR contains no `.class` files or custom entity registrations. Its public `crimson_curse:inf_mobs` tag contains only vanilla entity types. | No safe actor candidate |

## Outcome

Pale Mirror now uses a PM-owned vanilla anchor as the canonical physical
objective. `CrimsonEncounterAdapter` reports `DEGRADED` when the JAR is present: authored encounter slots are
persisted and visible to diagnostics, but are deliberately not replaced by
ordinary vanilla mobs pretending to be native Crimson content. This explicit
degradation never blocks the PM recovery loop.

Reopen the actor portion of this audit only if Crimson Curse publishes a stable
local controller/actor API with caller-supplied identity/reference and an
observable removal contract, or a documented public entity representation with
equivalent lifecycle guarantees.
