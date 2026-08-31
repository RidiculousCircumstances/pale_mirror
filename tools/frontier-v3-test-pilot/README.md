# Frontier v3 test pilot

This tool proves a normal player's causal effect on Frontier v3. It is not a
simulation control plane: it sends ordinary client packets and read-only
`/pale_mirror v3 inspect` commands only.

## Profiles and isolation

`runFrontierV3PilotClient` is the default **lite** profile: NeoForge and the
current Pale Mirror source, with no installed modpack. It must connect to
`runFrontierV3PilotServer`, which has the same minimal dependency set. The
server receives an explicit seed, a per-run level name and a generated
offline-mode operator identity for the declared pilot. A run never shares its
world directory with the deployment server or another scenario.

An isolated scenario may additionally declare `"server": { "profile":
"hot-scene-strike" }`, `"hive-growth" }`, `"scene-return" }`,
`"hot-scout-sighting" }`, `"hot-scout-intercept" }`, `"health-quarantine" }`
or `"resident-transit" }`. These fail-closed development
fixtures are available only to the named disposable pilot runner. The first
selects a deterministic canonical HOT engagement; the second stops the real
twelve-settlement schedule at a durable exact-biomass receipt, so an ordinary
visit to the owned chest must cause the named organ, bioform and infection
advance. `scene-return` exposes one real COLD route continuation, while
`health-quarantine` begins with a contaminated infirmary and requires the
ordinary first strategic review to create the exact exposure and quarantine
facts. `resident-transit` begins with one genuine displaced resident, a bounded
reserved-bed corridor and no HOT body; an ordinary visit must materialize and
advance that same journey. The normal `world` profile remains the default. No profile gives the
pilot a canonical mutation API or force-loads chunks.

Run the checked-in isolated terminal-harvest regression on the visible `:0`
display:

```bash
DISPLAY=:0 npm run scenario:isolated -- scenarios/disposable-redwillow-harvest.json
```

The runner creates `pale-mirror-neoforge/build/runs/frontier-v3-pilot-server/<unique-world>`,
starts one local server on port `25575` (override with
`FRONTIER_V3_PILOT_PORT`), then stops and removes that exact generated world.
Set
`FRONTIER_V3_KEEP_DISPOSABLE=true` only when preserving a failed world for
diagnosis. It never resets a live/server-pack world.

The harvest regression is deliberately strict: at present it exposes a real
strategic-objective starvation defect rather than yielding a false green
result. A passing run requires the complete domain result described below.

`FRONTIER_V3_PILOT_PROFILE=pack npm run scenario -- ...` remains available for
the final full-pack compatibility and visual gate. It is deliberately not the
default loop.

## Scenario contract

All scenarios declare a server and pilot. Isolated scenarios additionally use:

```json
"isolation": { "mode": "disposable_lite", "seed": 41 }
```

Setup actions may arrange a deterministic test location. Evidence actions are
normal player actions. `wait_until_diagnostic` is useful for a stable single
canonical predicate. Do not use it to wait for a transient phase such as
`READY` when testing an end-to-end operation.

`wait_until_harvest_result` waits for all three terminal facts together:

- the named physical intent is `CONFIRMED`;
- the named exact 64-wheat stack has `CONTAINER_SLOT` custody in the depot;
- the named site entered its next `GROWING` epoch.

This is the pattern for future domain waits: model the player-meaningful result,
not one internal waypoint.

`/pale_mirror v3 inspect intent <id>` adds `physicalReadiness` for a pending
resource-site harvest. It reports loaded field/depot chunks, canonical depot
surface state, exact owned chest presence, mature-block match and output-slot
emptiness. It reads the naturally loaded world only; it does not load chunks,
write blocks, change WAL or repair a mismatch.

## Evidence and gates

Each scenario emits a manifest and a sibling `*.pmv3.jsonl`. JSONL contains
only PMV3 records such as `run_started`, `action_started`,
`diagnostic_received`, `action_completed`, `frame_captured` and `run_finished`.
Every evidence action has a stable `scenario:<run-id>:<step>` correlation ID.
Third-party Forge/modpack warnings are intentionally not copied into this
stream.

Use the cheapest sufficient gate while iterating:

| Gate | When | Evidence |
| --- | --- | --- |
| Fast | each small edit | `npm test` and focused JUnit tests |
| Minecraft boundary | changed executor, client packet, persistence or physical observation | relevant GameTest server |
| Live flow | changed one causal flow | one matching isolated scenario |
| Milestone/commit | critical Frontier code | repository full critical Gradle gate, then the relevant visible scenario; full-pack visual only when its compatibility/art is in scope |

The repository guardrails still require the full critical Gradle gate before a
commit touching canonical/materialization/test-harness code. The matrix avoids
running unrelated visual or third-party-pack suites on every local edit; it
does not weaken that final gate.
