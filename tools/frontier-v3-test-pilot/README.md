# Frontier v3 test-pilot

This is a development-only normal Minecraft protocol client. It never ships in
the NeoForge JAR or Packwiz pack. Its evidence path is deliberately narrow:

```text
Mineflayer action -> normal server event -> v3 observation -> WAL -> PMV3_DIAG
```

## Local run

1. Start a disposable v3 server and join exactly one normal NeoForge client as
   operator `PMAudit` on `DISPLAY=:0`.
2. Grant temporary operator status to `PMTestPilot`; the sample's setup uses it
   only for teleport/spectate and read-only `/pale_mirror v3 inspect`.
3. From this directory, install the pinned development dependencies and run:

   ```bash
   npm ci
   DISPLAY=:0 npm run scenario -- scenarios/field-player-break.json
   ```

The sample makes `PMAudit` spectate the bot, so the one visible client shows
the bot's actual movement and break. It writes a JSON evidence manifest plus
the before/after X11 frames under `build/frontier-v3-scenarios/`.

`npm audit` currently reports six moderate transitive findings in Mineflayer's
optional Microsoft-auth path. The pilot uses offline local authentication and
is excluded from production artifacts; do not expose it to an untrusted
network or use it with production credentials.

Scenario setup may manipulate a disposable world, but action entries are the
only causality evidence. A scenario cannot put a setup command in `actions`.
