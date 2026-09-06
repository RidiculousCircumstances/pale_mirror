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
        int radius = Math.min(72, Math.max(52, Math.max(bounds.max().x() - bounds.min().x(),
                bounds.max().z() - bounds.min().z()) / 3));
        int diagonalY = bounds.max().y() + 42;
        add(result, region, "settlement/aerial_top", "settlement", region.planId(),
                new VisualPoint(center.x() + 2, bounds.max().y() + 72, center.z() + 2), center);
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
                raised(away(gate, depot, 12), 3), raised(gate, 3));
        region.settlementSite().buildings().stream()
                .filter(value -> value.buildingId().equals("receiving_depot")).findFirst()
                .ifPresent(building -> buildingView(region, "settlement/receiving_depot", building, result));
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
                            raised(away(wall, center, 10), 5), raised(wall, 3));
                });
    }

    private static void openSpace(AuthoredRegionSeed region, OpenSpaceKind kind, String id,
                                  List<VisualAuditView> result) {
        region.settlementSite().openSpaces().stream().filter(value -> value.kind() == kind).findFirst()
                .ifPresent(space -> {
                    VisualPoint focus = center(space.bounds());
                    VisualPoint camera = openSpaceCamera(region, space.bounds());
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
                    VisualPoint camera = local(entrance, 0, 12, 3, port.outwardQuarterTurns());
                    add(result, region, id, "settlement", region.planId(), camera, raised(entrance, 3));
                });
    }

    private static void mine(AuthoredRegionSeed region, AuthoredMineSitePlan mine, String prefix,
                             List<VisualAuditView> result) {
        VisualPoint center = center(mine.bounds());
        add(result, region, prefix + "/aerial", "mine", mine.siteId(),
                new VisualPoint(center.x(), mine.bounds().max().y() + 78, center.z()), center);
        add(result, region, prefix + "/arrival", "mine", mine.siteId(),
                local(mine.loadingEndpoint(), 0, -18, 5, mine.inwardQuarterTurns()),
                raised(mine.loadingEndpoint(), 4));
        add(result, region, prefix + "/portal", "mine", mine.siteId(),
                // Judge the walkable threshold and portal-house facade from
                // player scale. The former high aim point framed only the
                // roof and could hide a buried or suspended entrance.
                local(mine.portal(), 0, -18, 3, mine.inwardQuarterTurns()), raised(mine.portal(), 2));
        mine.surfaceBuildings().stream().filter(value -> value.category() == SettlementBuildingCategory.INDUSTRY)
                .findFirst().or(() -> mine.surfaceBuildings().stream().findFirst()).ifPresent(building ->
                        buildingView(region, mine, prefix + "/industrial_campus", building, result));
        VisualPoint controller = mine.controllerAnchor();
        add(result, region, prefix + "/controller_chamber", "mine", mine.siteId(),
                // Module origins sit one block below their walkable floor. Keeping
                // spectator feet at origin Y puts the audit camera inside stone and
                // produces a misleading x-ray frame instead of the chamber.
                local(controller, 0, -7, 2, mine.inwardQuarterTurns()), raised(controller, 3));
    }

    private static void buildingView(AuthoredRegionSeed region, AuthoredMineSitePlan mine, String id,
                                     AuthoredBuildingPlan building, List<VisualAuditView> result) {
        var port = building.modules().stream().flatMap(module -> module.ports().stream())
                .filter(value -> value.kind() == VisualPortKind.PUBLIC_ENTRANCE).findFirst().orElse(null);
        if (port == null) return;
        add(result, region, id, "mine", mine.siteId(),
                local(port.position(), 0, 12, 3, port.outwardQuarterTurns()), raised(port.position(), 3));
    }

    private static void buildingView(AuthoredRegionSeed region, String id, AuthoredBuildingPlan building,
                                     List<VisualAuditView> result) {
        var port = building.modules().stream().flatMap(module -> module.ports().stream())
                .filter(value -> value.kind() == VisualPortKind.PUBLIC_ENTRANCE
                        || value.kind() == VisualPortKind.FREIGHT)
                .findFirst().orElse(null);
        if (port == null) return;
        add(result, region, id, "settlement", region.planId(),
                local(port.position(), 0, 12, 3, port.outwardQuarterTurns()), raised(port.position(), 3));
    }

    private static void railway(AuthoredRegionSeed region, List<VisualAuditView> result) {
        List<VisualPoint> nodes = region.baselineRailNodes();
        int middle = nodes.size() / 2;
        int sightline = Math.min(12, nodes.size() - 1);
        routeView(region, result, "railway/departure", nodes.getFirst(), nodes.get(sightline));
        routeView(region, result, "railway/corridor", nodes.get(middle),
                nodes.get(Math.min(middle + sightline, nodes.size() - 1)));
        routeView(region, result, "railway/arrival", nodes.getLast(),
                nodes.get(Math.max(0, nodes.size() - 1 - sightline)));
    }

    private static void routeView(AuthoredRegionSeed region, List<VisualAuditView> result, String id,
                                  VisualPoint position, VisualPoint focus) {
        int dx = Integer.signum(focus.x() - position.x());
        int dz = Integer.signum(focus.z() - position.z());
        int sideX = dz == 0 ? 0 : -dz;
        int sideZ = dx == 0 ? 0 : dx;
        if (sideX == 0 && sideZ == 0) sideX = 1;
        VisualPoint camera = new VisualPoint(position.x() + sideX * 9,
                position.y() + 5, position.z() + sideZ * 9);
        add(result, region, id, "railway", region.planId() + ":baseline_railway",
                camera, raised(focus, 2));
    }

    private static VisualPoint openSpaceCamera(AuthoredRegionSeed region, VisualBounds bounds) {
        VisualPoint focus = center(bounds);
        int y = bounds.max().y() + 4;
        List<VisualPoint> candidates = List.of(
                new VisualPoint(bounds.max().x() + 10, y, focus.z()),
                new VisualPoint(bounds.min().x() - 10, y, focus.z()),
                new VisualPoint(focus.x(), y, bounds.max().z() + 10),
                new VisualPoint(focus.x(), y, bounds.min().z() - 10));
        return candidates.stream().filter(candidate -> region.settlementSite().buildings().stream()
                        .noneMatch(building -> containsHorizontal(building.parcel(), candidate)))
                .findFirst().orElse(candidates.getFirst());
    }

    private static boolean containsHorizontal(VisualBounds bounds, VisualPoint point) {
        return point.x() >= bounds.min().x() && point.x() <= bounds.max().x()
                && point.z() >= bounds.min().z() && point.z() <= bounds.max().z();
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
