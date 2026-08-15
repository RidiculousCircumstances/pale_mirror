package io.farfrontier.palemirror.visuals.genesis;

import io.farfrontier.palemirror.api.AuthoredBuildingPlan;
import io.farfrontier.palemirror.api.AuthoredOpenSpacePlan;
import io.farfrontier.palemirror.api.DevelopmentReservation;
import io.farfrontier.palemirror.api.LinearFeatureKind;
import io.farfrontier.palemirror.api.LinearFeaturePlan;
import io.farfrontier.palemirror.api.ManagedAreaPlan;
import io.farfrontier.palemirror.api.VisualBounds;
import io.farfrontier.palemirror.api.VisualPoint;
import io.farfrontier.palemirror.api.VisualPort;
import io.farfrontier.palemirror.api.VisualPortKind;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Shared coordinate and footprint operations for authored settlement grammars. */
final class SettlementLayoutGeometry {
    private static final int CORRIDOR_VALIDATION_STEP = 4;
    private static final int MAXIMUM_CORRIDOR_CUT_FILL = 4;
    private SettlementLayoutGeometry() { }

    static LinearFeaturePlan followTerrain(LinearFeaturePlan feature, SettlementTerrainSnapshot snapshot) {
        return new LinearFeaturePlan(feature.id(), feature.kind(), feature.nodes().stream()
                .map(value -> surfacePoint(value, snapshot)).toList(), feature.width(), feature.walkable());
    }

    /**
     * Building access starts on the authored foundation surface. Every later
     * node follows the natural solid surface, not Minecraft's first-air
     * height. Keeping the first node exact prevents a road from silently
     * moving away from its declared door after terrain adaptation.
     */
    static LinearFeaturePlan followAccessTerrain(LinearFeaturePlan feature,
                                                  SettlementTerrainSnapshot snapshot) {
        java.util.List<VisualPoint> nodes = new java.util.ArrayList<>(feature.nodes().size());
        for (int index = 0; index < feature.nodes().size(); index++) {
            VisualPoint point = feature.nodes().get(index);
            // The authored doorway and its four-block apron form one threshold.
            // Snapping the second node to natural terrain created a one-block
            // cliff directly outside raised NBT doors.
            nodes.add(index <= 1 ? point : surfacePoint(point, snapshot));
        }
        return new LinearFeaturePlan(feature.id(), feature.kind(), nodes, feature.width(), feature.walkable());
    }

    /** The depot node is a raised authored pad; the rest of the spine is native ground. */
    static LinearFeaturePlan followFreightTerrain(LinearFeaturePlan feature,
                                                   SettlementTerrainSnapshot snapshot) {
        java.util.List<VisualPoint> nodes = new java.util.ArrayList<>(feature.nodes().size());
        for (int index = 0; index < feature.nodes().size(); index++) {
            VisualPoint point = feature.nodes().get(index);
            nodes.add(index == 1 ? point : surfacePoint(point, snapshot));
        }
        return new LinearFeaturePlan(feature.id(), feature.kind(), nodes, feature.width(), feature.walkable());
    }

    static void requireDryCirculation(List<LinearFeaturePlan> features,
                                      SettlementTerrainSnapshot snapshot, VisualPoint anchor) {
        for (LinearFeaturePlan feature : features) {
            if (feature.kind() != LinearFeatureKind.FREIGHT_ROAD
                    && feature.kind() != LinearFeatureKind.STREET
                    && feature.kind() != LinearFeatureKind.SIDEWALK
                    && feature.kind() != LinearFeatureKind.PLAZA) continue;
            for (int segment = 1; segment < feature.nodes().size(); segment++) {
                List<VisualPoint> points = raster(feature.nodes().get(segment - 1), feature.nodes().get(segment));
                for (int index = 0; index < points.size(); index += CORRIDOR_VALIDATION_STEP) {
                    VisualPoint point = points.get(index);
                    if (snapshot.waterAt(point.x(), point.z())) {
                        throw new DryMineSiteUnavailableException("Township circulation crosses water at "
                                + point.x() + "," + point.z() + " near " + anchor.x() + "," + anchor.z());
                    }
                    int actual = snapshot.exactSurfaceHeight(point.x(), point.z());
                    if (Math.abs(actual - point.y()) > MAXIMUM_CORRIDOR_CUT_FILL) {
                        throw new DryMineSiteUnavailableException("Township circulation exceeds bounded cut/fill at "
                                + point.x() + "," + point.z() + " near " + anchor.x() + "," + anchor.z());
                    }
                }
            }
        }
    }

