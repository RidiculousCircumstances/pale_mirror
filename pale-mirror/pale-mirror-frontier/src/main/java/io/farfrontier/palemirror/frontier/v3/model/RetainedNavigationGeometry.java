package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.geometry.*;
import io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianGeometryRevision.Region;
import java.util.*;

/** Shared observation overlay. No knowledge is inferred from the job asking for a route. */
final class RetainedNavigationGeometry {
    private record Index(Map<TerrainColumn, List<BlockPosition>> columns, Set<TerrainColumn> detailed) { }
    private record Projection(RegionalBlockMap<Optional<NavigationCell>> cells, Map<Region, Index> index) { }
    private static RegionalBlockMap<NavigationObservation> priorObserved = RegionalBlockMap.copyOf(Map.of());
    private static RegionalBlockMap<NavigationBlockState> priorSources = RegionalBlockMap.copyOf(Map.of());
    private static Map<NavigationBlockState, NavigationObservation> priorDefinitions = Map.of();
    private static Projection projected = new Projection(RegionalBlockMap.copyOf(Map.of()), Map.of());
    private static final Set<Region> contextualRegions = new HashSet<>();
    private RetainedNavigationGeometry() { }
    static BoundedPedestrianApproach.SurveyedSurface overlay(BoundedPedestrianApproach.SurveyedSurface base,
                                                           NavigationGeometryState observations,
                                                           Map<BlockPosition, NavigationBlockState> sourceBlocks) {
        if (observations.cells().isEmpty() && observations.definitions().isEmpty()) return base;
        var projection = project(observations, RegionalBlockMap.copyOf(sourceBlocks));
        var cells = projection.cells();
        if (cells.isEmpty()) return base;
        var index = projection.index();
        return new BoundedPedestrianApproach.SurveyedSurface() {
            @Override public SurfaceAnchor at(int x, int z) { var all = surfacesAt(x, z); return all.isEmpty() ? null : all.getLast(); }
            @Override public List<SurfaceAnchor> surfacesAt(int x, int z) {
                var part = index.get(new Region(x >> 4, z >> 4));
                var positions = part == null ? null : part.columns().get(new TerrainColumn(x, z));
                if (positions == null) return base.surfacesAt(x, z);
                var levels = new TreeSet<SurfaceAnchor>(Comparator.comparingInt(SurfaceAnchor::y));
                levels.addAll(base.surfacesAt(x, z));
                for (var position : positions) {
                    var surface = new SurfaceAnchor(position); levels.remove(surface);
                    var cell = cells.get(position);
                    if (cell.isPresent() && cell.orElseThrow().dry() && !cell.orElseThrow().shape().boxes().isEmpty()) levels.add(surface);
                }
                return List.copyOf(levels);
            }
            @Override public boolean excluded(SurfaceAnchor surface) {
                var cell = cells.get(surface.support());
                return cell == null ? base.excluded(surface)
                        : cell.isPresent() && (!cell.orElseThrow().dry() || cell.orElseThrow().shape().boxes().isEmpty());
            }
            @Override public boolean blockKnown(BlockPosition position) {
                var cell = cells.get(position); return cell == null ? base.blockKnown(position) : cell.isPresent();
            }
            @Override public boolean clearanceKnown(SurfaceAnchor surface) {
                for (int y = 0; y <= 2; y++) if (!blockKnown(surface.support().offset(0, y, 0))) return false;
                return true;
            }
            @Override public Optional<NavigationCell> collisionAt(BlockPosition position) {
                var observed = cells.get(position);
                return observed == null ? base.collisionAt(position) : observed;
            }
            @Override public boolean detailedCollisionAt(int x, int z) {
                var column = new TerrainColumn(x, z);
                for (int rx = (x - 3) >> 4; rx <= (x + 3) >> 4; rx++)
                    for (int rz = (z - 3) >> 4; rz <= (z + 3) >> 4; rz++) {
                        var part = index.get(new Region(rx, rz));
                        if (part != null && part.detailed().contains(column)) return true;
                    }
                return base.detailedCollisionAt(x, z);
            }
        };
    }
    private static synchronized Projection project(NavigationGeometryState observations, RegionalBlockMap<NavigationBlockState> sources) {
        var observed = RegionalBlockMap.copyOf(observations.cells());
        var changed = new HashSet<>(observed.changesFrom(priorObserved));
        for (var region : changed) {
            if (observed.in(region).values().stream().anyMatch(value -> !value.dependencies().isEmpty())) contextualRegions.add(region);
            else contextualRegions.remove(region);
        }
        changed.addAll(sources.changesFrom(priorSources));
        // Contextual shapes can read a neighbouring region. Recompute only this
        // bounded halo; captured neighbour identities also validate after recovery.
        for (var region : Set.copyOf(changed)) for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++)
            if (contextualRegions.contains(new Region(region.x() + dx, region.z() + dz)))
                changed.add(new Region(region.x() + dx, region.z() + dz));
        if (priorDefinitions != observations.definitions() && !priorDefinitions.equals(observations.definitions())) {
            changed.addAll(sources.regions()); changed.addAll(priorSources.regions());
        }
        if (changed.isEmpty()) return projected;
        var updates = new HashMap<Region, Map<BlockPosition, Optional<NavigationCell>>>();
        for (var region : changed) {
            var result = new HashMap<BlockPosition, Optional<NavigationCell>>();
            sources.in(region).forEach((position, block) -> {
                var evidence = observations.definitions().get(block);
                var definition = evidence == null ? null : evidence.cell();
                if (definition != null && (definition.isEmpty() || !definition.orElseThrow().dry()
                        || !definition.orElseThrow().shape().equals(NavigationShape.FULL)
                            && !definition.orElseThrow().shape().equals(NavigationShape.EMPTY))) result.put(position, definition);
            });
            observed.in(region).forEach((position, evidence) -> {
                var cell = evidence.cell();
                var current = sources.get(position);
                // Validate the sampled block itself before its neighbours: a
                // stale contextual shape must not fence a newer COLD source.
                if (current != null && cell.isPresent()
                        && !cell.orElseThrow().observedState().filter(current::equals).isPresent()) return;
                for (var dependency : evidence.dependencies().entrySet()) {
                    var currentDependency = sources.get(dependency.getKey());
                    if (currentDependency != null && !dependency.getValue().filter(currentDependency::equals).isPresent()) {
                        cell = Optional.empty(); break;
                    }
                }
                // A retained native sample cannot resurrect a source block already mined in COLD.
                if (current == null || cell.isEmpty() || cell.orElseThrow().observedState().filter(current::equals).isPresent())
                    result.put(position, cell);
            });
            updates.put(region, result);
        }
        var cells = projected.cells().replace(updates);
        var index = new HashMap<>(projected.index());
        for (var region : cells.changesFrom(projected.cells())) {
            if (cells.in(region).isEmpty()) { index.remove(region); continue; }
            var columns = new HashMap<TerrainColumn, List<BlockPosition>>(); var detailed = new HashSet<TerrainColumn>();
            for (var position : cells.in(region).keySet()) {
                columns.computeIfAbsent(new TerrainColumn(position.x(), position.z()), ignored -> new ArrayList<>()).add(position);
                for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++)
                    detailed.add(new TerrainColumn(position.x() + dx, position.z() + dz));
            }
            columns.replaceAll((key, values) -> List.copyOf(values));
            index.put(region, new Index(Map.copyOf(columns), Set.copyOf(detailed)));
        }
        priorObserved = observed; priorSources = sources; priorDefinitions = observations.definitions();
        projected = new Projection(cells, Map.copyOf(index));
        return projected;
    }
}
