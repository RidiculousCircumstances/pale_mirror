# Crimson Curse 1.4.3.1 L1 audit

Audit target: `mr_crimson_curse 1.4.3.1` from the pinned Packwiz URL.
The downloaded JAR matched the Packwiz SHA-512:

```text
7dff835f17bca4cf2a4efbf237393ccde1c12e4c334f3b3789d36499e3653c756a0be57af18ddae6e36e40cfcf93b33c21d3f47ce02c2d0a659da39fdb7d424c
```

The first part of the audit covered the public datapack surface. The second,
deliberately version-pinned part records the private actor protocol used by
`CrimsonSandboxProfile 1.4.3.1`. It has no Java classes: its implementation is
datapack functions, entity tags, teams, scores, and technical entities.

| Required Pale Mirror capability | Public L1 result | Decision |
| --- | --- | --- |
| Create exactly one local threat controller | `cc_cmd:begin_infection` writes global `Mass`; `cc_cmd:set_mass`, `set_phase`, and `set_points` write global scoreboards. No documented local-controller function exists. | Blocked |
| Bind controller to PM object/job/revision | No public function accepts a PM identifier or returns a native reference. | Blocked |
| Disable autonomous raid side effects | `cc_config:disable_raids` writes global `Raids_Enabled = 0`; Packwiz already applies this policy. | Available globally, insufficient for local controller |
| Detect one cleanup event after restart/unload | No public controller query, callback, or documented stable controller identity exists. | Blocked |
| Avoid unintended global progression writes | Every exposed infection control function changes global scoreboards. | Blocked |
| Public native encounter actor | The pinned JAR contains no `.class` files or custom entity registrations. Its public `crimson_curse:inf_mobs` tag contains only vanilla entity types. | No safe actor candidate |

## Public-surface outcome

Pale Mirror uses a PM-owned vanilla anchor as the canonical physical objective.
No public function offers a local controller, caller-supplied identity, or
safe local infection lifecycle. PM therefore never calls `cc_cmd:*` functions.

## Isolated sandbox profile

The enabled private compatibility layer is intentionally tiny and exact-version
only:

```text
Crimson 1.4.3.1
→ always-active top-priority built-in datapack shadows crimson_curse:load and :tick
→ PM owns all spread, phases, raids, and scheduling
→ PM creates a persisted vanilla zombie with PM provenance
→ PM-only initializer gives it the Crimsonified Human local form
```

The initializers reproduce sixteen audited local forms: seven Crimsonified,
their seven Decayed counterparts, Rusher and Raptor. They omit global teams,
sounds, particles, and `Global Mass += 15` / `+= 45`. Rusher's upstream
behaviour reads or writes `Second`, `Mass`, `Aggro`, `Leap`, `Global Points`,
and Bloodlink state, so PM replaces only its 7–11 block charge with a bounded
local runtime behavior. Raptor's upstream passive writes global scores, runs
animation functions, applies Bloodlinks, and can break doors; PM keeps only
local invisibility plus a close-range poison/weakness aura against the selected
in-site player. Other upstream actor passive/ability functions are not invoked.
Vanilla AI plus the bounded PM actor runtime is the supported profile set; exact
provenance and release restrictions are recorded in
[`crimson-content-ledger-1.4.3.1.md`](crimson-content-ledger-1.4.3.1.md).

PM-native tiers (`FOOTHOLD`, `INFESTED`, `SIEGE`, `APEX`) advance
deterministically in the domain and select encounter roster entries. They do
not read, write, or mirror Crimson's global Phase/Points/Mass values.

## Client visual contract

This is a private, non-distributed integration. PM therefore consumes the
client assets already present in the pinned Crimson JAR; it does not copy its
models or textures. The private pack's Entity Model Features 3.2.4 and Entity
Texture Features 7.1 load those CEM resources. Crimson's CEM rules select most forms by the vanilla
entity's exact custom name. Each PM profile has that name as a postcondition,
so a materialization whose initializer loses the visual identity is discarded
instead of becoming a generic vanilla mob. PM also recreates the audited
scale/equipment/model carriers for the siege forms.

Pummeler has no ghast CEM rule. It is rendered through a PM-owned
`item_display` passenger carrying the Crimson book model value `5450230`; the
passenger is part of the Pummeler postcondition and is removed with its
registered PM parent. `verifyCrimsonVisualContract` checks every used CEM name
and this display-model entry against the pinned JAR. It validates resources and
server-side entity data, not pixels rendered by a live graphical client.

## PM-owned siege profile

At `APEX`, the optional sandbox materializes a separate, PM-owned clearance
chain:

```text
four registered Node cells
→ one hash-selected boss (Juggernaut, Knight, Mangler, Pummeler, Kraken, or Osiris)
→ Bloodlink I → II → III
→ PM anchor becomes vulnerable
```

Nodes are Sea Lanterns placed only into the controlled mine's four registered
mutable cells; normal provenance preconditions still apply. Bosses and
Bloodlinks are vanilla base entities carrying PM object, job, role, slot and
profile data. Their UUIDs are saved in `SiegeRecord`; no world scan attempts to
discover a similar entity. Death and node-break events are typed observations,
deduplicated before a domain command advances the chain. The adapter blocks an
active scenario if this capability disappears rather than silently unsealing
the controller.

The six boss forms retain only bounded local behavior. Mangler uses a PM dash;
Pummeler and Kraken use direct non-griefing local pulses; Osiris uses a local
health phase; Bloodlinks use a local weakness aura. Ravager, Ghast, Phantom and
Bloodlink autonomous AI is disabled where needed. No form calls upstream
Bloodlink, raid, terrain conversion, global-score or player functions.

All private identifiers are confined to
`internal/integration/crimson`. PM stores the actor UUID, slot, object ID, and
job ID; death becomes a typed observation and never resolves the PM controller.
Absence, version mismatch, initializer failure, or identity conflict degrades
only the optional encounter operation.

`runCrimsonGameTestServer` proves a real Crimsonified Human, global-tick
shadowing, and the full Node → boss → Bloodlink → controller PM clearance
chain. Before every actor materialization the adapter also checks that the
selected `load` and `tick` resources still come from the sandbox pack. Re-audit
this profile for every Crimson update; if the test fails, the adapter must
remain `BLOCKED` rather than approximate compatibility.
