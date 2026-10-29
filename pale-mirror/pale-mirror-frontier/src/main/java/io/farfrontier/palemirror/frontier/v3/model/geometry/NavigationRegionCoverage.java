package io.farfrontier.palemirror.frontier.v3.model.geometry;

import io.farfrontier.palemirror.frontier.v3.model.WorldBounds;
import io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianGeometryRevision.Region;
import java.util.Set;

/** Bounded spatial rejection of optional positive geometry certificates. No block
 * observation, missing-knowledge assumption, route or execution authority. */
public final class NavigationRegionCoverage {
    private static final int MAX_SIDE = 320, MAX_REGIONS = 128;
    private NavigationRegionCoverage() { }
    public static boolean bounded(WorldBounds area) {
        if (area.width() > MAX_SIDE || area.depth() > MAX_SIDE) return false;
        long x = ((area.maxXExclusive() - 1) >> 4) - (area.minX() >> 4) + 1L;
        long z = ((area.maxZExclusive() - 1) >> 4) - (area.minZ() >> 4) + 1L;
        return x * z <= MAX_REGIONS;
    }
    public static WorldBounds expanded(WorldBounds area, int margin) {
        if (margin < 0 || margin > 8) throw new IllegalArgumentException("invalid geometry certificate halo");
        return new WorldBounds(Math.subtractExact(area.minX(), margin), Math.subtractExact(area.minZ(), margin),
                Math.addExact(area.width(), margin * 2), Math.addExact(area.depth(), margin * 2));
    }
    public static boolean intersects(Set<Region> regions, WorldBounds area) {
        if (!bounded(area)) return true; // No unbounded proof scan: use ordinary geometry.
        var indexed = regions instanceof ImmutableRegionMap.RegionKeys keys ? keys : null;
        for (int x = area.minX() >> 4; x <= (area.maxXExclusive() - 1) >> 4; x++)
            for (int z = area.minZ() >> 4; z <= (area.maxZExclusive() - 1) >> 4; z++)
                if (indexed != null ? indexed.containsRegion(x, z) : regions.contains(new Region(x, z))) return true;
        return false;
    }
}
