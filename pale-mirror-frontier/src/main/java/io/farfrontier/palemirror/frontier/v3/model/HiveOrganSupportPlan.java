package io.farfrontier.palemirror.frontier.v3.model;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Immutable terrain-provider support for one seed nest's organs. */
public final class HiveOrganSupportPlan {
    private HiveOrganSupportPlan() { }

    /** Chooses one finished datum for all organs belonging to a seed nest. */
    public static int nestDeckY(TerrainSurfacePlan terrain, List<HiveOrgan> organs) {
        Objects.requireNonNull(terrain, "terrain");
        organs = List.copyOf(Objects.requireNonNull(organs, "hive organs"));
        if (organs.isEmpty()) throw new IllegalArgumentException("seed nest requires one organ");
        int highest = Integer.MIN_VALUE;
        for (HiveOrgan organ : organs) for (BlockPosition surface : baseSupportCells(organ)) {
            highest = Math.max(highest, terrain.supportYAt(surface.x(), surface.z()));
        }
        return Math.addExact(highest, 1);
    }

    /** Provider-owned hiveroot fill below actual bottom tissue cells. */
    public static Set<BlockPosition> foundationCells(TerrainSurfacePlan terrain, HiveOrgan organ) {
        Objects.requireNonNull(terrain, "terrain"); Objects.requireNonNull(organ, "hive organ");
        Set<BlockPosition> roots = new LinkedHashSet<>();
        for (BlockPosition surface : baseSupportCells(organ)) {
            int terrainY = terrain.supportYAt(surface.x(), surface.z());
            if (terrainY >= surface.y()) {
                throw new IllegalArgumentException("surveyed terrain occupies or exceeds hive organ tissue at " + surface);
            }
            for (int y = terrainY + 1; y < surface.y(); y++) roots.add(new BlockPosition(surface.x(), y, surface.z()));
        }
        return Set.copyOf(roots);
    }

    /** Bottom tissue, including a STORE's exact chest socket. */
    static Set<BlockPosition> baseSupportCells(HiveOrgan organ) {
        Objects.requireNonNull(organ, "hive organ");
        Set<BlockPosition> result = new LinkedHashSet<>();
        for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) {
            if (Math.abs(x) == 2 || Math.abs(z) == 2 || organ.containerId().isPresent() && x == 0 && z == 0) {
                result.add(organ.anchor().offset(x, 0, z));
            }
        }
        return Set.copyOf(result);
    }
}
