package io.farfrontier.palemirror.visuals.genesis;

import io.farfrontier.palemirror.api.AuthoredBuildingPlan;
import io.farfrontier.palemirror.api.LinearFeatureKind;
import io.farfrontier.palemirror.api.LinearFeaturePlan;
import io.farfrontier.palemirror.api.VisualBounds;
import io.farfrontier.palemirror.api.VisualPoint;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Joins authored public-realm fragments without routing a new street through a building. */
final class SettlementPublicStreetConnector {
    private static final int BUILDING_CLEARANCE = 3;
    private static final int SEARCH_MARGIN = 8;

    private SettlementPublicStreetConnector() { }

    static List<LinearFeaturePlan> connect(List<LinearFeaturePlan> source,
                                           List<AuthoredBuildingPlan> buildings) {
        List<LinearFeaturePlan> result = new ArrayList<>(source);
        int connector = 0;
        while (true) {
            List<Set<Cell>> components = components(result);
            if (components.size() <= 1) return List.copyOf(result);
            Set<Cell> trunk = componentContaining(components, cells(feature(result, "freight_spine")));
            Set<Cell> destinations = new LinkedHashSet<>();
            components.stream().filter(value -> value != trunk).forEach(destinations::addAll);
            List<Cell> path = search(trunk, destinations, blocked(buildings), bounds(buildings, result));
            List<VisualPoint> nodes = compress(path).stream()
                    .map(cell -> new VisualPoint(cell.x(), 0, cell.z())).toList();
            result.add(new LinearFeaturePlan("street_connector_" + connector++, LinearFeatureKind.STREET,
                    nodes, 3, true));
        }
    }

    private static List<Cell> search(Set<Cell> starts, Set<Cell> targets, Set<Cell> blocked, Bounds bounds) {
        ArrayDeque<Cell> queue = new ArrayDeque<>();
        Map<Cell, Cell> previous = new HashMap<>();
        Set<Cell> visited = new HashSet<>();
        starts.stream().sorted().forEach(cell -> {
            queue.addLast(cell);
            visited.add(cell);
        });
        blocked.removeAll(starts);
        blocked.removeAll(targets);
        Cell destination = null;
        int[][] directions = {{1, 0}, {0, 1}, {-1, 0}, {0, -1}};
        while (!queue.isEmpty() && destination == null) {
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
                "Settlement public street components cannot be joined around authored buildings");
        List<Cell> reverse = new ArrayList<>();
        for (Cell current = destination; current != null; current = previous.get(current)) {
            reverse.add(current);
            if (starts.contains(current)) break;
        }
        java.util.Collections.reverse(reverse);
        return reverse;
    }

    private static List<Set<Cell>> components(List<LinearFeaturePlan> features) {
        Set<Cell> remaining = new LinkedHashSet<>();
        features.forEach(feature -> remaining.addAll(cells(feature)));
        List<Set<Cell>> result = new ArrayList<>();
        while (!remaining.isEmpty()) {
            Cell seed = remaining.iterator().next();
            Set<Cell> component = new LinkedHashSet<>();
            ArrayDeque<Cell> queue = new ArrayDeque<>();
            queue.add(seed);
            while (!queue.isEmpty()) {
                Cell current = queue.removeFirst();
                if (!remaining.remove(current)) continue;
                component.add(current);
                for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                    if (dx != 0 || dz != 0) queue.add(new Cell(current.x() + dx, current.z() + dz));
                }
            }
            result.add(component);
        }
        return result;
    }

    private static Set<Cell> componentContaining(List<Set<Cell>> components, Set<Cell> feature) {
        return components.stream().filter(value -> value.stream().anyMatch(feature::contains))
                .findFirst().orElseThrow();
    }

    private static Set<Cell> cells(LinearFeaturePlan feature) {
        Set<Cell> result = new LinkedHashSet<>();
        for (int segment = 1; segment < feature.nodes().size(); segment++) {
            VisualPoint from = feature.nodes().get(segment - 1);
            VisualPoint to = feature.nodes().get(segment);
            int steps = Math.max(1, Math.max(Math.abs(to.x() - from.x()), Math.abs(to.z() - from.z())));
            for (int step = segment == 1 ? 0 : 1; step <= steps; step++) result.add(new Cell(
                    from.x() + (to.x() - from.x()) * step / steps,
                    from.z() + (to.z() - from.z()) * step / steps));
        }
        return result;
    }

    private static Set<Cell> blocked(List<AuthoredBuildingPlan> buildings) {
        Set<Cell> result = new HashSet<>();
        for (AuthoredBuildingPlan building : buildings) {
            VisualBounds bounds = building.modules().getFirst().footprint();
            for (int x = bounds.min().x() - BUILDING_CLEARANCE;
                 x <= bounds.max().x() + BUILDING_CLEARANCE; x++) {
                for (int z = bounds.min().z() - BUILDING_CLEARANCE;
                     z <= bounds.max().z() + BUILDING_CLEARANCE; z++) result.add(new Cell(x, z));
            }
        }
        return result;
    }

    private static Bounds bounds(List<AuthoredBuildingPlan> buildings, List<LinearFeaturePlan> features) {
        int minX = buildings.stream().mapToInt(value -> value.parcel().min().x()).min().orElseThrow();
        int maxX = buildings.stream().mapToInt(value -> value.parcel().max().x()).max().orElseThrow();
        int minZ = buildings.stream().mapToInt(value -> value.parcel().min().z()).min().orElseThrow();
        int maxZ = buildings.stream().mapToInt(value -> value.parcel().max().z()).max().orElseThrow();
        for (LinearFeaturePlan feature : features) for (VisualPoint point : feature.nodes()) {
            minX = Math.min(minX, point.x()); maxX = Math.max(maxX, point.x());
            minZ = Math.min(minZ, point.z()); maxZ = Math.max(maxZ, point.z());
        }
        return new Bounds(minX - SEARCH_MARGIN, maxX + SEARCH_MARGIN,
                minZ - SEARCH_MARGIN, maxZ + SEARCH_MARGIN);
    }

    private static List<Cell> compress(List<Cell> path) {
        if (path.size() <= 2) return path;
        List<Cell> result = new ArrayList<>();
        result.add(path.getFirst());
        int priorX = path.get(1).x() - path.getFirst().x();
        int priorZ = path.get(1).z() - path.getFirst().z();
        for (int index = 2; index < path.size(); index++) {
            int dx = path.get(index).x() - path.get(index - 1).x();
            int dz = path.get(index).z() - path.get(index - 1).z();
            if (dx != priorX || dz != priorZ) result.add(path.get(index - 1));
            priorX = dx;
            priorZ = dz;
        }
        result.add(path.getLast());
        return result;
    }

    private static LinearFeaturePlan feature(List<LinearFeaturePlan> features, String id) {
        return features.stream().filter(value -> value.id().equals(id)).findFirst().orElseThrow();
    }

    private record Cell(int x, int z) implements Comparable<Cell> {
        @Override public int compareTo(Cell other) {
            int xOrder = Integer.compare(x, other.x);
            return xOrder != 0 ? xOrder : Integer.compare(z, other.z);
        }
    }

    private record Bounds(int minX, int maxX, int minZ, int maxZ) {
        boolean contains(Cell cell) {
            return cell.x() >= minX && cell.x() <= maxX && cell.z() >= minZ && cell.z() <= maxZ;
        }
    }
}
