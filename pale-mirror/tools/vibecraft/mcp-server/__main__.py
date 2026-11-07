"""Main entry point for VibeCraft MCP server"""

from src.vibecraft.server import main
import asyncio

if __name__ == "__main__":
    asyncio.run(main())
