# Pale Mirror

Pale Mirror is the server-authoritative world simulation for Far Frontier. Its
first vertical slice is deliberately standalone: a controlled mine can become
infected in the abstract model, receive an Investigation/Recovery scenario, be
materialized through a PM-owned vanilla anchor, and recover after the player
destroys that anchor. Optional integrations can add presentation but cannot
become a second source of truth.

## Build

Java 21 is mandatory. The Gradle toolchain resolver provisions it when needed:

```bash
./gradlew check
./gradlew :pale-mirror-neoforge:build :pale-mirror-visuals:build
```

The required product profile consists of the core JAR under
`pale-mirror-neoforge/build/libs/` and the visual JAR under
`pale-mirror-visuals/build/libs/`. `check` verifies core domain packaging and
the visual asset/module contract.

## Runtime verification

Run the core-slice GameTest in a real NeoForge server level:

```bash
./gradlew :pale-mirror-neoforge:runGameTestServer
```

It creates a mock server player, materializes the test mine and its PM anchor,
handles optional encounter failure, destroys the anchor through the normal
death event, and verifies scenario resolution, recovery, and overlay cleanup.
For a manual server smoke test, use `./gradlew :pale-mirror-neoforge:runServer`;
its local port is configured in the ignored `pale-mirror-neoforge/run/server.properties`.

## First Living Region release candidate

The current 0.3 completion-candidate behavior and its repeatable playtest
procedure are documented in
[living-frontier-0.3-completion.md](docs/living-frontier-0.3-completion.md).

On a fresh product-profile world, Pale Mirror Visuals deterministically authors
three independent frontier regions by default; the batch count, map radius and
minimum spacing are configurable. Each begins as a PM-owned radial timber
fort with a civic center, freight district, 48 stable residents and reserved
development plots. Observed vanilla/Integrated Villages places no longer
bootstrap the default campaign. The campaign materializes two provenance-preflighted MineSites,
including a loading yard, readable entrance, descending supported drift and
underground controller chamber.

The fresh Mine17–Ironhill baseline is a complete, low-capacity vanilla
minecart corridor: regular rails, powered intervals, trestles and a receiving
platform. Its terrain-costed, grade-safe path may turn around expensive terrain;
it is compiled into immutable chunk-local slices and written only by normal
world generation when each chunk is first created. Runtime adopts exact
generation stamps and provenance but never retrofits missing genesis geometry.
Health is the observed endpoint-connected rail graph, so a
connected player reroute is valid without recreating the authored geometry.
Its cart is only a readable representative; Pale Mirror still owns route
capacity and `IRON`. A player-built Create route is certified separately by the
read-only Create adapter. Railway Untold `1.2.1-pm.1` remains an isolated,
explicit industrial-upgrade provider rather than the default for a small
village. First recognition gives the player a survey map, welcome letter and
dynamic native `Regional Ledger` written book.

The authored manifest is pinned before players are admitted and reused after
restart. Prospective terrain is sampled through generator APIs; normal overworld
generation alone grades and builds each compiled slice. Loaded-chunk events are
read-only observations of persisted stamps. See
[pale-mirror-visuals.md](docs/pale-mirror-visuals.md) for module boundaries,
resident ownership, parcel commissioning, semantic reconstruction, canonical
journeys and visual state behavior.

Run the optional industrial railway profile and packaged two-start harness with:

```bash
./gradlew :pale-mirror-neoforge:runRailwayGameTestServer
./gradlew :pale-mirror-neoforge:managedRailwayIntegrationHarness
./gradlew :pale-mirror-visuals:runGameTestServer
./gradlew :pale-mirror-visuals:visualsIntegrationHarness
```

Provider pin, safety ownership and the remaining graphical acceptance steps are
documented in [managed-railway-integration.md](docs/managed-railway-integration.md).

## Core-only manual check

1. Start the dev server and grant yourself permission level 4.
2. Stand in an empty 9×5×9 air volume and run `/pale_mirror testmine create`.
3. Run `/pale_mirror simulate step 1`, then `/pale_mirror scenario list`.
4. Accept the listed scenario and enter the mine.
5. Destroy the named `Pale Mirror Infection Anchor`.
6. Run `/pale_mirror simulate step 1`; the mine returns to operational state.

Use `/pale_mirror object inspect pale_mirror:test_mine` to see the PM anchor,
optional encounter state, and persisted materialization job.

## PM staged infection biome

New controlled test mines use `test-mine-v2`: four dedicated Node cells plus
66 separately registered biome cells. The biome is a PM-owned materialization
layer, works without Crimson, and changes only when the canonical PM threat
tier changes:

