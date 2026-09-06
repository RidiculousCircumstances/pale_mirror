"""Shared, non-destructive semantic-region helpers for Collector artist work."""

from __future__ import annotations

from typing import Any

from mathutils import Vector


def artist_region(protocol: dict[str, Any]) -> dict[str, Any]:
    region = protocol.get("artist_region")
    if not isinstance(region, dict) or region.get("id") != "leading_mantle_v03":
        raise ValueError("The active Collector session does not declare the leading-mantle artist region.")
    polygon = region.get("primary_polygon_px")
    if not isinstance(polygon, list) or len(polygon) < 3:
        raise ValueError("The leading-mantle artist region has no valid primary polygon.")
    if not all(isinstance(point, list) and len(point) == 2 and all(isinstance(value, int) for value in point) for point in polygon):
        raise ValueError("The leading-mantle artist polygon must contain source-image pixel pairs.")
    return region


def primary_world(trace: dict[str, Any], pixel: list[int] | tuple[int, int]) -> Vector:
    mapping = trace["primary_camera_mapping"]
    origin_x, origin_y = mapping["origin_pixel"]
    units = float(mapping["world_units_per_pixel"])
    return Vector(((int(pixel[0]) - int(origin_x)) * units, 0.0, (int(origin_y) - int(pixel[1])) * units))


def primary_polygon_world(protocol: dict[str, Any], trace: dict[str, Any]) -> list[Vector]:
    return [primary_world(trace, point) for point in artist_region(protocol)["primary_polygon_px"]]


def contains_primary_point(polygon: list[Vector], point: Vector) -> bool:
    """Return whether an X/Z primary projection lies inside a polygon.

    The selection is descriptive only. It never changes geometry or grants a
    broad deformation operation permission.
    """
    inside = False
    previous = polygon[-1]
    for current in polygon:
        crosses = (current.z > point.z) != (previous.z > point.z)
        if crosses:
            x_at_crossing = (previous.x - current.x) * (point.z - current.z) / (previous.z - current.z) + current.x
            if point.x < x_at_crossing:
                inside = not inside
        previous = current
    return inside