    private static List<VisualPoint> raster(VisualPoint from, VisualPoint to) {
        int dx = to.x() - from.x();
        int dy = to.y() - from.y();
        int dz = to.z() - from.z();
        int steps = Math.max(1, Math.max(Math.abs(dx), Math.abs(dz)));
        List<VisualPoint> result = new ArrayList<>(steps + 1);
        for (int step = 0; step <= steps; step++) {
            result.add(new VisualPoint(from.x() + dx * step / steps,
                    from.y() + dy * step / steps, from.z() + dz * step / steps));
        }
        return result;
    }

    static boolean elevationTransition(List<VisualPoint> points, int index) {
        int y = points.get(index).y();
        return points.get(Math.max(0, index - 1)).y() != y
                || points.get(Math.min(points.size() - 1, index + 1)).y() != y;
    }

    static boolean lowerTransition(List<VisualPoint> points, int index) {
        int y = points.get(index).y();
        return points.get(Math.max(0, index - 1)).y() > y
                || points.get(Math.min(points.size() - 1, index + 1)).y() > y;
    }

    static AuthoredOpenSpacePlan resolveOpenSpace(AuthoredOpenSpacePlan space,
                                                   SettlementTerrainSnapshot snapshot) {
        SettlementTerrainSnapshot.PadResolution pad = snapshot.resolvePad(space.bounds());
        if (!pad.accepted()) throw new DryMineSiteUnavailableException("Township open space " + space.id()
                + " failed exact " + pad.failure() + " validation");
        int target = pad.targetY() - 1;
        int delta = target - space.bounds().min().y();
        VisualBounds shifted = new VisualBounds(withY(space.bounds().min(), space.bounds().min().y() + delta),
                withY(space.bounds().max(), space.bounds().max().y() + delta));
        return new AuthoredOpenSpacePlan(space.id(), space.kind(), shifted, space.ports().stream()
                .map(port -> new VisualPort(port.id(), port.kind(),
                        withY(port.position(), port.position().y() + delta), port.outwardQuarterTurns())).toList());
    }

    static VisualPoint terrainPoint(VisualPoint point, SettlementTerrainSnapshot snapshot) {
        return withY(point, snapshot.approximateHeight(point.x(), point.z()));
    }

    static VisualPoint surfacePoint(VisualPoint point, SettlementTerrainSnapshot snapshot) {
        return withY(point, snapshot.surfaceHeight(point.x(), point.z()));
    }

    static VisualPoint withY(VisualPoint point, int y) {
        return new VisualPoint(point.x(), y, point.z());
    }

    static ManagedAreaPlan managedArea(List<AuthoredBuildingPlan> buildings,
                                       List<LinearFeaturePlan> circulation,
                                       List<LinearFeaturePlan> defences,
                                       List<AuthoredOpenSpacePlan> openSpaces,
                                       List<DevelopmentReservation> reservations) {
        List<VisualBounds> areas = new ArrayList<>();
        // The managed footprint is a union of inhabited districts rather than
        // a geometric town circle. Generous verges join nearby buildings to
        // their streets, remove naturally generated forest between parcels,
        // and still leave an irregular edge that follows the authored plan.
        buildings.forEach(building -> areas.add(expand(building.parcel(), 12, 2, 12)));
        openSpaces.forEach(space -> areas.add(expand(space.bounds(), 8, 1, 8)));
        circulation.forEach(feature -> areas.add(featureBounds(feature,
                feature.kind() == LinearFeatureKind.FREIGHT_ROAD
                        || feature.kind() == LinearFeatureKind.STREET ? 14 : 8)));
        defences.forEach(feature -> areas.add(featureBounds(feature, 6)));
        reservations.forEach(reservation -> areas.add(expand(reservation.bounds(), 4, 1, 4)));
        return new ManagedAreaPlan(areas);
    }

