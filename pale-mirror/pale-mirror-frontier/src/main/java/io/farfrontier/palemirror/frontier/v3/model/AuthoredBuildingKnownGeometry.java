package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.buildings.*;
import io.farfrontier.palemirror.frontier.v3.model.geometry.*;
import io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianGeometryRevision.Region;

import java.util.*;

/**
 * Sparse pristine fabric and complete declared air volumes. Current native observations win. No
 * private navigator, resource, placement executor or repair permission lives here.
 */
final class AuthoredBuildingKnownGeometry implements KnownSpatialGeometry.Provider {
    private record Coverage(BuildingVolume volume, int airFromY) {}

    private record Image(
            RegionalBlockMap<NavigationCell> cells,
            Map<TerrainColumn, List<BlockPosition>> columns,
            Map<Region, Set<SubjectId>> owners,
            Map<SubjectId, Coverage> coverage) {
        Optional<NavigationCell> declaration(BlockPosition position) {
            var solid = cells.get(position);
            if (solid != null) return Optional.of(solid);
            for (var owner : owners.getOrDefault(Region.at(position), Set.of())) {
                var part = coverage.get(owner);
                if (position.y() >= part.airFromY() && part.volume().contains(position))
                    return Optional.of(NavigationCell.AIR);
            }
            return Optional.empty();
        }
    }

    private final ImmutableInputView<Image> images = new ImmutableInputView<>();
    private final ImmutableInputView<RegionalBlockSet> mutations = new ImmutableInputView<>();
    private final ImmutableInputView<KnownSpatialGeometry.Snapshot> snapshots =
            new ImmutableInputView<>();
    private final ImmutableInputView<Set<NavigationBlockState>> palettes =
            new ImmutableInputView<>();

    @Override
    public KnownSpatialGeometry.Key key() {
        return KnownSpatialGeometry.Key.AUTHORED_BUILDINGS;
    }

    private static List<SettlementStructure> buildings(FrontierBootstrap bootstrap) {
        return bootstrap.settlements().stream()
                .flatMap(s -> s.structures().stream())
                .filter(
                        s ->
                                s.definition().representation()
                                        == BuildingDefinition.Representation.AUTHORED)
                .toList();
    }

    @Override
    public Set<NavigationBlockState> palette(FrontierWorldState state) {
        return palettes.get(
                List.of(state.bootstrap()),
                () -> {
                    var values = new HashSet<NavigationBlockState>();
                    buildings(state.bootstrap())
                            .forEach(
                                    s ->
                                            BuildingSpatialPlan.of(s)
                                                    .blocks()
                                                    .values()
                                                    .forEach(
                                                            cell ->
                                                                    values.add(
                                                                            ((StructureMaterial
                                                                                                    .Authored)
                                                                                            cell
                                                                                                    .material())
                                                                                    .block())));
                    return Set.copyOf(values);
                });
    }

    @Override
    public KnownSpatialGeometry.Snapshot snapshot(FrontierWorldState state) {
        var image = images.get(List.of(state.bootstrap()), () -> image(state.bootstrap()));
        return snapshots.get(
                List.of(
                        image,
                        state.structureConditions(),
                        state.navigationGeometry(),
                        state.physicalDeltas()),
                () ->
                        new Snapshot(
                                image,
                                state.structureConditions(),
                                state.navigationGeometry(),
                                mutations.get(
                                        List.of(state.physicalDeltas()),
                                        () ->
                                                RegionalBlockSet.copyOf(
                                                        state.physicalDeltas().keySet()))));
    }

