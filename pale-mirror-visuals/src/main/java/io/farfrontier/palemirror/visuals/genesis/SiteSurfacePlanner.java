package io.farfrontier.palemirror.visuals.genesis;

import io.farfrontier.palemirror.api.AuthoredOpenSpacePlan;
import io.farfrontier.palemirror.api.DevelopmentReservation;
import io.farfrontier.palemirror.api.LinearFeatureKind;
import io.farfrontier.palemirror.api.LinearFeaturePlan;
import io.farfrontier.palemirror.api.SettlementFoundationPlan;
import io.farfrontier.palemirror.api.SiteSurfaceColumn;
import io.farfrontier.palemirror.api.SiteSurfacePlan;
import io.farfrontier.palemirror.api.SiteSurfaceUse;
import io.farfrontier.palemirror.api.VisualPoint;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Compiles all authored vertical datums before chunk slicing; no runtime heightmap participates. */
final class SiteSurfacePlanner {
    private SiteSurfacePlanner() { }

    static SiteSurfacePlan settlement(List<SettlementFoundationPlan> foundations,
                                      List<AuthoredOpenSpacePlan> openSpaces,
                                      List<LinearFeaturePlan> circulation,
                                      List<LinearFeaturePlan> defences,
                                      List<DevelopmentReservation> reservations) {
        Map<Long, SiteSurfaceColumn> claims = new LinkedHashMap<>();
        for (SettlementFoundationPlan foundation : foundations) {
            // Only the actual authored footprint is a hard foundation. The
            // catalog apron is a graded transition allowance, not a second
            // exposed stone platform around every imported building.
            for (int x = foundation.footprint().min().x();
                 x <= foundation.footprint().max().x(); x++) {
                for (int z = foundation.footprint().min().z();
                     z <= foundation.footprint().max().z(); z++) {
                    claim(claims, new SiteSurfaceColumn(x, z, foundation.targetY() + 1,
                            SiteSurfaceUse.FOUNDATION, foundation.id()));
                }
            }
        }
        for (AuthoredOpenSpacePlan space : openSpaces) {
            for (int x = space.bounds().min().x(); x <= space.bounds().max().x(); x++) {
                for (int z = space.bounds().min().z(); z <= space.bounds().max().z(); z++) {
                    claim(claims, new SiteSurfaceColumn(x, z, space.bounds().min().y() + 1,
                            space.kind() == io.farfrontier.palemirror.api.OpenSpaceKind.MARKET_SQUARE
                                    ? SiteSurfaceUse.PLAZA : SiteSurfaceUse.OPEN_SPACE,
                            "open_space:" + space.id()));
                }
            }
        }
        circulation.forEach(feature -> line(claims, feature, use(feature.kind()), "route:" + feature.id()));
        defences.forEach(feature -> line(claims, feature,
                feature.kind() == LinearFeatureKind.PALISADE_GATE ? SiteSurfaceUse.GATE
                        : SiteSurfaceUse.PERIMETER, "perimeter:" + feature.id()));
        // A future parcel is planning data, not physical geometry. It acquires
        // surface ownership only when a staged project is actually compiled.
        return new SiteSurfacePlan(claims.values().stream().sorted(Comparator.comparingInt(SiteSurfaceColumn::z)
                .thenComparingInt(SiteSurfaceColumn::x)).toList());
    }

    private static void line(Map<Long, SiteSurfaceColumn> claims, LinearFeaturePlan feature,
                             SiteSurfaceUse use, String owner) {
        int radius = feature.kind() == LinearFeatureKind.PALISADE_GATE ? 7
                : feature.kind() == LinearFeatureKind.STREET || feature.kind() == LinearFeatureKind.FREIGHT_ROAD
                ? feature.width() / 2 + 3 : feature.width() / 2;
        for (int segment = 1; segment < feature.nodes().size(); segment++) {
            for (VisualPoint point : raster(feature.nodes().get(segment - 1), feature.nodes().get(segment))) {
                for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
                    claim(claims, new SiteSurfaceColumn(point.x() + dx, point.z() + dz, point.y() + 1,
                            use, owner));
                }
            }
        }
    }

    private static void claim(Map<Long, SiteSurfaceColumn> claims, SiteSurfaceColumn value) {
        long coordinate = key(value.x(), value.z());
        SiteSurfaceColumn prior = claims.get(coordinate);
        if (prior == null) {
            claims.put(coordinate, value);
            return;
        }
        // Intersections are compiled into one explicit public-realm owner.
        // The more specific semantic claim owns both the column and its datum;
        // downstream compilers never re-query or average the terrain.
        if (priority(value.use()) >= priority(prior.use())) claims.put(coordinate, value);
    }

    private static long key(int x, int z) { return ((long) x << 32) ^ (z & 0xffffffffL); }

    private static int priority(SiteSurfaceUse use) {
        return switch (use) {
            case CLEARANCE -> 0;
            case OPEN_SPACE -> 1;
            case PLAZA, SIDEWALK -> 2;
            case ROAD, BRIDGE -> 3;
            case FOUNDATION -> 4;
            case PERIMETER -> 5;
            case GATE -> 6;
        };
    }

    private static SiteSurfaceUse use(LinearFeatureKind kind) {
        return switch (kind) {
            case BRIDGE -> SiteSurfaceUse.BRIDGE;
            case SIDEWALK, FOOTPATH, STAIRS -> SiteSurfaceUse.SIDEWALK;
            case PLAZA -> SiteSurfaceUse.PLAZA;
            default -> SiteSurfaceUse.ROAD;
        };
    }

    private static List<VisualPoint> raster(VisualPoint from, VisualPoint to) {
        int dx = to.x() - from.x();
        int dy = to.y() - from.y();
        int dz = to.z() - from.z();
        int steps = Math.max(1, Math.max(Math.abs(dx), Math.abs(dz)));
        java.util.ArrayList<VisualPoint> result = new java.util.ArrayList<>(steps + 1);
        for (int step = 0; step <= steps; step++) result.add(new VisualPoint(from.x() + dx * step / steps,
                from.y() + dy * step / steps, from.z() + dz * step / steps));
        return result;
    }
}
