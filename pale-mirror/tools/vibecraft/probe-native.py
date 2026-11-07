#!/usr/bin/env python3
"""Probe the real MCP server while the existing disposable pilot owns Minecraft."""
import asyncio
import base64
import json
import os
from pathlib import Path
import sys
import time

from mcp import ClientSession, StdioServerParameters
from mcp.client.stdio import stdio_client
from vibecraft.client_bridge import ClientBridge
from vibecraft.config import VibeCraftConfig
from vibecraft.exceptions import ClientBridgeProtocolError


async def main():
    tool = Path(__file__).resolve().parent
    output = Path(sys.argv[1]).resolve()
    output.mkdir(parents=True, exist_ok=True)
    config = VibeCraftConfig(client_port=18766, client_token="vibecraft-disposable-smoke",
                             worldedit_mode="off", enable_version_detection=False)
    bridge = ClientBridge(config)
    deadline = time.monotonic() + 240
    while time.monotonic() < deadline:
        try:
            bridge.reset_backoff()
            if bridge.test_connection():
                context = bridge.get_player_context()
                position = context.get("position", {})
                if context.get("dimension") == "minecraft:overworld" and abs(position.get("x", 1000)) < 10:
                    region = bridge.scan_region(-1, 100, -1, 3, 101, 1, True)
                    if "minecraft:stone" in region.get("palette", []):
                        break
        except Exception:
            pass
        await asyncio.sleep(1)
    else:
        raise RuntimeError("Disposable native client did not reach the declared test platform")

    assert bridge.get_capabilities()["block_registry"]
    wrong = ClientBridge(VibeCraftConfig(client_port=18766, client_token="incorrect"))
    assert not wrong.test_connection(), "Wrong token must be rejected"
    wrong.close()
    bridge.close()
    assert bridge.test_connection(), "Bridge must reconnect after disconnect"
    acknowledgement = bridge.execute_command("/time query daytime")
    assert "no server acknowledgement" not in acknowledgement, acknowledgement

    env = dict(os.environ, VIBECRAFT_CLIENT_PORT="18766",
               VIBECRAFT_CLIENT_TOKEN="vibecraft-disposable-smoke",
               VIBECRAFT_WORLDEDIT_MODE="off", VIBECRAFT_ENABLE_VERSION_DETECTION="false")
    params = StdioServerParameters(command=str(tool / "run-mcp.sh"), env=env)
    checks = ["authentication rejection", "bridge reconnect", "server command feedback capture"]
    async with stdio_client(params) as (read, write):
        async with ClientSession(read, write) as session:
            await session.initialize()
            listed = await session.list_tools()
            assert {"build", "scan_region", "capture_screenshot"} <= {t.name for t in listed.tools}
            search = await session.call_tool("search_minecraft_item", {"query": "pale_mirror:production_bench"})
            assert "pale_mirror:production_bench" in search.content[0].text
            assert "west" in search.content[0].text
            checks.append("actual mod registry and states over MCP")

            bad = await session.call_tool("build", {"commands": [
                "/setblock 0 101 0 pale_mirror:production_bench[facing=up]"]})
            assert "Invalid block state" in bad.content[0].text
            checks.append("invalid mod state rejected before mutation")

            built = await session.call_tool("build", {"description": "NeoForge bridge native probe", "commands": [
                "/setblock 0 101 0 pale_mirror:production_bench[facing=west]",
                "/setblock 1 101 0 minecraft:oak_stairs[facing=east]",
                "/tp @s 0 102 6 facing 0 101 0"]})
            assert "❌" not in built.content[0].text, built.content[0].text
            deadline = time.monotonic() + 15
            while True:
                scan = await session.call_tool("scan_region", {
                    "x1": 0, "y1": 101, "z1": 0, "x2": 1, "y2": 101, "z2": 0,
                    "include_states": True})
                payload = json.loads(scan.content[0].text)
                if any("pale_mirror:production_bench" in state and "facing=west" in state
                       for state in payload["palette"]):
                    break
                if time.monotonic() > deadline:
                    raise AssertionError("Native block placement never reached client scan")
                await asyncio.sleep(.2)
            (output / "scan.json").write_text(json.dumps(payload, indent=2) + "\n")
            checks.append("MCP command -> server block -> client state scan")

            unknown = await session.call_tool("scan_region", {
                "x1": 200000, "y1": 100, "z1": 200000,
                "x2": 200001, "y2": 101, "z2": 200001})
            assert "chunk not loaded" in unknown.content[0].text
            checks.append("unloaded chunk rejected instead of reported as air")

            shot = await session.call_tool("capture_screenshot", {"max_width": 960, "max_height": 540})
            image = next(part for part in shot.content if part.type == "image")
            png = base64.b64decode(image.data)
            assert png.startswith(b"\x89PNG\r\n\x1a\n")
            (output / "minecraft.png").write_bytes(png)
            metadata = json.loads(shot.content[0].text)
            assert 0 < metadata["width"] <= 960 and 0 < metadata["height"] <= 540
            checks.append("actual framebuffer as MCP ImageContent")

            # Ordinary server command releases the existing pilot's terminal barrier.
            done = await session.call_tool("build", {"commands": ["/setblock 3 101 0 minecraft:gold_block"]})
            assert "❌" not in done.content[0].text
    bridge.close()
    (output / "mcp-receipt.json").write_text(json.dumps({"status": "passed", "checks": checks}, indent=2) + "\n")
    print(json.dumps({"status": "passed", "checks": checks}))


if __name__ == "__main__":
    asyncio.run(main())
