#!/usr/bin/env python3
"""
Test script to check how many tools the SSE server exposes
"""
import asyncio
import httpx
import json

async def test_sse_server():
    """Test the SSE server's tool list"""

    # Connect to SSE endpoint
    url = "http://127.0.0.1:8765/sse"

    async with httpx.AsyncClient(timeout=30.0) as client:
        print("🔍 Testing SSE server tool list...")
        print(f"📡 Connecting to {url}")

        try:
            # Make SSE connection
            async with client.stream("GET", url) as response:
                print(f"✅ Connected: {response.status_code}")

                # Read initial events
                async for line in response.aiter_lines():
                    if line.startswith("data:"):
                        data = line[5:].strip()
                        if data:
                            try:
                                msg = json.loads(data)
                                print(f"\n📨 Received message type: {msg.get('jsonrpc')}")

                                # Send list tools request
                                if msg.get("jsonrpc") == "2.0":
                                    print("Sending ListToolsRequest...")
                                    break
                            except json.JSONDecodeError:
                                pass

                # Now send list tools request via POST
                session_id = "test123"
                post_url = f"http://127.0.0.1:8765/messages/?session_id={session_id}"

                list_tools_request = {
                    "jsonrpc": "2.0",
                    "id": 1,
                    "method": "tools/list",
                    "params": {}
                }

                print(f"\n📤 Sending tools/list request to {post_url}")
                post_response = await client.post(post_url, json=list_tools_request)
                print(f"✅ Response status: {post_response.status_code}")

                # The response comes via SSE, so we need to read from the stream
                # For simplicity, let's just use the direct approach

        except Exception as e:
            print(f"❌ Error: {e}")
            import traceback
            traceback.print_exc()

# Simpler approach: just import and call the function directly
async def test_direct():
    """Test by calling list_tools directly"""
    import sys
    sys.path.insert(0, "src")

    from vibecraft import server as s

    print("🔍 Testing list_tools function directly...")
    tools = await s.list_tools()

    print(f"\n✅ Total tools: {len(tools)}")
    print("\n📋 All tools:")
    for i, tool in enumerate(tools, 1):
        print(f"  {i}. {tool.name}")

    return tools

if __name__ == "__main__":
    print("="*60)
    print("VibeCraft SSE Server Tool Test")
    print("="*60)

    # Test direct function call
    tools = asyncio.run(test_direct())

    print(f"\n{'='*60}")
    print(f"✅ RESULT: {len(tools)} tools available")
    print(f"{'='*60}")
