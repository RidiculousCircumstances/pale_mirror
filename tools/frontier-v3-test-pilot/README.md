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

The one visible client performs the actual movement and break. It writes a JSON
evidence manifest plus the before/after X11 frames under
`build/frontier-v3-scenarios/`.

Mineflayer remains an offline compatibility/preflight helper only: a protocol
bot cannot complete this pack's full NeoForge handshake. It is excluded from
production artifacts and must never receive production credentials.

Scenario setup may manipulate a disposable world, but action entries are the
only causality evidence. A scenario cannot put a setup command in `actions`.