| PM tier | Safe visual layer |
| --- | --- |
| `FOOTHOLD` | nine central `netherrack` cells |
| `INFESTED` | the center evolves to `crimson_nylium`; a second ring becomes `netherrack` |
| `SIEGE` | the center becomes `nether_wart_block`, the inner ring `crimson_nylium`, and the outer floor ring `netherrack` |
| `APEX` | the outer ring also evolves to `nether_wart_block`; 21 predeclared wall/ceiling cells become `shroomlight` |

The four Node cells remain outside the decorative palette and are used only by
the PM siege chain. Every changed cell stores its baseline and last PM-applied
block. A cell is changed only when its current block is that baseline or PM's
last value; an unknown modification marks the cell conflicted and blocks the
persisted job without overwriting it. Recovery restores only PM-owned cells.

PM deliberately does not call Crimson terrain conversion, `fillbiome`, worldgen
or unrestricted block scans. Schema v9 migrates existing v1 mines conservatively:
their four recorded cells stay legacy Node cells, rather than claiming nearby
terrain that PM did not previously own. Create a new test mine to use the full
staged biome.

Crimson is an explicitly optional, version-pinned sandbox integration. PM
shadows Crimson's global bootstrap and tick, and remains the owner of spread,
phases, raids, and recovery. With Crimson `1.4.3.1`, the Apex encounter has
sixteen PM-owned local forms: seven Crimsonified, seven Decayed, Rusher and
Raptor. Rusher's dash and Raptor's aura run in the bounded PM runtime; killing
any encounter actor is observed but cannot resolve the PM anchor.

At `APEX`, an available Crimson sandbox additionally enables the PM siege
chain: four Sea Lantern Nodes in the mine's predeclared mutable cells, one
deterministically selected boss form, and Bloodlink I–III. All four Nodes, the
boss, and each Bloodlink must be cleared before the PM anchor accepts damage.
PM persists every physical reference and never invokes Crimson's global
infection, phase, raid, terrain, or tick functions. If the pinned adapter is
unavailable while this chain is active, the scenario visibly blocks; core-only
worlds bypass the optional chain rather than receiving a surprise lock.

## Crimson visuals in the private pack

Pale Mirror does not copy Crimson assets. In the private Far Frontier pack,
the installed pinned Crimson JAR supplies its client resources and the already
installed Entity Model Features 3.2.4 / Entity Texture Features 7.1 bridge
interprets its CEM rules. PM makes those resources render by creating the same vanilla base
entities, exact CEM-selecting names, equipment and scale used by the audited
local forms. The Pummeler also has its Crimson custom-model item-display
passenger. This keeps visual assets in Crimson while PM keeps the lifecycle,
AI policy and world state.

The runtime fails the operation rather than accepting a visually malformed
actor: PM verifies the exact name for every roster/siege profile and verifies
the Pummeler passenger. `verifyCrimsonVisualContract` compares all required
CEM names and the Pummeler model entry with the checksum-pinned Crimson JAR.
It also verifies the Raptor and Bloodlink model-frame resources plus Osiris
Brain's CEM resource.

`CrimsonPresentationRuntime` owns the live presentation of the supported PM
roster. It emits the audited local spawn, ambient, hurt, death and attack cues;
drives Raptor and Bloodlink book-model frames; provides short-lived invisible
PM markers for Rusher/Mangler CEM dash poses; and gives Osiris a safe,
PM-owned Brain visual passenger. All effects are bounded to registered PM
entities, have a per-tick work budget, and are deleted with their parent. It
uses only vanilla sound/particle APIs and never calls upstream Crimson
functions, scoreboards, raids, terrain conversion or infection logic.

This verifies server-side visual data and effect dispatch; an actual
rendered-frame check still requires a graphical client playthrough.

Run the real Crimson actor and tick-isolation GameTest with:

```bash
./gradlew :pale-mirror-neoforge:runCrimsonGameTestServer
./gradlew :pale-mirror-neoforge:verifyCrimsonVisualContract
```

Run the isolated packaged-JAR boot profile with:

```bash
./gradlew :pale-mirror-neoforge:crimsonIntegrationHarness
```

The packaged-JAR task is a boot smoke test, not a full Crimson gameplay test:
the upstream datapack can log missing optional Spore resources in an otherwise
successful clean-server boot. Pale Mirror does not call those functions.

## Spore controlled combat

With the checksum-pinned Spore `2.2.0j` installed, the two PM Spore forms are
stationary local defenders. PM holds their native AI, target acquisition,
navigation and movement off; it owns their hit points and attack cooldown in
SavedData. A defender can attack only a non-spectator player within its
registered mine bounds and fixed profile range. Player hits are consumed by
PM before Spore's native damage/death code runs. A lethal hit safely discards
the form and records a presentation-only defeat; it cannot leave Spore remains,
spread infection or resolve the PM anchor. Native movement, evolution, terrain,
organisms, raids and hivemind behaviors remain disabled.
