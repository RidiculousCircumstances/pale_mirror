# VibeCraft for NeoForge 1.21.1

Repository-owned adaptation of [amenti-labs/VibeCraft](https://github.com/amenti-labs/vibecraft),
pinned upstream `be5045f20027b60dd1d1a8604379c51ebf84e2f4` (MIT; see [LICENSE](LICENSE)).
The client sources and Python MCP server are kept here as ordinary source files,
without another Git repository or a submodule. Upstream's standalone deployment,
Fabric installers and agent instructions are not part of this adaptation.

The client bridge runs on **Minecraft 1.21.1 / NeoForge 21.1.248**, separately from
Pale Mirror. It exposes world reading, ordinary player commands and actual client
screenshots over a local WebSocket. The Python server exposes those capabilities
through MCP stdio. WorldEdit is optional; vanilla `/setblock` and `/fill` work.

## Build and install the client

With Java 21:

```sh
cd client-mod
./gradlew build --no-daemon
```

Copy `build/libs/vibecraft-neoforge-1.21.1-0.1.0-pm.1.jar` into the **client's**
`mods/` directory. It bundles Java-WebSocket using NeoForge Jar-in-Jar.
This is a client-only authoring tool; no dedicated-server installation or restart
is needed. Building this directory does not rebuild the Pale Mirror simulation.

Start Minecraft, join the intended test world and run `/vibecraft allow`.
Commands execute as that player and retain the server's normal permission checks.
`/vibecraft deny` disables mutations; `/vibecraft stop` stops the local bridge.
`/vibecraft status`, `start`, `restart`, `token <value>`, and `port <number>` are
also available. Configuration is `config/vibecraft-client.json` in the client
profile, default host `127.0.0.1`, port `8766`, path `/vibecraft`.

## Connect an MCP client

Install [uv](https://docs.astral.sh/uv/) and configure an MCP stdio server:

```json
{
  "mcpServers": {
    "vibecraft": {
      "command": "/absolute/path/to/pale-mirror/tools/vibecraft/run-mcp.sh",
      "env": {
        "VIBECRAFT_WORLDEDIT_MODE": "off",
        "VIBECRAFT_CLIENT_TOKEN": "match-the-client-token-if-set"
      }
    }
  }
}
```

Omit `VIBECRAFT_CLIENT_TOKEN` if no token is configured. `VIBECRAFT_CLIENT_PORT`
selects another port. The launcher uses the checked-in `uv.lock`; diagnostics go
to stderr and ignored log files, while stdout carries only MCP messages.
No global MCP configuration is modified by these scripts.

Useful tools: `search_minecraft_item`, `get_player_context`, `scan_region`,
`build`, `capture_screenshot`, `get_heightmap`, `analyze_palette_region`.
Use `build` with a `commands` array for exact block states. Search/build tools
refresh block names and legal properties from the connected game, including
modded blocks. Reconnecting to a different server replaces that catalog.
`capture_screenshot` returns standard MCP `ImageContent`, plus JSON metadata.
Region scans return JSON with a palette and run-length encoded block indices
in Y/Z/X traversal order. Unknown client chunks are rejected, never treated as
empty space. Screenshots show the current camera; automatic room cameras,
roof hiding, furniture semantics and NPC seating are separate future work.
Bundled upstream furniture templates describe decorative vanilla constructions;
they are not a catalog of functional mod furniture.

## Verification

```sh
cd mcp-server
uv sync --extra dev --frozen
uv run --frozen pytest
```

The graphical integration reuses the existing Frontier v3 disposable pilot,
its normal client connection, 20 FPS limit, frame capture and cleanup:

```sh
# Run from the Pale Mirror project root. The process root must already exist.
mkdir -p build/vibecraft-processes
FRONTIER_V3_PILOT_GRAYBOX_DATAPACK_DIRECTORY=/absolute/path/to/pack/datapacks/pale-mirror-graybox \
FRONTIER_V3_PILOT_PORT=25585 \
FRONTIER_V3_NATIVE_PROCESS_ROOT="$PWD/build/vibecraft-processes" \
  scripts/with-private-xvfb.sh tools/vibecraft/run-native-smoke.sh
```

It injects this built JAR only into that disposable client. The MCP probe checks
authentication rejection, reconnect, actual mod registry/state validation,
command placement and client observation of an oriented Pale Mirror block,
unloaded-chunk rejection, and PNG `ImageContent`. A block written through MCP
releases the pilot's terminal barrier. Evidence is under `build/vibecraft-native-*`.
This proves the bridge's tested flow, not dining behavior or full-pack visual quality.

Verified 2026-11-07: client build/package inspection, 165 Python tests and project
guardrails passed. The real-client MCP probe passed all eight checks above on
the disposable seed-41 world `v3-disposable_vibecraft_bridge-fa1dccf9`;
`build/vibecraft-native-20261107-231342/` retains the MCP receipt, palette scan,
reviewed framebuffer PNG and the existing pilot manifest. Client, server and
private display exited after normal save. The tested and packaged JAR SHA-256 is
`c4a3bbeae1cabe1221cccebf007227111f7a0d954a42d95b55008e417b350712`.
The native placement used `pale_mirror:production_bench[facing=west]`;
WorldEdit operations and Handcrafted-specific interactions were not part of this check.
