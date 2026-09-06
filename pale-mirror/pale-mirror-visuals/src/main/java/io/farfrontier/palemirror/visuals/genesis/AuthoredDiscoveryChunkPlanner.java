package io.farfrontier.palemirror.visuals.genesis;

import io.farfrontier.palemirror.api.AuthoredSettlementSitePlan;
import io.farfrontier.palemirror.api.VisualBounds;
import io.farfrontier.palemirror.api.VisualChunk;
import io.farfrontier.palemirror.api.VisualPoint;
import java.util.List;
import java.util.TreeSet;

/** Compiles the horizontal arrival zone used only for player knowledge, never physical ownership. */
final class AuthoredDiscoveryChunkPlanner {
    private static final long RAIL_APPROACH_DISTANCE_SQUARED = 64L * 64L;

    private AuthoredDiscoveryChunkPlanner() { }

    static List<VisualChunk> plan(AuthoredSettlementSitePlan settlement, List<VisualPoint> baselineRailNodes) {
        TreeSet<VisualChunk> chunks = new TreeSet<>();
        settlement.managedArea().areas().forEach(area -> addIntersecting(chunks, area));
        chunks.add(VisualChunk.containing(settlement.freightGate()));
        chunks.add(VisualChunk.containing(settlement.receivingDepot()));
        VisualPoint gate = settlement.freightGate();
        VisualPoint depot = settlement.receivingDepot();
        baselineRailNodes.stream().filter(point -> horizontalDistanceSquared(point, gate)
                        <= RAIL_APPROACH_DISTANCE_SQUARED
                        || horizontalDistanceSquared(point, depot) <= RAIL_APPROACH_DISTANCE_SQUARED)
                .map(VisualChunk::containing).forEach(chunks::add);
        return List.copyOf(chunks);
    }

    private static void addIntersecting(TreeSet<VisualChunk> target, VisualBounds bounds) {
        int minimumX = Math.floorDiv(bounds.min().x(), 16);
        int maximumX = Math.floorDiv(bounds.max().x(), 16);
        int minimumZ = Math.floorDiv(bounds.min().z(), 16);
        int maximumZ = Math.floorDiv(bounds.max().z(), 16);
        for (int z = minimumZ; z <= maximumZ; z++) {
            for (int x = minimumX; x <= maximumX; x++) target.add(new VisualChunk(x, z));
        }
    }

    private static long horizontalDistanceSquared(VisualPoint first, VisualPoint second) {
        long dx = first.x() - second.x();
        long dz = first.z() - second.z();
        return dx * dx + dz * dz;
    }
}
