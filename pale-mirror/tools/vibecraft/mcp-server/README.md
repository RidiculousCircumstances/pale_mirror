# VibeCraft MCP server — NeoForge adaptation

See [the adaptation README](../README.md) for the supported Minecraft/NeoForge
version, building the client, MCP stdio configuration and native verification.

This directory retains the Python server from amenti-labs/VibeCraft (MIT),
upstream commit `be5045f20027b60dd1d1a8604379c51ebf84e2f4`, with live mod registry
validation, MCP image output and NeoForge integration changes. The canonical
launcher is `../run-mcp.sh`; it uses this directory's `uv.lock`.

Run focused Python/protocol checks with `uv sync --extra dev --frozen` and
`uv run --frozen pytest`. These mocked checks supplement the real-client scenario;
they do not establish native Minecraft behavior by themselves.
