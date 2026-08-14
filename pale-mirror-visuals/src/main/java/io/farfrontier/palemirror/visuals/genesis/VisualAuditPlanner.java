package io.farfrontier.palemirror.visuals.genesis;

import io.farfrontier.palemirror.api.AuthoredBuildingPlan;
import io.farfrontier.palemirror.api.AuthoredMineSitePlan;
import io.farfrontier.palemirror.api.AuthoredRegionSeed;
import io.farfrontier.palemirror.api.LinearFeatureKind;
import io.farfrontier.palemirror.api.LinearFeaturePlan;
import io.farfrontier.palemirror.api.OpenSpaceKind;
import io.farfrontier.palemirror.api.SettlementBuildingCategory;
import io.farfrontier.palemirror.api.VisualAuditView;
import io.farfrontier.palemirror.api.VisualBounds;
import io.farfrontier.palemirror.api.VisualPoint;
import io.farfrontier.palemirror.api.VisualPortKind;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Semantic camera grammar shared by every authored-region visual audit. */
public final class VisualAuditPlanner {
    private VisualAuditPlanner() { }

    public static List<VisualAuditView> views(AuthoredRegionSeed region) {
        List<VisualAuditView> result = new ArrayList<>();
        settlement(region, result);
        mine(region, region.primaryMineSite(), "primary_mine", result);
        mine(region, region.alternateMineSite(), "alternate_mine", result);
        railway(region, result);
        return List.copyOf(result);
    }

    private static void settlement(AuthoredRegionSeed region, List<VisualAuditView> result) {
        VisualBounds bounds = region.settlementSite().bounds();
        VisualPoint center = center(bounds);
        int radius = Math.max(90, Math.max(bounds.max().x() - bounds.min().x(),
                bounds.max().z() - bounds.min().z()) / 2 + 18);
        int diagonalY = bounds.max().y() + 54;
        add(result, region, "settlement/aerial_top", "settlement", region.planId(),
                new VisualPoint(center.x(), bounds.max().y() + 140, center.z()), center);
        add(result, region, "settlement/aerial_south_east", "settlement", region.planId(),
                new VisualPoint(center.x() + radius, diagonalY, center.z() + radius), center);
        add(result, region, "settlement/aerial_south_west", "settlement", region.planId(),
                new VisualPoint(center.x() - radius, diagonalY, center.z() + radius), center);
        add(result, region, "settlement/aerial_north_east", "settlement", region.planId(),
                new VisualPoint(center.x() + radius, diagonalY, center.z() - radius), center);
        add(result, region, "settlement/aerial_north_west", "settlement", region.planId(),
                new VisualPoint(center.x() - radius, diagonalY, center.z() - radius), center);

        VisualPoint gate = region.freightGate();
        VisualPoint depot = region.receivingDepot();
        add(result, region, "settlement/freight_gate", "settlement", region.planId(),
                away(gate, depot, 12), raised(depot, 3));
        add(result, region, "settlement/receiving_depot", "settlement", region.planId(),
                away(depot, gate, 11), raised(depot, 3));
        openSpace(region, OpenSpaceKind.CIVIC_GREEN, "settlement/civic_green", result);
        openSpace(region, OpenSpaceKind.MARKET_SQUARE, "settlement/market_square", result);
        openSpace(region, OpenSpaceKind.INDUSTRIAL_YARD, "settlement/industrial_yard", result);
        building(region, SettlementBuildingCategory.HOUSING, "settlement/residential_lane", result);

        region.settlementSite().defences().stream().filter(value -> value.kind() == LinearFeatureKind.PALISADE
                        || value.kind() == LinearFeatureKind.PALISADE_GATE)
                .flatMap(feature -> segments(feature).stream()).max(Comparator.comparingLong(Segment::lengthSquared))
                .ifPresent(segment -> {
                    VisualPoint wall = midpoint(segment.from(), segment.to());
                    add(result, region, "settlement/perimeter", "settlement", region.planId(),
                            toward(wall, center, 9), raised(wall, 2));
                });
    }

    private static void openSpace(AuthoredRegionSeed region, OpenSpaceKind kind, String id,
                                  List<VisualAuditView> result) {
        region.settlementSite().openSpaces().stream().filter(value -> value.kind() == kind).findFirst()
                .ifPresent(space -> {
                    VisualPoint focus = center(space.bounds());
                    VisualPoint camera = new VisualPoint(focus.x() + Math.min(10,
                            Math.max(6, (space.bounds().max().x() - space.bounds().min().x()) / 2)),
                            focus.y(), focus.z());
                    add(result, region, id, "settlement", region.planId(), camera, raised(focus, 2));
                });
    }

    private static void building(AuthoredRegionSeed region, SettlementBuildingCategory category, String id,
                                 List<VisualAuditView> result) {
        region.settlementSite().buildings().stream().filter(value -> value.category() == category).findFirst()
                .flatMap(building -> building.modules().stream().findFirst())
                .flatMap(module -> module.ports().stream()
                        .filter(value -> value.kind() == VisualPortKind.PUBLIC_ENTRANCE).findFirst())
                .ifPresent(port -> {
                    VisualPoint entrance = port.position();
                    VisualPoint camera = local(entrance, 0, -9, 0, port.outwardQuarterTurns());
                    add(result, region, id, "settlement", region.planId(), camera, raised(entrance, 3));
                });
    }

