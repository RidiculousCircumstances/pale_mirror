package io.farfrontier.palemirror.visuals.genesis;

import io.farfrontier.palemirror.api.AuthoredSettlementSitePlan;
import io.farfrontier.palemirror.api.VisualBounds;
import java.util.List;
import net.minecraft.world.level.block.Blocks;

/** Builds one continuous, softly blended terrain datum beneath an authored township. */
final class SettlementTerrainCompiler {
    static final int CORE_RELIEF = 1;
    static final int OUTER_BLEND = 14;

    private SettlementTerrainCompiler() { }

    static void compile(AuthoredSettlementSitePlan settlement, SettlementGenesisCompiler.Sink sink) {
        List<VisualBounds> areas = settlement.managedArea().areas();
        int minimumX = areas.stream().mapToInt(value -> value.min().x()).min().orElseThrow() - OUTER_BLEND;
        int maximumX = areas.stream().mapToInt(value -> value.max().x()).max().orElseThrow() + OUTER_BLEND;
        int minimumZ = areas.stream().mapToInt(value -> value.min().z()).min().orElseThrow() - OUTER_BLEND;
        int maximumZ = areas.stream().mapToInt(value -> value.max().z()).max().orElseThrow() + OUTER_BLEND;
        int datum = medianFoundationY(settlement);
        for (int z = minimumZ; z <= maximumZ; z++) {
            for (int x = minimumX; x <= maximumX; x++) {
                int distance = distanceFrom(areas, x, z);
                if (distance > OUTER_BLEND) continue;
                // Deep cracks are filled inside the inhabited union, while
                // one block of local relief survives. Beyond it, the allowed
                // deviation grows one block per column into untouched land.
                sink.blend(x, z, datum, Blocks.GRASS_BLOCK.defaultBlockState(),
                        Blocks.DIRT.defaultBlockState(), CORE_RELIEF + distance);
                sink.cleanup(x, z, datum);
            }
        }
    }

    private static int medianFoundationY(AuthoredSettlementSitePlan settlement) {
        int[] values = settlement.foundations().stream().mapToInt(value -> value.targetY()).sorted().toArray();
        return values[values.length / 2];
    }

    static int distanceFrom(List<VisualBounds> areas, int x, int z) {
        int nearest = Integer.MAX_VALUE;
        for (VisualBounds area : areas) {
            int dx = Math.max(area.min().x() - x, x - area.max().x());
            int dz = Math.max(area.min().z() - z, z - area.max().z());
            nearest = Math.min(nearest, Math.max(0, Math.max(dx, dz)));
            if (nearest == 0) return 0;
        }
        return nearest;
    }

    static int envelopeColumnCount(List<VisualBounds> areas) {
        int minimumX = areas.stream().mapToInt(value -> value.min().x()).min().orElseThrow() - OUTER_BLEND;
        int maximumX = areas.stream().mapToInt(value -> value.max().x()).max().orElseThrow() + OUTER_BLEND;
        int minimumZ = areas.stream().mapToInt(value -> value.min().z()).min().orElseThrow() - OUTER_BLEND;
        int maximumZ = areas.stream().mapToInt(value -> value.max().z()).max().orElseThrow() + OUTER_BLEND;
        int columns = 0;
        for (int z = minimumZ; z <= maximumZ; z++) {
            for (int x = minimumX; x <= maximumX; x++) {
                if (distanceFrom(areas, x, z) <= OUTER_BLEND) columns++;
            }
        }
        return columns;
    }
}
