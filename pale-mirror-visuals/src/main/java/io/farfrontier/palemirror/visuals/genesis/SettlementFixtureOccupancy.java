package io.farfrontier.palemirror.visuals.genesis;

import io.farfrontier.palemirror.api.AuthoredRegionSeed;
import io.farfrontier.palemirror.api.LinearFeaturePlan;
import io.farfrontier.palemirror.api.VisualBounds;
import io.farfrontier.palemirror.api.VisualPoint;
import java.util.HashSet;
import java.util.Set;

/** Deterministic, region-global reservation for public-realm furniture. */
final class SettlementFixtureOccupancy {
    private final Set<Long> blocked = new HashSet<>();
    private final Set<Long> fixtures = new HashSet<>();

    static SettlementFixtureOccupancy forSettlement(AuthoredRegionSeed seed) {
        SettlementFixtureOccupancy result = new SettlementFixtureOccupancy();
        seed.settlementSite().modules().forEach(module -> result.block(module.footprint(), 1));
        seed.settlementSite().circulation().forEach(feature -> result.block(feature, feature.width() / 2));
        seed.baselineRailNodes().forEach(point -> result.block(point.x(), point.z(), 1));
        return result;
    }

    boolean reserve(int x, int z, int clearance) {
        if (blocked.contains(key(x, z)) || !freeOfFixtures(x, z, clearance)) return false;
        reserveArea(x, z, clearance);
        return true;
    }

    boolean reserveWithCompanion(int x, int z, int companionX, int companionZ, int clearance) {
        if (blocked.contains(key(x, z)) || blocked.contains(key(companionX, companionZ))
                || !freeOfFixtures(x, z, clearance)) return false;
        reserveArea(x, z, clearance);
        fixtures.add(key(companionX, companionZ));
        return true;
    }

    private boolean freeOfFixtures(int x, int z, int clearance) {
        for (int dx = -clearance; dx <= clearance; dx++) for (int dz = -clearance; dz <= clearance; dz++) {
            if (fixtures.contains(key(x + dx, z + dz))) return false;
        }
        return true;
    }

    private void block(VisualBounds bounds, int margin) {
        for (int x = bounds.min().x() - margin; x <= bounds.max().x() + margin; x++) {
            for (int z = bounds.min().z() - margin; z <= bounds.max().z() + margin; z++) {
                blocked.add(key(x, z));
            }
        }
    }

    private void block(LinearFeaturePlan feature, int radius) {
        for (int segment = 1; segment < feature.nodes().size(); segment++) {
            for (VisualPoint point : raster(feature.nodes().get(segment - 1), feature.nodes().get(segment))) {
                block(point.x(), point.z(), radius);
            }
        }
    }

    private static java.util.List<VisualPoint> raster(VisualPoint from, VisualPoint to) {
        int dx = to.x() - from.x();
        int dz = to.z() - from.z();
        int dy = to.y() - from.y();
        int steps = Math.max(1, Math.max(Math.abs(dx), Math.abs(dz)));
        java.util.List<VisualPoint> result = new java.util.ArrayList<>(steps + 1);
        for (int step = 0; step <= steps; step++) {
            result.add(new VisualPoint(from.x() + dx * step / steps,
                    from.y() + dy * step / steps, from.z() + dz * step / steps));
        }
        return result;
    }

    private void block(int x, int z, int radius) {
        for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
            blocked.add(key(x + dx, z + dz));
        }
    }

    private void reserveArea(int x, int z, int radius) {
        for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
            fixtures.add(key(x + dx, z + dz));
        }
    }

    private static long key(int x, int z) {
        return (long) x << 32 ^ Integer.toUnsignedLong(z);
    }
}