    private static void mine(AuthoredRegionSeed region, AuthoredMineSitePlan mine, String prefix,
                             List<VisualAuditView> result) {
        VisualPoint center = center(mine.bounds());
        add(result, region, prefix + "/aerial", "mine", mine.siteId(),
                new VisualPoint(center.x(), mine.bounds().max().y() + 78, center.z()), center);
        add(result, region, prefix + "/arrival", "mine", mine.siteId(),
                local(mine.loadingEndpoint(), 0, -12, 2, mine.inwardQuarterTurns()),
                raised(mine.loadingEndpoint(), 3));
        add(result, region, prefix + "/portal", "mine", mine.siteId(),
                local(mine.portal(), 0, -11, 1, mine.inwardQuarterTurns()), raised(mine.portal(), 3));
        mine.surfaceBuildings().stream().filter(value -> value.category() == SettlementBuildingCategory.INDUSTRY)
                .findFirst().or(() -> mine.surfaceBuildings().stream().findFirst()).ifPresent(building ->
                        buildingView(region, mine, prefix + "/industrial_campus", building, result));
        VisualPoint controller = mine.controllerAnchor();
        add(result, region, prefix + "/controller_chamber", "mine", mine.siteId(),
                local(controller, 0, -7, 0, mine.inwardQuarterTurns()), raised(controller, 1));
    }

    private static void buildingView(AuthoredRegionSeed region, AuthoredMineSitePlan mine, String id,
                                     AuthoredBuildingPlan building, List<VisualAuditView> result) {
        var port = building.modules().stream().flatMap(module -> module.ports().stream())
                .filter(value -> value.kind() == VisualPortKind.PUBLIC_ENTRANCE).findFirst().orElse(null);
        if (port == null) return;
        add(result, region, id, "mine", mine.siteId(),
                local(port.position(), 0, -10, 0, port.outwardQuarterTurns()), raised(port.position(), 3));
    }

    private static void railway(AuthoredRegionSeed region, List<VisualAuditView> result) {
        List<VisualPoint> nodes = region.baselineRailNodes();
        int middle = nodes.size() / 2;
        routeView(region, result, "railway/departure", nodes.getFirst(), nodes.get(Math.min(4, nodes.size() - 1)));
        routeView(region, result, "railway/corridor", nodes.get(middle), nodes.get(Math.min(middle + 3, nodes.size() - 1)));
        routeView(region, result, "railway/arrival", nodes.getLast(), nodes.get(Math.max(0, nodes.size() - 5)));
    }

    private static void routeView(AuthoredRegionSeed region, List<VisualAuditView> result, String id,
                                  VisualPoint position, VisualPoint focus) {
        add(result, region, id, "railway", region.planId() + ":baseline_railway",
                raised(position, 3), raised(focus, 1));
    }

    private static void add(List<VisualAuditView> target, AuthoredRegionSeed region, String id,
                            String kind, String targetId, VisualPoint camera, VisualPoint focus) {
        double dx = focus.x() - camera.x();
        double dz = focus.z() - camera.z();
        double horizontal = Math.max(0.001D, Math.sqrt(dx * dx + dz * dz));
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) Math.toDegrees(Math.atan2(camera.y() + 1.62D - focus.y(), horizontal));
        target.add(new VisualAuditView(id, kind, targetId, region.dimensionId(), camera, yaw, pitch));
    }

    private static VisualPoint away(VisualPoint origin, VisualPoint toward, int distance) {
        int dx = Integer.signum(toward.x() - origin.x());
        int dz = Integer.signum(toward.z() - origin.z());
        return new VisualPoint(origin.x() - dx * distance, origin.y(), origin.z() - dz * distance);
    }

    private static VisualPoint toward(VisualPoint origin, VisualPoint target, int distance) {
        int dx = Integer.signum(target.x() - origin.x());
        int dz = Integer.signum(target.z() - origin.z());
        return new VisualPoint(origin.x() + dx * distance, origin.y(), origin.z() + dz * distance);
    }

    private static VisualPoint raised(VisualPoint value, int up) {
        return new VisualPoint(value.x(), value.y() + up, value.z());
    }

    private static VisualPoint center(VisualBounds bounds) {
        return new VisualPoint((bounds.min().x() + bounds.max().x()) / 2,
                (bounds.min().y() + bounds.max().y()) / 2,
                (bounds.min().z() + bounds.max().z()) / 2);
    }

    private static VisualPoint midpoint(VisualPoint first, VisualPoint second) {
        return new VisualPoint((first.x() + second.x()) / 2, (first.y() + second.y()) / 2,
                (first.z() + second.z()) / 2);
    }

    private static VisualPoint local(VisualPoint origin, int right, int inward, int up, int direction) {
        int dx = switch (Math.floorMod(direction, 4)) {
            case 0 -> inward; case 1 -> -right; case 2 -> -inward; default -> right;
        };
        int dz = switch (Math.floorMod(direction, 4)) {
            case 0 -> right; case 1 -> inward; case 2 -> -right; default -> -inward;
        };
        return new VisualPoint(origin.x() + dx, origin.y() + up, origin.z() + dz);
    }

    private static List<Segment> segments(LinearFeaturePlan feature) {
        List<Segment> result = new ArrayList<>();
        for (int index = 1; index < feature.nodes().size(); index++) {
            result.add(new Segment(feature.nodes().get(index - 1), feature.nodes().get(index)));
        }
        return result;
    }

    private record Segment(VisualPoint from, VisualPoint to) {
        long lengthSquared() {
            long dx = (long) to.x() - from.x();
            long dz = (long) to.z() - from.z();
            return dx * dx + dz * dz;
        }
    }
}
