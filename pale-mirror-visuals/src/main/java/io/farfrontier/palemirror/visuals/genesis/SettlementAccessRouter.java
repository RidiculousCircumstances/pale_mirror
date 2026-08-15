package io.farfrontier.palemirror.visuals.genesis;

import io.farfrontier.palemirror.api.AuthoredBuildingPlan;
import io.farfrontier.palemirror.api.LinearFeaturePlan;
import io.farfrontier.palemirror.api.VisualBounds;
import io.farfrontier.palemirror.api.VisualPoint;
import io.farfrontier.palemirror.api.VisualPort;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Deterministic obstacle-aware routing from a real facade port to the public street graph. */
final class SettlementAccessRouter {
    private static final int OTHER_BUILDING_CLEARANCE = 2;
    private static final int DOORWAY_APPROACH_LENGTH = 4;
    private static final int SEARCH_MARGIN = 8;

    private SettlementAccessRouter() { }

    static List<VisualPoint> route(AuthoredBuildingPlan owner, List<AuthoredBuildingPlan> buildings,
                                   List<LinearFeaturePlan> publicGraph) {
        VisualPort port = SettlementLayoutGeometry.publicPort(owner);
        VisualPoint access = SettlementLayoutGeometry.below(port.position());
        int outwardX = switch (Math.floorMod(port.outwardQuarterTurns(), 4)) {
            case 0 -> 1; case 2 -> -1; default -> 0;
        };
        int outwardZ = switch (Math.floorMod(port.outwardQuarterTurns(), 4)) {
            case 1 -> 1; case 3 -> -1; default -> 0;
        };
        VisualBounds ownerFootprint = owner.modules().getFirst().footprint();
        int approachLength = Math.max(DOORWAY_APPROACH_LENGTH,
                distanceOutside(ownerFootprint, access, port.outwardQuarterTurns()) + 2);
        Cell throat = new Cell(access.x() + outwardX * approachLength,
                access.z() + outwardZ * approachLength);

        Set<Cell> blocked = new HashSet<>();
        for (AuthoredBuildingPlan building : buildings) {
            VisualBounds footprint = building.modules().getFirst().footprint();
            int expansion = building == owner ? 0 : OTHER_BUILDING_CLEARANCE;
            fill(blocked, footprint, expansion);
        }
        blocked.remove(throat);

        Set<Cell> targets = new LinkedHashSet<>();
        for (LinearFeaturePlan feature : publicGraph) {
            for (int segment = 1; segment < feature.nodes().size(); segment++) {
                raster(feature.nodes().get(segment - 1), feature.nodes().get(segment)).stream()
                        .map(value -> new Cell(value.x(), value.z()))
                        .filter(value -> !blocked.contains(value)).forEach(targets::add);
            }
        }
        if (targets.isEmpty()) throw new DryMineSiteUnavailableException(
                "Settlement public street graph has no free access cells");

        Bounds bounds = Bounds.around(buildings, publicGraph, SEARCH_MARGIN);
        List<Cell> path;
        try {
            path = search(throat, targets, blocked, bounds);
        } catch (DryMineSiteUnavailableException failure) {
            throw new DryMineSiteUnavailableException(owner.buildingId() + ": " + failure.getMessage());
        }
        List<VisualPoint> result = new ArrayList<>();
        result.add(access);
        // Keep this as one authored segment. The circulation compiler then
        // interpolates a bounded five-block apron instead of snapping the
        // first outside column to an unrelated natural height.
        result.add(new VisualPoint(throat.x(), access.y(), throat.z()));
        for (Cell cell : compress(path)) {
            VisualPoint point = new VisualPoint(cell.x(), access.y(), cell.z());
            if (!point.equals(result.getLast())) result.add(point);
        }
        return List.copyOf(result);
    }

