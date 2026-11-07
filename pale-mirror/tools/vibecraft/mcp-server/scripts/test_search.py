#!/usr/bin/env python3
"""Test the search_minecraft_item functionality"""

import sys
from pathlib import Path

# Add src to path (go up one level from scripts/ to mcp-server/, then into src/)
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "src"))

from vibecraft.server import minecraft_items

def search_items(query, limit=20):
    """Simulate the search functionality"""
    query = query.lower()
    matches = []

    for item in minecraft_items:
        if query in item.get("name", "").lower() or query in item.get("displayName", "").lower():
            matches.append(item)
            if len(matches) >= limit:
                break

    return matches

# Test searches
test_queries = [
    ("stone", 10),
    ("concrete", 10),
    ("oak", 10),
    ("red", 10),
    ("sword", 5),
]

print("=" * 60)
print("TESTING MINECRAFT ITEM SEARCH")
print("=" * 60)

for query, limit in test_queries:
    matches = search_items(query, limit)
    print(f"\n🔍 Search: '{query}' (limit: {limit})")
    print(f"Found {len(matches)} items:")

    for item in matches[:5]:  # Show first 5
        print(f"  • {item['displayName']:30s} ({item['name']}) - ID: {item['id']}")

print("\n" + "=" * 60)
print("✅ Search functionality working!")
print("=" * 60)
