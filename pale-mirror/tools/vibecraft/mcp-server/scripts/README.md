# MCP Server Scripts

This directory contains **manual test scripts and utility scripts** that are NOT part of the automated pytest test suite.

## Purpose

These scripts are for:
- Manual testing and debugging during development
- One-off exploratory analysis
- Developer utilities

## Files

- `test_search.py` - Manual test script for Minecraft item search (now superseded by `tests/test_minecraft_item_search.py`)
- `test_furniture_placement_fix.py` - Manual furniture placement verification script
- `test_sse_tools.py` - Manual SSE (Server-Sent Events) testing script

## Running Manual Scripts

These scripts can be run directly:

```bash
cd mcp-server
python3 scripts/test_search.py
python3 scripts/test_furniture_placement_fix.py
python3 scripts/test_sse_tools.py
```

## Automated Tests

For **automated testing** (pytest), see the `tests/` directory:

```bash
cd mcp-server
pytest tests/
```

---

**Note**: The review comment about "manual tests bypass pytest" has been addressed:
- Proper pytest tests are now in `tests/` directory
- Manual scripts have been moved here to `scripts/` directory for clarity
- `pytest` will now discover and run tests in `tests/` directory
