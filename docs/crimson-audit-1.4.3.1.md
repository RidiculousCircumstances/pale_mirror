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

The initializer reproduces the zombie branch's local name, tags, attributes,
equipment, loot table, and persistence, but omits its global team, sounds,
particles, and `Global Mass += 15` side effect. The integration currently does
**not** invoke Crimson's actor passive/ability functions: several read or write
global scores. Base zombie AI plus Crimson's actor form is the supported first
profile.

All private identifiers are confined to
`internal/integration/crimson`. PM stores the actor UUID, slot, object ID, and
job ID; death becomes a typed observation and never resolves the PM controller.
Absence, version mismatch, initializer failure, or identity conflict degrades
only the optional encounter operation.

`runCrimsonGameTestServer` proves both that a real Crimsonified Human is
materialized and that the original global `crimson_curse:tick` does not process
a test actor. Before every actor materialization the adapter also checks that
the selected `load` and `tick` resources still come from the sandbox pack.
Re-audit this profile for every Crimson update; if the test fails, the adapter
must remain `BLOCKED` rather than approximate compatibility.