    private static VisualBounds featureBounds(LinearFeaturePlan feature, int extra) {
        int radius = feature.width() / 2 + extra;
        int minX = feature.nodes().stream().mapToInt(VisualPoint::x).min().orElseThrow() - radius;
        int maxX = feature.nodes().stream().mapToInt(VisualPoint::x).max().orElseThrow() + radius;
        int minZ = feature.nodes().stream().mapToInt(VisualPoint::z).min().orElseThrow() - radius;
        int maxZ = feature.nodes().stream().mapToInt(VisualPoint::z).max().orElseThrow() + radius;
        int minY = feature.nodes().stream().mapToInt(VisualPoint::y).min().orElseThrow() - 2;
        int maxY = feature.nodes().stream().mapToInt(VisualPoint::y).max().orElseThrow() + 6;
        return new VisualBounds(new VisualPoint(minX, minY, minZ), new VisualPoint(maxX, maxY, maxZ));
    }

    static LinearFeaturePlan line(String id, LinearFeatureKind kind, VisualPoint anchor, int direction,
                                  int rightA, int inwardA, int rightB, int inwardB, int width) {
        return new LinearFeaturePlan(id, kind, List.of(local(anchor, rightA, inwardA, 0, direction),
                local(anchor, rightB, inwardB, 0, direction)), width,
                kind == LinearFeatureKind.PALISADE || kind == LinearFeatureKind.PALISADE_GATE);
    }

    static AuthoredBuildingPlan building(List<AuthoredBuildingPlan> buildings, String id) {
        return buildings.stream().filter(value -> value.buildingId().equals(id)).findFirst().orElseThrow();
    }

    static VisualPoint publicEntrance(AuthoredBuildingPlan building) {
        return publicPort(building).position();
    }

    static VisualPort publicPort(AuthoredBuildingPlan building) {
        return building.modules().stream().flatMap(module -> module.ports().stream())
                .filter(port -> port.kind() == VisualPortKind.PUBLIC_ENTRANCE).findFirst().orElseThrow();
    }

    static VisualPoint below(VisualPoint value) {
        return new VisualPoint(value.x(), value.y() - 1, value.z());
    }

    static VisualBounds moduleFootprint(FrontierModuleCatalog.Definition definition,
                                        VisualPoint origin, int rotation) {
        boolean swap = Math.floorMod(rotation, 2) == 1;
        int xSize = swap ? definition.sizeZ() : definition.sizeX();
        int zSize = swap ? definition.sizeX() : definition.sizeZ();
        VisualPoint min = new VisualPoint(origin.x() - xSize / 2, origin.y() + 1, origin.z() - zSize / 2);
        return new VisualBounds(min, new VisualPoint(min.x() + xSize - 1,
                min.y() + definition.sizeY() - 1, min.z() + zSize - 1));
    }

    static VisualPoint entrance(VisualBounds bounds, int direction) {
        int x = (bounds.min().x() + bounds.max().x()) / 2;
        int z = (bounds.min().z() + bounds.max().z()) / 2;
        if (direction == 0) x = bounds.max().x();
        else if (direction == 1) z = bounds.max().z();
        else if (direction == 2) x = bounds.min().x();
        else z = bounds.min().z();
        return new VisualPoint(x, bounds.min().y(), z);
    }

    /** Resolves the curated NBT threshold through the exact compiler rotation. */
    static VisualPoint authoredEntrance(FrontierModuleCatalog.Definition definition,
                                        VisualBounds footprint, int rotation) {
        int turns = Math.floorMod(rotation, 4);
        int x;
        int z;
        if (turns == 1) {
            x = definition.sizeZ() - 1 - definition.entranceZ();
            z = definition.entranceX();
        } else if (turns == 2) {
            x = definition.sizeX() - 1 - definition.entranceX();
            z = definition.sizeZ() - 1 - definition.entranceZ();
        } else if (turns == 3) {
            x = definition.entranceZ();
            z = definition.sizeX() - 1 - definition.entranceX();
        } else {
            x = definition.entranceX();
            z = definition.entranceZ();
        }
        return new VisualPoint(footprint.min().x() + x,
                footprint.min().y() + definition.entranceY(), footprint.min().z() + z);
    }

