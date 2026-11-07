package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.buildings.*;
import io.farfrontier.palemirror.frontier.v3.model.geometry.*;
import io.farfrontier.palemirror.frontier.v3.model.navigation.UnitNavigationProfile;

import java.util.*;

/** Shared plan admission for genesis and developer drafts; no mutation or navigation executor. */
public final class SettlementLayoutValidation {
    private record Pad(TerrainSurfacePlan terrain, WorldBounds bounds) {}

    private static final ImmutableResultView<Pad, Boolean> CHECKED = new ImmutableResultView<>(64);

    public static void validate(
            SettlementLayoutPlanner.Result plan, TerrainSurfacePlan terrain, WorldBounds world) {
        CHECKED.get(
                List.of(plan),
                new Pad(terrain, world),
                () -> {
                    validatePads(plan, terrain, world);
                    return Boolean.TRUE;
                });
    }

    private static void validatePads(
            SettlementLayoutPlanner.Result plan, TerrainSurfacePlan terrain, WorldBounds world) {
        for (var building : plan.buildings()) {
            var box = building.placement().volume(building.definition().parcel());
            if (!SettlementLayoutPlanner.levelFootprint(terrain, building)
                    || !world.contains(box.min())
                    || !world.contains(box.max().offset(-1, 0, -1)))
                throw new IllegalArgumentException(
                        "building pad disagrees with declared terrain/world: " + building.id());
        }
        var entranceSupports = plan.buildings().stream().flatMap(building ->
                BuildingSpatialPlan.of(building).publicEntrances().stream()).flatMap(port -> port.ingressSurfaces().stream())
                .map(SurfaceAnchor::support).collect(java.util.stream.Collectors.toSet());
        for (var p : plan.layout().paving())
            if (!world.contains(p) || p.y() != terrain.supportYAt(p.x(), p.z()) && !entranceSupports.contains(p))
                throw new IllegalArgumentException(
                        "paving must replace a surveyed ground surface: " + p);
        validate(plan);
    }

    public static void validate(SettlementLayoutPlanner.Result plan) {
        plan.layout().requireBuildings(plan.buildings());
        BuildingLayoutValidation.requireSeparateFabric(plan.buildings());
        var cells = new HashMap<BlockPosition, GrayboxCell>();
        for (var building : plan.buildings())
            for (var cell : BuildingSpatialPlan.of(building).blocks().values()) {
                if (cells.putIfAbsent(cell.position(), cell) != null)
                    throw new IllegalArgumentException("building fabric has competing owners");
            }
        var streets = plan.layout().paving();
        NavigationCollisionView geometry =
                p -> {
                    var authored = cells.get(p);
                    if (authored != null && !(streets.contains(p) && authored.material().emptySpace())) {
                        var block = ((StructureMaterial.Authored) authored.material()).block();
                        return BuildingCollisionCatalog.standard(block)
                                .flatMap(NavigationObservation::cell);
                    }
                    return Optional.of(
                            streets.contains(p) ? NavigationCell.SOLID : NavigationCell.AIR);
                };
        for (var point : streets)
            if (NavigationBodyGeometry.contacts(
                            geometry,
                            new SurfaceAnchor(point),
                            UnitNavigationProfile.VILLAGER,
                            .5,
                            .5)
                    .isEmpty() && NavigationBodyGeometry.edgeContacts(geometry, new SurfaceAnchor(point), UnitNavigationProfile.VILLAGER).isEmpty())
                throw new IllegalArgumentException("blocked/unsupported paving at " + point + "; support=" + describe(cells.get(point))
                        + "; feet=" + describe(cells.get(point.offset(0, 1, 0))) + "; head=" + describe(cells.get(point.offset(0, 2, 0))));
        var nodes = new HashMap<TerrainColumn, BlockPosition>();
        for (var p : streets)
            if (nodes.putIfAbsent(new TerrainColumn(p.x(), p.z()), p) != null)
                throw new IllegalArgumentException("street has competing surface heights");
        var queue = new ArrayDeque<BlockPosition>();
        var reached = new HashSet<BlockPosition>();
        queue.add(streets.iterator().next());
        while (!queue.isEmpty()) {
            var p = queue.remove();
            if (!reached.add(p)) continue;
            for (var d : FacilityFacing.values()) {
                var q = nodes.get(new TerrainColumn(p.x() + d.x(), p.z() + d.z()));
                if (q != null && Math.abs(p.y() - q.y()) <= 1 && !reached.contains(q)) queue.add(q);
            }
        }
        if (reached.size() != streets.size())
            throw new IllegalArgumentException("disconnected paving components: reached="+reached.size()
                    +" total="+streets.size()+" unreached="+streets.stream().filter(p -> !reached.contains(p))
                        .sorted(Comparator.comparingInt(BlockPosition::x).thenComparingInt(BlockPosition::z))
                        .limit(8).toList());
        for (var building : plan.buildings())
            for (var port : BuildingSpatialPlan.of(building).publicEntrances())
                if (!reached.contains(port.exteriorApproach().getFirst().support()))
                    throw new IllegalArgumentException(
                            "building entrance lacks a street connection: " + building.id());
    }

    private static String describe(GrayboxCell cell) {
        return cell == null ? "empty" : cell.ownerId().value() + "/" + ((StructureMaterial.Authored) cell.material()).block().kind();
    }
    private SettlementLayoutValidation() {}
}