    private static List<Cell> search(Cell start, Set<Cell> targets, Set<Cell> blocked, Bounds bounds) {
        ArrayDeque<Cell> queue = new ArrayDeque<>();
        Map<Cell, Cell> previous = new HashMap<>();
        Set<Cell> visited = new HashSet<>();
        queue.add(start);
        visited.add(start);
        Cell destination = null;
        int[][] directions = {{1, 0}, {0, 1}, {-1, 0}, {0, -1}};
        while (!queue.isEmpty()) {
            Cell current = queue.removeFirst();
            if (targets.contains(current)) {
                destination = current;
                break;
            }
            for (int[] direction : directions) {
                Cell next = new Cell(current.x() + direction[0], current.z() + direction[1]);
                if (!bounds.contains(next) || blocked.contains(next) || !visited.add(next)) continue;
                previous.put(next, current);
                queue.addLast(next);
            }
        }
        if (destination == null) throw new DryMineSiteUnavailableException(
                "Settlement entrance cannot reach its public street without crossing a building");
        ArrayList<Cell> reverse = new ArrayList<>();
        for (Cell current = destination; current != null; current = previous.get(current)) {
            reverse.add(current);
            if (current.equals(start)) break;
        }
        java.util.Collections.reverse(reverse);
        return reverse;
    }

    private static List<Cell> compress(List<Cell> path) {
        if (path.size() <= 2) return path;
        List<Cell> result = new ArrayList<>();
        result.add(path.getFirst());
        int previousDx = path.get(1).x() - path.getFirst().x();
        int previousDz = path.get(1).z() - path.getFirst().z();
        for (int index = 2; index < path.size(); index++) {
            int dx = path.get(index).x() - path.get(index - 1).x();
            int dz = path.get(index).z() - path.get(index - 1).z();
            if (dx != previousDx || dz != previousDz) result.add(path.get(index - 1));
            previousDx = dx;
            previousDz = dz;
        }
        result.add(path.getLast());
        return result;
    }

    private static void fill(Set<Cell> target, VisualBounds bounds, int expansion) {
        for (int x = bounds.min().x() - expansion; x <= bounds.max().x() + expansion; x++) {
            for (int z = bounds.min().z() - expansion; z <= bounds.max().z() + expansion; z++) {
                target.add(new Cell(x, z));
            }
        }
    }

    private static int distanceOutside(VisualBounds bounds, VisualPoint point, int outward) {
        return switch (Math.floorMod(outward, 4)) {
            case 0 -> bounds.max().x() - point.x();
            case 1 -> bounds.max().z() - point.z();
            case 2 -> point.x() - bounds.min().x();
            default -> point.z() - bounds.min().z();
        };
    }

    private static List<VisualPoint> raster(VisualPoint from, VisualPoint to) {
        int dx = to.x() - from.x();
        int dz = to.z() - from.z();
        int steps = Math.max(1, Math.max(Math.abs(dx), Math.abs(dz)));
        List<VisualPoint> result = new ArrayList<>(steps + 1);
        for (int step = 0; step <= steps; step++) result.add(new VisualPoint(
                from.x() + dx * step / steps, from.y(), from.z() + dz * step / steps));
        return result;
    }

    private record Cell(int x, int z) { }

    private record Bounds(int minX, int maxX, int minZ, int maxZ) {
        static Bounds around(List<AuthoredBuildingPlan> buildings, List<LinearFeaturePlan> graph, int margin) {
            int minX = buildings.stream().mapToInt(value -> value.parcel().min().x()).min().orElseThrow();
            int maxX = buildings.stream().mapToInt(value -> value.parcel().max().x()).max().orElseThrow();
            int minZ = buildings.stream().mapToInt(value -> value.parcel().min().z()).min().orElseThrow();
            int maxZ = buildings.stream().mapToInt(value -> value.parcel().max().z()).max().orElseThrow();
            for (LinearFeaturePlan feature : graph) for (VisualPoint point : feature.nodes()) {
                minX = Math.min(minX, point.x()); maxX = Math.max(maxX, point.x());
                minZ = Math.min(minZ, point.z()); maxZ = Math.max(maxZ, point.z());
            }
            return new Bounds(minX - margin, maxX + margin, minZ - margin, maxZ + margin);
        }

        boolean contains(Cell cell) {
            return cell.x() >= minX && cell.x() <= maxX && cell.z() >= minZ && cell.z() <= maxZ;
        }
    }
}
