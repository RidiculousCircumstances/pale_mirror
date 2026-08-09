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

It creates a mock server player, materializes the test mine and one PM anchor,
persists a degraded optional Crimson encounter operation, destroys the anchor through the normal death event,
and verifies scenario resolution, recovery, and overlay cleanup. For a manual
server smoke test, use `./gradlew :pale-mirror-neoforge:runServer`; its local
port is configured in the ignored `pale-mirror-neoforge/run/server.properties`.

## Core-only manual check

1. Start the dev server and grant yourself permission level 4.
2. Stand in an empty 9×5×9 air volume and run `/pale_mirror testmine create`.
3. Run `/pale_mirror simulate step 1`, then `/pale_mirror scenario list`.
4. Accept the listed scenario and enter the mine.
5. Destroy the named `Pale Mirror Infection Anchor`.
6. Run `/pale_mirror simulate step 1`; the mine returns to operational state.

Use `/pale_mirror object inspect pale_mirror:test_mine` to see the PM anchor,
optional encounter state, and persisted materialization job.

Crimson is an explicitly optional presentation adapter. Its pinned public
surface currently has no safe local actor/controller contract, so it reports
`DEGRADED` and the PM anchor path remains fully playable. Run the isolated
packaged-JAR smoke profile with:

```bash
./gradlew :pale-mirror-neoforge:crimsonIntegrationHarness
```

This is a binary startup smoke test, not a full Crimson gameplay test: the
upstream datapack can log missing optional Spore resources in an otherwise
successful clean-server boot. Pale Mirror does not call those functions.
