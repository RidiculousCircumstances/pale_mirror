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
./gradlew :pale-mirror-neoforge:build
```

The distributable JAR is under `pale-mirror-neoforge/build/libs/`. `check`
verifies that it embeds the pure domain module.

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

## Core-only manual check

1. Start the dev server and grant yourself permission level 4.
2. Stand in an empty 9×5×9 air volume and run `/pale_mirror testmine create`.
3. Run `/pale_mirror simulate step 1`, then `/pale_mirror scenario list`.
4. Accept the listed scenario and enter the mine.
5. Destroy the named `Pale Mirror Infection Anchor`.
6. Run `/pale_mirror simulate step 1`; the mine returns to operational state.

Use `/pale_mirror object inspect pale_mirror:test_mine` to see the PM anchor,
optional encounter state, and persisted materialization job.

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