    static VisualPoint oppositeEntrance(VisualBounds bounds, int direction) {
        return entrance(bounds, Math.floorMod(direction + 2, 4));
    }

    static void validateNoOverlap(List<AuthoredBuildingPlan> buildings) {
        Set<String> ids = new HashSet<>();
        for (int first = 0; first < buildings.size(); first++) {
            AuthoredBuildingPlan a = buildings.get(first);
            if (!ids.add(a.buildingId())) throw new IllegalStateException("duplicate building id " + a.buildingId());
            for (int second = first + 1; second < buildings.size(); second++) {
                AuthoredBuildingPlan b = buildings.get(second);
                if (overlaps(a.parcel(), b.parcel())) throw new IllegalStateException(
                        "Township grammar overlaps " + a.buildingId() + " and " + b.buildingId());
            }
        }
    }

    static boolean overlaps(VisualBounds a, VisualBounds b) {
        return a.min().x() <= b.max().x() && a.max().x() >= b.min().x()
                && a.min().z() <= b.max().z() && a.max().z() >= b.min().z();
    }

    static boolean containsHorizontal(VisualBounds outer, VisualBounds inner) {
        return outer.min().x() <= inner.min().x() && outer.max().x() >= inner.max().x()
                && outer.min().z() <= inner.min().z() && outer.max().z() >= inner.max().z();
    }

    static VisualBounds expand(VisualBounds bounds, int x, int y, int z) {
        return new VisualBounds(new VisualPoint(bounds.min().x() - x, bounds.min().y() - y, bounds.min().z() - z),
                new VisualPoint(bounds.max().x() + x, bounds.max().y() + y, bounds.max().z() + z));
    }

    static VisualBounds around(VisualPoint point, int radius, int vertical) {
        return new VisualBounds(new VisualPoint(point.x() - radius, point.y() - 2, point.z() - radius),
                new VisualPoint(point.x() + radius, point.y() + vertical, point.z() + radius));
    }

    static VisualPoint local(VisualPoint anchor, int right, int inward, int up, int direction) {
        int dx = switch (Math.floorMod(direction, 4)) {
            case 0 -> inward; case 1 -> -right; case 2 -> -inward; default -> right;
        };
        int dz = switch (Math.floorMod(direction, 4)) {
            case 0 -> right; case 1 -> inward; case 2 -> -right; default -> -inward;
        };
        return new VisualPoint(anchor.x() + dx, anchor.y() + up, anchor.z() + dz);
    }

    static Local relative(VisualPoint anchor, VisualPoint point, int direction) {
        int dx = point.x() - anchor.x();
        int dz = point.z() - anchor.z();
        return switch (Math.floorMod(direction, 4)) {
            case 0 -> new Local(dz, dx); case 1 -> new Local(-dx, dz);
            case 2 -> new Local(-dz, -dx); default -> new Local(dx, -dz);
        };
    }

    static VisualBounds orientedBounds(VisualPoint anchor, int right, int inward, int down, int up,
                                       int direction) {
        List<VisualPoint> corners = List.of(local(anchor, -right, -inward, down, direction),
                local(anchor, right, -inward, down, direction), local(anchor, -right, inward, up, direction),
                local(anchor, right, inward, up, direction));
        return new VisualBounds(new VisualPoint(corners.stream().mapToInt(VisualPoint::x).min().orElseThrow(),
                anchor.y() + down, corners.stream().mapToInt(VisualPoint::z).min().orElseThrow()),
                new VisualPoint(corners.stream().mapToInt(VisualPoint::x).max().orElseThrow(),
                        anchor.y() + up, corners.stream().mapToInt(VisualPoint::z).max().orElseThrow()));
    }

    record Local(int right, int inward) { }
}
