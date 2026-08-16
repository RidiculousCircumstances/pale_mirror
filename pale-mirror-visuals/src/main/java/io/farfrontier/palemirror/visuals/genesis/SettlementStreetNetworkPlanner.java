package io.farfrontier.palemirror.visuals.genesis;

import io.farfrontier.palemirror.api.LinearFeaturePlan;
import io.farfrontier.palemirror.api.VisualPoint;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Resolves the whole public-realm graph before any street owns a physical surface column. */
final class SettlementStreetNetworkPlanner {
    private static final int TERRAIN_SAMPLE_STEP = 4;
    private static final int LEVEL_DOORWAY_CELLS = 0;

    private SettlementStreetNetworkPlanner() { }

    static List<LinearFeaturePlan> resolve(List<LinearFeaturePlan> raw,
                                           SettlementTerrainSnapshot snapshot) {
        List<LinearFeaturePlan> publicFeatures = raw.stream()
                .filter(value -> !value.id().startsWith("access_") && value.walkable()).toList();
        Map<Long, Integer> facadeDatums = facadeConnectionDatums(raw, publicFeatures, snapshot);
        List<LinearFeaturePlan> publicNetwork = resolvePublic(publicFeatures, snapshot, facadeDatums);
        Map<String, LinearFeaturePlan> publicById = new HashMap<>();
        publicNetwork.forEach(feature -> publicById.put(feature.id(), feature));
        raw.stream().filter(value -> !value.id().startsWith("access_") && !value.walkable())
                .map(value -> resolvePublic(List.of(value), snapshot).getFirst())
                .forEach(feature -> publicById.put(feature.id(), feature));
        Map<Long, Integer> publicDatums = new HashMap<>();
        Map<Long, String> publicOwners = new HashMap<>();
        publicNetwork.forEach(feature -> feature.nodes().forEach(point -> {
            Integer first = publicDatums.putIfAbsent(key(point), point.y());
            if (first != null && first != point.y()) throw new IllegalStateException(
                    "Street junction resolved to " + first + " by " + publicOwners.get(key(point))
                            + " and " + point.y() + " by " + feature.id() + " at "
                            + point.x() + "," + point.z());
            publicOwners.putIfAbsent(key(point), feature.id());
        }));
        List<LinearFeaturePlan> result = new ArrayList<>(raw.size());
        Map<Long, Integer> connectedDatums = new HashMap<>(publicDatums);
        for (LinearFeaturePlan feature : raw) {
            LinearFeaturePlan resolved = feature.id().startsWith("access_")
                    ? resolveAccess(feature, snapshot, connectedDatums) : publicById.get(feature.id());
            result.add(resolved);
            if (feature.id().startsWith("access_")) resolved.nodes().forEach(point ->
                    connectedDatums.putIfAbsent(key(point), point.y()));
        }
        requireSharedDatums(result);
        return List.copyOf(result);
    }

    private static Map<Long, Integer> facadeConnectionDatums(List<LinearFeaturePlan> raw,
                                                              List<LinearFeaturePlan> publicFeatures,
                                                              SettlementTerrainSnapshot snapshot) {
        Set<Long> publicCells = new HashSet<>();
        publicFeatures.forEach(feature -> raster(feature).forEach(point -> publicCells.add(key(point))));
        Map<Long, DatumRange> ranges = new HashMap<>();
        for (LinearFeaturePlan feature : raw) {
            if (!feature.id().startsWith("access_")) continue;
            List<VisualPoint> path = eraseLoops(raster(feature));
            int thresholdY = feature.nodes().getFirst().y();
            for (int index = 0; index < path.size(); index++) {
                long coordinate = key(path.get(index));
                if (!publicCells.contains(coordinate)) continue;
                mergeRange(ranges, coordinate, thresholdY - index, thresholdY + index, path.get(index));
                break;
            }
        }
        Map<Long, Integer> result = new HashMap<>();
        ranges.forEach((coordinate, range) -> {
            long packed = coordinate;
            int natural = snapshot.exactSurfaceHeight((int) (packed >> 32), (int) packed);
            result.put(coordinate, clamp(natural, range.minimum(), range.maximum()));
        });
        return result;
    }

    private static void mergeRange(Map<Long, DatumRange> ranges, long coordinate,
                                   int minimum, int maximum, VisualPoint point) {
        DatumRange prior = ranges.get(coordinate);
        DatumRange merged = prior == null ? new DatumRange(minimum, maximum)
                : new DatumRange(Math.max(prior.minimum(), minimum), Math.min(prior.maximum(), maximum));
        if (merged.minimum() > merged.maximum()) throw new DryMineSiteUnavailableException(
                "Facade approaches require incompatible street heights at " + point.x() + "," + point.z());
        ranges.put(coordinate, merged);
    }