    private static Image image(FrontierBootstrap bootstrap) {
        var cells = new HashMap<BlockPosition, NavigationCell>();
        var owners = new HashMap<Region, Set<SubjectId>>();
        var coverage = new HashMap<SubjectId, Coverage>();
        for (var structure : buildings(bootstrap)) {
            var plan = BuildingSpatialPlan.of(structure);
            var bounds = plan.placement().volume(plan.definition().fabric());
            coverage.put(structure.id(), new Coverage(bounds, structure.anchor().y()));
            for (int x = bounds.min().x() >> 4; x <= (bounds.max().x() - 1) >> 4; x++)
                for (int z = bounds.min().z() >> 4; z <= (bounds.max().z() - 1) >> 4; z++)
                    owners.computeIfAbsent(new Region(x, z), ignored -> new HashSet<>())
                            .add(structure.id());
            plan.blocks()
                    .forEach(
                            (position, block) -> {
                                var material = (StructureMaterial.Authored) block.material();
                                var fact =
                                        BuildingCollisionCatalog.standard(material.block())
                                                .flatMap(NavigationObservation::cell)
                                                .orElseThrow(
                                                        () ->
                                                                new IllegalStateException(
                                                                        "authored content lacks"
                                                                            + " compiled native"
                                                                            + " collision at "
                                                                                + position));
                                var prior = cells.putIfAbsent(position, fact);
                                if (prior != null && !prior.equals(fact))
                                    throw new IllegalArgumentException(
                                            "conflicting authored fabric at " + position);
                            });
        }
        var columns = new HashMap<TerrainColumn, List<BlockPosition>>();
        cells.forEach(
                (p, c) -> {
                    if (!c.shape().boxes().isEmpty())
                        columns.computeIfAbsent(
                                        new TerrainColumn(p.x(), p.z()),
                                        ignored -> new ArrayList<>())
                                .add(p);
                });
        columns.replaceAll((k, v) -> List.copyOf(v));
        owners.replaceAll((k, v) -> Set.copyOf(v));
        return new Image(
                RegionalBlockMap.copyOf(cells),
                Collections.unmodifiableMap(new HashMap<>(columns)),
                Map.copyOf(owners),
                Map.copyOf(coverage));
    }

    private record Snapshot(
            Image image,
            Map<SubjectId, StructureCondition> conditions,
            NavigationGeometryState observations,
            RegionalBlockSet deltas)
            implements KnownSpatialGeometry.Snapshot {
        @Override
        public Set<Region> regions() {
            return image.owners().keySet();
        }

        @Override
        public Optional<Object> token(Region region) {
            var owners = image.owners().get(region);
            if (owners == null) return Optional.empty();
            return Optional.of(
                    List.of(
                            image.cells().token(region),
                            owners.stream()
                                    .sorted()
                                    .map(id -> Map.entry(id, conditions.get(id)))
                                    .toList()));
        }

        @Override
        public Set<Region> changesFrom(KnownSpatialGeometry.Snapshot previous) {
            if (!(previous instanceof Snapshot old) || image != old.image)
                throw new IllegalArgumentException("building geometry requires one world lineage");
            if (conditions == old.conditions) return Set.of();
            var changed = new HashSet<Region>();
            image.owners()
                    .forEach(
                            (r, ids) -> {
                                if (ids.stream()
                                        .anyMatch(
                                                id -> conditions.get(id) != old.conditions.get(id)))
                                    changed.add(r);
                            });
            return Set.copyOf(changed);
        }

        @Override
        public BoundedPedestrianApproach.SurveyedSurface overlay(
                BoundedPedestrianApproach.SurveyedSurface base) {
            if (image.cells().isEmpty()) return base;
            return KnownBlockSurfaceOverlay.apply(
                    base,
                    p ->
                            image.declaration(p)
                                    .map(
                                            cell ->
                                                    new KnownBlockSurfaceOverlay.Cell(
                                                            cell,
                                                            KnownBlockSurfaceOverlay.Precedence
                                                                    .OBSERVATION_FIRST)),
                    c -> image.columns().getOrDefault(c, List.of()),
                    area -> NavigationRegionCoverage.intersects(image.owners().keySet(), area),
                    p -> {
                        if (observations.cells().containsKey(p))
                            return base.collisionAt(p).isPresent();
                        if (deltas.contains(p)) return false;
                        if (image.declaration(p).isEmpty()) return base.blockKnown(p);
                        return image.owners().get(Region.at(p)).stream()
                                .noneMatch(
                                        id ->
                                                image.coverage().get(id).volume().contains(p)
                                                        && conditions.get(id)
                                                                == StructureCondition.DESTROYED);
                    });
        }
    }
}
