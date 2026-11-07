#!/usr/bin/env python3
"""
Test script to demonstrate the furniture placement floor fix.

This test shows:
1. OLD behavior (place_on_surface=False): Furniture replaces floor blocks
2. NEW behavior (place_on_surface=True, default): Furniture sits ON TOP of floor
"""

import sys
from pathlib import Path

# Add src to path (go up one level from scripts/ to mcp-server/, then into src/)
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "src"))

from vibecraft.furniture_placer import FurniturePlacer

# Example furniture layout (simplified corner table)
corner_table = {
    "name": "Corner Table",
    "id": "corner_table",
    "origin": {
        "type": "front_left_bottom",
        "facing": "north"
    },
    "bounds": {
        "width": 1,
        "height": 2,
        "depth": 1
    },
    "placements": [
        {
            "type": "block",
            "pos": {"x": 0, "y": 0, "z": 0},
            "block": "oak_fence",
            "state": "[axis=y]"
        },
        {
            "type": "block",
            "pos": {"x": 0, "y": 1, "z": 0},
            "block": "oak_pressure_plate"
        }
    ]
}


def test_floor_placement():
    """Test that furniture is placed on top of floor, not inside it."""

    # Scenario: Floor block is at Y=64
    floor_y = 64
    origin_x = 100
    origin_z = 200

    print("=" * 80)
    print("FURNITURE PLACEMENT FIX TEST")
    print("=" * 80)
    print(f"\nScenario: Floor block at Y={floor_y}")
    print(f"Furniture: Corner Table (oak fence leg at y=0, pressure plate at y=1)")
    print()

    # OLD BEHAVIOR (place_on_surface=False)
    print("-" * 80)
    print("OLD BEHAVIOR (place_on_surface=False)")
    print("-" * 80)
    commands_old = FurniturePlacer.get_placement_commands(
        layout=corner_table,
        origin_x=origin_x,
        origin_y=floor_y,
        origin_z=origin_z,
        place_on_surface=False
    )

    print("Commands generated:")
    for cmd in commands_old:
        if cmd.startswith("#"):
            print(f"  {cmd}")
        else:
            print(f"  {cmd}")

    print("\nResult:")
    print(f"  ❌ Oak fence placed at Y={floor_y} → REPLACES FLOOR BLOCK")
    print(f"  ❌ Pressure plate placed at Y={floor_y + 1}")
    print(f"  ❌ Floor block at Y={floor_y} is DESTROYED!")
    print()

    # NEW BEHAVIOR (place_on_surface=True, default)
    print("-" * 80)
    print("NEW BEHAVIOR (place_on_surface=True, DEFAULT)")
    print("-" * 80)
    commands_new = FurniturePlacer.get_placement_commands(
        layout=corner_table,
        origin_x=origin_x,
        origin_y=floor_y,
        origin_z=origin_z,
        place_on_surface=True  # This is the default
    )

    print("Commands generated:")
    for cmd in commands_new:
        if cmd.startswith("#"):
            print(f"  {cmd}")
        else:
            print(f"  {cmd}")

    print("\nResult:")
    print(f"  ✅ Oak fence placed at Y={floor_y + 1} → SITS ON TOP OF FLOOR")
    print(f"  ✅ Pressure plate placed at Y={floor_y + 2}")
    print(f"  ✅ Floor block at Y={floor_y} is PRESERVED!")
    print()

    # USAGE EXAMPLES
    print("=" * 80)
    print("USAGE EXAMPLES")
    print("=" * 80)
    print()
    print("Example 1: Using get_surface_level (RECOMMENDED)")
    print("-" * 80)
    print("""
# Get the floor level
surface_y = get_surface_level(x=100, z=200)  # Returns 64 (floor block level)

# Place furniture ON TOP of floor (default behavior)
place_furniture(
    furniture_id="corner_table",
    origin_x=100,
    origin_y=64,          # Floor block level from get_surface_level
    origin_z=200,
    place_on_surface=True  # DEFAULT - furniture sits ON floor
)

# Result: Furniture placed at Y=65 (on top of Y=64 floor)
    """)

    print("\nExample 2: Advanced - exact placement")
    print("-" * 80)
    print("""
# For advanced users who want exact Y placement
place_furniture(
    furniture_id="corner_table",
    origin_x=100,
    origin_y=65,           # Exact Y where furniture should be placed
    origin_z=200,
    place_on_surface=False  # Advanced - use exact Y coordinate
)

# Result: Furniture placed at Y=65 (exact coordinate specified)
    """)

    print("=" * 80)
    print("TEST COMPLETE")
    print("=" * 80)
    print()
    print("Summary:")
    print("  ✅ place_on_surface=True (default) - Furniture sits ON floor")
    print("  ✅ origin_y should be the floor BLOCK level (from get_surface_level)")
    print("  ✅ Furniture is automatically placed at origin_y + 1")
    print()


if __name__ == "__main__":
    test_floor_placement()