    private static void requireSharedDatums(List<LinearFeaturePlan> features) {
        Map<Long, StreetDatum> datums = new HashMap<>();
        for (LinearFeaturePlan feature : features) {
            if (!feature.walkable()) continue;
            for (int index = 0; index < feature.nodes().size(); index++) {
                VisualPoint point = feature.nodes().get(index);
                StreetDatum prior = datums.putIfAbsent(key(point),
                        new StreetDatum(feature.id(), index, point.y()));
                if (prior != null && prior.y() != point.y()) throw new DryMineSiteUnavailableException(
                        "Street graph has conflicting datums at " + point.x() + "," + point.z()
                                + ": " + prior.featureId() + "[" + prior.index() + "]=" + prior.y()
                                + ", " + feature.id() + "[" + index + "]=" + point.y());
            }
        }
    }

    static List<LinearFeaturePlan> resolvePublic(List<LinearFeaturePlan> raw,
                                                  SettlementTerrainSnapshot snapshot) {
        return resolvePublic(raw, snapshot, Map.of());
    }

    private static List<LinearFeaturePlan> resolvePublic(List<LinearFeaturePlan> raw,
                                                          SettlementTerrainSnapshot snapshot,
                                                          Map<Long, Integer> facadeDatums) {
        Map<String, List<VisualPoint>> paths = new LinkedHashMap<>();
        Map<Long, Set<String>> owners = new HashMap<>();
        for (LinearFeaturePlan feature : raw) {
            List<VisualPoint> path = raster(feature);
            paths.put(feature.id(), path);
            path.forEach(point -> owners.computeIfAbsent(key(point), ignored -> new HashSet<>()).add(feature.id()));
        }
        Set<Long> junctions = owners.entrySet().stream().filter(value -> value.getValue().size() > 1)
                .map(Map.Entry::getKey).collect(java.util.stream.Collectors.toUnmodifiableSet());
        Map<Long, Integer> fixedDatums = new HashMap<>(facadeDatums);
        for (long junction : junctions) fixedDatums.computeIfAbsent(junction, coordinate -> {
            long packed = coordinate;
            return snapshot.exactSurfaceHeight((int) (packed >> 32), (int) packed);
        });
        List<LinearFeaturePlan> result = new ArrayList<>(raw.size());
        for (LinearFeaturePlan feature : raw) {
            List<VisualPoint> path = paths.get(feature.id());
            Set<Integer> controlIndices = new HashSet<>();
            controlIndices.add(0);
            controlIndices.add(path.size() - 1);
            for (int index = 0; index < path.size(); index += TERRAIN_SAMPLE_STEP) controlIndices.add(index);
            for (int index = 0; index < path.size(); index++) {
                long key = key(path.get(index));
                if (junctions.contains(key) || fixedDatums.containsKey(key)) controlIndices.add(index);
            }
            result.add(resolvePublicFeature(feature, path, controlIndices, snapshot, fixedDatums));
        }
        return List.copyOf(result);
    }

    private static LinearFeaturePlan resolvePublicFeature(LinearFeaturePlan feature, List<VisualPoint> path,
                                                           Set<Integer> controls,
                                                           SettlementTerrainSnapshot snapshot,
                                                           Map<Long, Integer> fixedDatums) {
        List<Integer> ordered = controls.stream().sorted(Comparator.naturalOrder()).toList();
        int[] desired = new int[path.size()];
        for (int control = 1; control < ordered.size(); control++) {
            int start = ordered.get(control - 1);
            int end = ordered.get(control);
            VisualPoint from = path.get(start);
            VisualPoint to = path.get(end);
            int fromY = snapshot.exactSurfaceHeight(from.x(), from.z());
            int toY = snapshot.exactSurfaceHeight(to.x(), to.z());
            int span = end - start;
            for (int index = start; index <= end; index++) {
                desired[index] = fromY + (toY - fromY) * (index - start) / Math.max(1, span);
            }
        }
        if (path.size() == 1) desired[0] = snapshot.exactSurfaceHeight(path.getFirst().x(), path.getFirst().z());
        int[] heights = feature.walkable() ? resolveWalkableGrade(feature, path, desired, fixedDatums) : desired;
        List<VisualPoint> result = new ArrayList<>(path.size());
        for (int index = 0; index < path.size(); index++) {
            Integer fixed = fixedDatums.get(key(path.get(index)));
            if (fixed != null && heights[index] != fixed) throw new IllegalStateException(
                    feature.id() + " lost fixed datum " + fixed + " at "
                            + path.get(index).x() + "," + path.get(index).z() + ": " + heights[index]);
            result.add(withY(path.get(index), heights[index]));
        }
        return new LinearFeaturePlan(feature.id(), feature.kind(), result, feature.width(), feature.walkable());
    }

