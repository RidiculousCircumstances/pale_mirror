package io.farfrontier.palemirror.frontier.v3.model;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Finite, provider-compiled work positions for one four-by-four infection cell.
 *
 * <p>The cell origin remains only a field index. A service planner selects one of these ordered
 * typed support surfaces while it compiles a retained work corridor, then persists that result in
 * the service-work aggregate. Minecraft navigation may neither substitute another surface nor
 * infer a new one after the work has been admitted.</p>
 */
public final class InfectionTreatmentWorksite {
    private InfectionTreatmentWorksite() { }

    public static List<SurfaceAnchor> candidates(FrontierBootstrap bootstrap, InfectionCell cell) {
        Objects.requireNonNull(bootstrap, "infection treatment bootstrap");
        Objects.requireNonNull(cell, "infection treatment cell");
        Set<BlockPosition> occupied = immutableOccupancy(bootstrap);
        BlockPosition origin = cell.originAtY(0);
        List<SurfaceAnchor> values = new ArrayList<>();
        for (int[] offset : List.of(new int[] { 1, 1 }, new int[] { 2, 1 }, new int[] { 1, 2 }, new int[] { 2, 2 })) {
            int x = Math.addExact(origin.x(), offset[0]), z = Math.addExact(origin.z(), offset[1]);
            SurfaceAnchor station = SurfaceAnchor.at(x, Math.addExact(bootstrap.terrain().supportYAt(x, z), 1), z);
            if (bootstrap.bounds().contains(station.support()) && clear(station, occupied)) values.add(station);
        }
        if (values.isEmpty()) throw new IllegalArgumentException("infection cell has no immutable treatment worksite: " + cell);
        return List.copyOf(values);
    }

    static Set<BlockPosition> immutableOccupancy(FrontierBootstrap bootstrap) {
        Set<BlockPosition> occupied = new LinkedHashSet<>();
        for (Settlement settlement : bootstrap.settlements()) {
            occupied.addAll(FrontierSettlementActorSlots.intactStructureOccupancy(bootstrap.terrain(), settlement.structures()));
        }
        occupied.addAll(FrontierGrayboxPlan.intactOrganOccupancy(bootstrap.hive().organs()));
        return Set.copyOf(occupied);
    }

    private static boolean clear(SurfaceAnchor station, Set<BlockPosition> occupied) {
        return !occupied.contains(station.support()) && !occupied.contains(station.support().offset(0, 1, 0))
                && !occupied.contains(station.support().offset(0, 2, 0));
    }
}
