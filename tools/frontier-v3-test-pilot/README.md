# Frontier v3 test-pilot

This is development-only automation inside one normal NeoForge Minecraft
client. It never ships in the NeoForge JAR or Packwiz pack. Its evidence path
is deliberately narrow:

```text
native client action -> normal server event -> v3 observation -> WAL -> PMV3_DIAG
```

## Local run

1. Start a disposable v3 server. The scenario starts exactly one normal
   NeoForge test-pilot client on `DISPLAY=:0`.
2. Grant temporary operator status to `PMTestPilot`; the sample uses it only
   for disposable-world setup and read-only `/pale_mirror v3 inspect`, never
   for a canonical v3 mutation.
3. From this directory, install the pinned development dependencies and run:

   ```bash
   npm ci
   DISPLAY=:0 npm run scenario -- scenarios/field-player-break.json
   ```

The one visible client performs the actual movement and break. Its isolated
development run uses the user session's XWayland bridge so that a frame is
captured from the exact fullscreen Minecraft window, rather than from the
whole desktop. It writes a JSON evidence manifest plus the before/after X11 frames under
`build/frontier-v3-scenarios/`.

The run uses a disposable copy of that installed pack and excludes only
`pale_mirror-hosted.jar`; NeoForge ModDev then supplies the current source set.
This prevents a stale installed JAR from silently testing different code.

For an acceptance frame, place a local `hud` action and a short `wait` before
the frame declaration. It changes only the pilot's presentation, is restored
on disconnect, and cannot issue a server or canonical-state mutation.

Mineflayer remains an offline compatibility/preflight helper only: a protocol
bot cannot complete this pack's full NeoForge handshake. It is excluded from
production artifacts and must never receive production credentials.

Scenario setup may manipulate a disposable world, but action entries are the
only causality evidence. A scenario cannot put a setup command in `actions`.
