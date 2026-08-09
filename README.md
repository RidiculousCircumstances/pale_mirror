# Pale Mirror

Pale Mirror is the server-authoritative world simulation for Far Frontier. Its
first vertical slice is deliberately standalone: a controlled mine can become
infected in the abstract model, receive an Investigation/Recovery scenario, be
materialized through the built-in TestThreat adapter, and recover after the
player destroys that threat.

## Build

Java 21 is mandatory. The Gradle toolchain resolver provisions it when needed:

```bash
./gradlew check
./gradlew :pale-mirror-neoforge:build
```

The distributable JAR is under `pale-mirror-neoforge/build/libs/`. `check`
verifies that it embeds the pure domain module.

## Core-only manual check

1. Start the dev server and grant yourself permission level 4.
2. Stand in an empty 9×5×9 air volume and run `/pale_mirror testmine create`.
3. Run `/pale_mirror simulate step 1`, then `/pale_mirror scenario list`.
4. Accept the listed scenario and enter the mine.
5. Destroy the named `Pale Mirror Test Threat`.
6. Run `/pale_mirror simulate step 1`; the mine returns to operational state.

Crimson integration is intentionally reported as `BLOCKED` until its public L1
surface passes the separate compatibility audit.