    private static int[] resolveWalkableGrade(LinearFeaturePlan feature, List<VisualPoint> path,
                                               int[] desired, Map<Long, Integer> fixedDatums) {
        int[] minimum = new int[path.size()];
        int[] maximum = new int[path.size()];
        java.util.Arrays.fill(minimum, Integer.MIN_VALUE / 4);
        java.util.Arrays.fill(maximum, Integer.MAX_VALUE / 4);
        for (int fixedIndex = 0; fixedIndex < path.size(); fixedIndex++) {
            Integer fixed = fixedDatums.get(key(path.get(fixedIndex)));
            if (fixed == null) continue;
            for (int index = 0; index < path.size(); index++) {
                int distance = Math.abs(index - fixedIndex);
                minimum[index] = Math.max(minimum[index], fixed - distance);
                maximum[index] = Math.min(maximum[index], fixed + distance);
            }
        }
        int[] result = new int[path.size()];
        for (int index = 0; index < path.size(); index++) {
            int low = minimum[index];
            int high = maximum[index];
            if (index > 0) {
                low = Math.max(low, result[index - 1] - 1);
                high = Math.min(high, result[index - 1] + 1);
            }
            if (low > high) throw new DryMineSiteUnavailableException(
                    "Township " + feature.id() + " has incompatible fixed street datums");
            result[index] = clamp(desired[index], low, high);
        }
        return result;
    }

    private static LinearFeaturePlan resolveAccess(LinearFeaturePlan feature, SettlementTerrainSnapshot snapshot,
                                                    Map<Long, Integer> publicDatums) {
        List<VisualPoint> path = eraseLoops(raster(feature));
        int connection = path.size() - 1;
        int thresholdY = feature.nodes().getFirst().y();
        for (int index = 1; index < path.size(); index++) {
            Integer candidateY = publicDatums.get(key(path.get(index)));
            int levelCells = index <= 1 ? 0 : LEVEL_DOORWAY_CELLS;
            if (candidateY != null && Math.abs(candidateY - thresholdY) <= index - levelCells) {
                connection = index;
                break;
            }
        }
        int thresholdEnd = Math.min(connection, connection <= 1 ? 0 : LEVEL_DOORWAY_CELLS);
        Integer publicY = publicDatums.get(key(path.get(connection)));
        if (publicY == null) throw new DryMineSiteUnavailableException(
                feature.id() + " does not terminate on the resolved public street graph");
        int remaining = connection - thresholdEnd;
        if (Math.abs(publicY - thresholdY) > remaining) throw new DryMineSiteUnavailableException(
                feature.id() + " has insufficient length for a walkable facade approach");
        int[] heights = new int[connection + 1];
        for (int index = 0; index <= thresholdEnd; index++) heights[index] = thresholdY;
        for (int index = thresholdEnd + 1; index < connection; index++) {
            // Facade approaches are short and visually sensitive. A coarse 16-block sample
            // can otherwise stretch one high imported threshold into a broad causeway.
            int desired = snapshot.exactSurfaceHeight(path.get(index).x(), path.get(index).z());
            heights[index] = clamp(desired, heights[index - 1] - 1, heights[index - 1] + 1);
        }
        heights[connection] = publicY;
        for (int index = connection - 1; index > thresholdEnd; index--) {
            heights[index] = clamp(heights[index], heights[index + 1] - 1, heights[index + 1] + 1);
        }
        for (int index = 1; index < heights.length; index++) if (Math.abs(heights[index] - heights[index - 1]) > 1) {
            throw new DryMineSiteUnavailableException(feature.id() + " produced a disconnected street grade");
        }
        List<VisualPoint> resolved = new ArrayList<>(connection + 1);
        for (int index = 0; index <= connection; index++) resolved.add(withY(path.get(index), heights[index]));
        return new LinearFeaturePlan(feature.id(), feature.kind(), resolved,
                feature.width(), feature.walkable());
    }

    private static List<VisualPoint> eraseLoops(List<VisualPoint> path) {
        List<VisualPoint> result = new ArrayList<>(path.size());
        Map<Long, Integer> indices = new HashMap<>();
        for (VisualPoint point : path) {
            Integer repeated = indices.get(key(point));
            if (repeated != null) {
                while (result.size() > repeated + 1) indices.remove(key(result.removeLast()));
            } else {
                indices.put(key(point), result.size());
                result.add(point);
            }
        }
        return result;
    }

    private static List<VisualPoint> raster(LinearFeaturePlan feature) {
        List<VisualPoint> result = new ArrayList<>();
        for (int segment = 1; segment < feature.nodes().size(); segment++) {
            VisualPoint from = feature.nodes().get(segment - 1);
            VisualPoint to = feature.nodes().get(segment);
            int steps = Math.max(1, Math.max(Math.abs(to.x() - from.x()), Math.abs(to.z() - from.z())));
            for (int step = segment == 1 ? 0 : 1; step <= steps; step++) result.add(new VisualPoint(
                    from.x() + (to.x() - from.x()) * step / steps,
                    from.y() + (to.y() - from.y()) * step / steps,
                    from.z() + (to.z() - from.z()) * step / steps));
        }
        return result;
    }

    private static VisualPoint withY(VisualPoint point, int y) {
        return new VisualPoint(point.x(), y, point.z());
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static long key(VisualPoint point) {
        return (long) point.x() << 32 ^ Integer.toUnsignedLong(point.z());
    }

    private record StreetDatum(String featureId, int index, int y) { }
    private record DatumRange(int minimum, int maximum) { }
}
