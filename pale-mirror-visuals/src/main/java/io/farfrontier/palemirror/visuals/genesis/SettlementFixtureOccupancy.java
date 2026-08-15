package io.farfrontier.palemirror.visuals.genesis;

import io.farfrontier.palemirror.api.AuthoredRegionSeed;
import io.farfrontier.palemirror.api.LinearFeaturePlan;
import io.farfrontier.palemirror.api.VisualBounds;
import io.farfrontier.palemirror.api.VisualPoint;
import io.farfrontier.palemirror.api.VisualPortKind;
import java.util.HashSet;
import java.util.Set;

/** Deterministic, region-global reservation for public-realm furniture. */
final class SettlementFixtureOccupancy {
    private final Set<Long> blocked = new HashSet<>();
    private final Set<Long> fixtures = new HashSet<>();
    private final Set<Long> authoredModules = new HashSet<>();

    static SettlementFixtureOccupancy forSettlement(AuthoredRegionSeed seed) {
        SettlementFixtureOccupancy result = new SettlementFixtureOccupancy();
        seed.settlementSite().modules().forEach(module -> {
            result.block(module.footprint(), 1);
            result.rememberAuthoredModule(module.footprint());
        });
        seed.settlementSite().circulation().forEach(feature -> result.block(feature, feature.width() / 2));
        seed.baselineRailNodes().forEach(point -> result.block(point.x(), point.z(), 1));
        seed.settlementSite().modules().forEach(module -> module.ports().stream()
                .filter(port -> port.kind() == VisualPortKind.PUBLIC_ENTRANCE)
                .forEach(port -> result.blockEntranceApron(port.position(), port.outwardQuarterTurns(),
                        module.footprint())));
        return result;
    }

    boolean reserve(int x, int z, int clearance) {
        if (!freeOfBlocked(x, z, 0) || !freeOfFixtures(x, z, clearance)) return false;
        reserveArea(x, z, clearance);
        return true;
    }

    boolean insideAuthoredModule(int x, int z) { return authoredModules.contains(key(x, z)); }

    private void rememberAuthoredModule(VisualBounds bounds) {
        for (int x = bounds.min().x(); x <= bounds.max().x(); x++) {
            for (int z = bounds.min().z(); z <= bounds.max().z(); z++) authoredModules.add(key(x, z));
        }
    }

    boolean reserveWithCompanion(int x, int z, int companionX, int companionZ, int clearance) {
        if (!freeOfBlocked(x, z, 0) || !freeOfBlocked(companionX, companionZ, 0)
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

    private boolean freeOfBlocked(int x, int z, int clearance) {
        for (int dx = -clearance; dx <= clearance; dx++) for (int dz = -clearance; dz <= clearance; dz++) {
            if (blocked.contains(key(x + dx, z + dz))) return false;
        }
        return true;
    }

    private void blockEntranceApron(VisualPoint entrance, int outwardQuarterTurns, VisualBounds footprint) {
        var outward = SettlementPublicRealm.direction(outwardQuarterTurns);
        var tangent = outward.getClockWise();
        int exterior = switch (Math.floorMod(outwardQuarterTurns, 4)) {
            case 0 -> footprint.max().x() - entrance.x();
            case 1 -> footprint.max().z() - entrance.z();
            case 2 -> entrance.x() - footprint.min().x();
            default -> entrance.z() - footprint.min().z();
        };
        for (int depth = 1; depth <= Math.max(3, exterior + 2); depth++) {
            int halfWidth = depth <= 2 ? 2 : 1;
            for (int across = -halfWidth; across <= halfWidth; across++) {
                block(entrance.x() + outward.getStepX() * depth + tangent.getStepX() * across,
                        entrance.z() + outward.getStepZ() * depth + tangent.getStepZ() * across, 0);
            }
        }
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
