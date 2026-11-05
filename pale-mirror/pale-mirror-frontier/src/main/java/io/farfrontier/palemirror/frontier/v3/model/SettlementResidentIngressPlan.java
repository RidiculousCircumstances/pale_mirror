package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.*;

/** Public outdoor standing places and connected streets. A bed is not a spawn or idle position. */
public final class SettlementResidentIngressPlan {
    private static final ImmutableResultView<String, Plan> VIEWS =
            new ImmutableResultView<>(SimulationCapacity.MAX_SETTLEMENTS * 2);

    public static Plan compile(
            WorldBounds bounds, TerrainSurfacePlan terrain, Settlement settlement, int places) {
        if (places < 1 || places > SettlementBeds.declaredCapacity(settlement))
            throw new IllegalArgumentException("standing allocation exceeds declared beds");
        return VIEWS.get(
                List.of(bounds, terrain, settlement),
                "ingress",
                () ->
                        compile(
                                bounds,
                                terrain,
                                settlement.id(),
                                settlement.anchor(),
                                settlement.structures(),
                                settlement.layout(),
                                SettlementBeds.declaredCapacity(settlement)));
    }

    static Plan compile(
            WorldBounds bounds,
            TerrainSurfacePlan terrain,
            SubjectId id,
            BlockPosition anchor,
            List<SettlementStructure> structures,
            SettlementLayout layout,
            int places) {
        if (places < 1 || places > 96)
            throw new IllegalArgumentException("outdoor standing-place bound exceeded");
        var solids = FrontierSettlementActorSlots.intactStructureOccupancy(terrain, structures);
        var surfaces = new LinkedHashSet<SurfaceAnchor>();
        for (var p : layout.paving())
            if (bounds.contains(p)
                    && !solids.contains(p.offset(0, 1, 0))
                    && !solids.contains(p.offset(0, 2, 0))) surfaces.add(new SurfaceAnchor(p));
        var candidates =
                surfaces.stream()
                        .filter(s -> Math.floorMod(s.x(), 3) == 0 && Math.floorMod(s.z(), 3) == 0)
                        .filter(
                                s ->
                                        layout.plots().values().stream()
                                                .noneMatch(
                                                        plot -> {
                                                            var b = plot.bounds();
                                                            return s.x() >= b.min().x() - 2
                                                                    && s.x() < b.max().x() + 2
                                                                    && s.z() >= b.min().z() - 2
                                                                    && s.z() < b.max().z() + 2;
                                                        }))
                        .sorted(
                                Comparator.comparingInt(SurfaceAnchor::x)
                                        .thenComparingInt(SurfaceAnchor::z))
                        .toList();
        if (candidates.size() < places)
            throw new IllegalArgumentException(
                    "street plan lacks outdoor standing places for " + id);
        var homes = new ArrayList<BlockPosition>();
        for (int i = 0; i < places; i++)
            homes.add(candidates.get(i * candidates.size() / places).support());
        var topology = SettlementStreetTopology.of(layout);
        return new Plan(homes, surfaces, surfaces, Set.of(), topology);
    }

    public record Plan(
            List<BlockPosition> homeSlots,
            Set<SurfaceAnchor> perimeterSurfaces,
            Set<SurfaceAnchor> ownedSurfaces,
            Set<BlockPosition> foundationCells,
            TraversalTopology topology) {
        public Plan {
            homeSlots = List.copyOf(homeSlots);
            perimeterSurfaces = Set.copyOf(perimeterSurfaces);
            ownedSurfaces = Set.copyOf(ownedSurfaces);
            foundationCells = Set.copyOf(foundationCells);
            Objects.requireNonNull(topology);
        }
    }

    private SettlementResidentIngressPlan() {}
}
