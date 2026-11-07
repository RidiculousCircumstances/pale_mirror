package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.buildings.*;

import java.util.*;

/** Bounded genesis compiler. Exact content parcels, not nominal function sizes, allocate land. */
public final class SettlementLayoutPlanner {
    public record Request(SubjectId buildingId, StructureKind function, BuildingPlanRef content) {}

    public record Result(List<SettlementStructure> buildings, SettlementLayout layout) {
        public Result {
            buildings = List.copyOf(buildings);
            layout.requireBuildings(buildings);
        }
    }

    private record Input(
            long seed,
            SubjectId owner,
            BuildingStyleId style,
            BlockPosition center,
            TerrainSurfacePlan terrain,
            WorldBounds bounds,
            List<Request> requests) {
        private Input {
            requests = List.copyOf(requests);
        }
    }

    private static final BoundedValueMemo<Input, Result> PLANS = new BoundedValueMemo<>(64);

    public static Result plan(
            long seed,
            SubjectId settlementId,
            BuildingStyleId style,
            BlockPosition center,
            TerrainSurfacePlan terrain,
            WorldBounds world,
            List<Request> requests) {
        return PLANS.get(
                new Input(seed, settlementId, style, center, terrain, world, requests),
                () -> compile(seed, settlementId, style, center, terrain, world, requests));
    }

    private static Result compile(
            long seed,
            SubjectId settlementId,
            BuildingStyleId style,
            BlockPosition center,
            TerrainSurfacePlan terrain,
            WorldBounds world,
            List<Request> requests) {
        var policy = SettlementLayoutRules.standard();
        int variation = Math.floorMod(Objects.hash(seed, settlementId), 3);
        String scheme = List.of("crossroads", "spine", "courtyards").get(variation);
        int extent = policy.streetExtent(), half = policy.streetHalfWidth();
        var lines = new LinkedHashSet<TerrainColumn>();
        if (variation == 0) {
            line(lines, -extent, 0, extent, 0);
            line(lines, 0, -extent, 0, extent);
        } else if (variation == 1) {
            line(lines, 0, -extent, 0, extent);
            for (int z : new int[] {-extent / 2, extent / 2}) line(lines, -extent, z, extent, z);
        } else {
            int edge = extent / 2;
            line(lines, -edge, -edge, edge, -edge);
            line(lines, -edge, edge, edge, edge);
            line(lines, -edge, -extent, -edge, extent);
            line(lines, edge, -extent, edge, extent);
            line(lines, -extent, 0, extent, 0);
        }
        var road = new LinkedHashSet<BlockPosition>();
        for (var point : lines)
            for (int dx = -half; dx <= half; dx++)
                for (int dz = -half; dz <= half; dz++) {
                    int x = center.x() + point.x() + dx, z = center.z() + point.z() + dz;
                    var support = new BlockPosition(x, terrain.supportYAt(x, z), z);
                    if (!world.contains(support))
                        throw new IllegalArgumentException("settlement street outside world");
                    road.add(support);
                }
        // Keep departure corridors free before allocating plots. They are real initial
        // streets, not a runtime permission to tunnel through a later house.
        var fronts = new ArrayList<SurfaceAnchor>();
        for (var point : lines)
            if (Math.abs(point.x()) == extent || Math.abs(point.z()) == extent) {
                int dx = Math.abs(point.x()) > Math.abs(point.z()) ? Integer.signum(point.x()) : 0;
                int dz = dx == 0 ? Integer.signum(point.z()) : 0;
                for (int step = 1; step <= policy.departureLength(); step++)
                    for (int x = -half; x <= half; x++)
                        for (int z = -half; z <= half; z++) {
                            int wx = center.x() + point.x() + dx * step + x,
                                    wz = center.z() + point.z() + dz * step + z;
                            var support = new BlockPosition(wx, terrain.supportYAt(wx, wz), wz);
                            if (!world.contains(support))
                                throw new IllegalArgumentException(
                                        "settlement departure outside world");
                            road.add(support);
                        }
                int x = center.x() + point.x() + dx * policy.departureLength(),
                        z = center.z() + point.z() + dz * policy.departureLength();
                fronts.add(SurfaceAnchor.at(x, terrain.supportYAt(x, z), z));
            }
        var ordered = new ArrayList<>(lines);
        ordered.sort(
                Comparator.comparingInt((TerrainColumn p) -> Math.abs(p.x()) + Math.abs(p.z()))
                        .thenComparingInt(TerrainColumn::x)
                        .thenComparingInt(TerrainColumn::z));
        var buildings = new ArrayList<SettlementStructure>();
        var plots = new LinkedHashMap<SettlementLayout.PlotId, SettlementLayout.Plot>();
        for (var request : requests) {
            SettlementStructure chosen = null;
            SurfaceAnchor access = null;
            BuildingVolume parcel = null;
            for (var point : ordered) {
                if (chosen != null) break;
                for (var facing :
                        List.of(
                                FacilityFacing.NORTH,
                                FacilityFacing.SOUTH,
                                FacilityFacing.WEST,
                                FacilityFacing.EAST)) {
                    // Face the street; the whole parcel and its service yard lie beyond its curb.
                    var def =
                            BuildingCatalog.standard()
                                    .require(request.content(), request.function());
                    var relative =
                            def.placement(new BlockPosition(0, 0, 0), facing).volume(def.parcel());
                    int projection =
                            Math.max(
                                    Math.max(
                                            relative.min().x() * facing.x(),
                                            (relative.max().x() - 1) * facing.x()),
                                    Math.max(
                                            relative.min().z() * facing.z(),
                                            (relative.max().z() - 1) * facing.z()));
                    int distance = projection + half + policy.plotGap() + 1;
                    int x = center.x() + point.x() - facing.x() * distance,
                            z = center.z() + point.z() - facing.z() * distance;
                    var anchor = BuildingSitePlanner.anchor(terrain, def, x, z, facing);
                    var candidate =
                            new SettlementStructure(
                                    request.buildingId(),
                                    settlementId,
                                    request.function(),
                                    anchor,
                                    facing,
                                    request.content());
                    var bounds = candidate.placement().volume(def.parcel());
                    if (!world.contains(bounds.min())
                            || !world.contains(bounds.max().offset(-1, 0, -1))) continue;
                    if (plots.values().stream()
                            .anyMatch(p -> SettlementLayout.horizontalOverlap(p.bounds(), bounds)))
                        continue;
                    if (road.stream()
                            .anyMatch(
                                    p ->
                                            p.x() >= bounds.min().x()
                                                    && p.x() < bounds.max().x()
                                                    && p.z() >= bounds.min().z()
                                                    && p.z() < bounds.max().z())) continue;
                    if (!levelFootprint(terrain, candidate)) continue;
                    var street =
                            SurfaceAnchor.at(
                                    center.x() + point.x(),
                                    terrain.supportYAt(
                                            center.x() + point.x(), center.z() + point.z()),
                                    center.z() + point.z());
                    var frontage = frontageCells(candidate, street, terrain);
                    if (frontage.stream()
                            .anyMatch(
                                    p ->
                                            plots.values().stream()
                                                    .anyMatch(plot -> plot.bounds().contains(p))))
                        continue;
                    chosen = candidate;
                    parcel = bounds;
                    access = street;
                    break;
                }
            }
            if (chosen == null)
                throw new IllegalArgumentException("no buildable plot for " + request.buildingId());
            buildings.add(chosen);
            var id =
                    new SettlementLayout.PlotId(
                            "plot:" + request.buildingId().value().replace(':', '-'));
            plots.put(id, new SettlementLayout.Plot(id, request.buildingId(), parcel, access));
            road.addAll(frontageCells(chosen, access, terrain));
        }
        return connect(
                new Result(
                        buildings,
                        new SettlementLayout(settlementId, 1, scheme, style, plots, road, fronts)),
                terrain);
    }

    public static Result connect(Result plan, TerrainSurfacePlan terrain) {
        var road = new LinkedHashSet<>(plan.layout().paving());
        var buildings = plan.buildings();
        var plots = plan.layout().plots();
        // Connect every explicit public entrance to its retained street frontage. A connector
        // never routes through a neighbouring plot or grants clearance through building fabric.
        for (var building : buildings) {
            var plot =
                    plots.values().stream()
                            .filter(p -> p.buildingId().equals(building.id()))
                            .findFirst()
                            .orElseThrow();
            for (var p : frontageCells(building, plot.streetAccess(), terrain))
                addConnector(road, buildings, building, p, terrain);
        }

        var layout = plan.layout();
        return new Result(
                buildings,
                new SettlementLayout(
                        layout.settlementId(),
                        layout.revision(),
                        layout.scheme(),
                        layout.style(),
                        plots,
                        road,
                        layout.extensionFronts()));
    }

    private static Set<BlockPosition> frontageCells(
            SettlementStructure building, SurfaceAnchor street, TerrainSurfacePlan terrain) {
        var result = new LinkedHashSet<BlockPosition>();
        var end = street.support();
        for (var passage : building.definition().passages().values()) {
            if (passage.from().kind() != BuildingDefinition.EndpointKind.EXTERIOR
                    || !passage.publicAccess()) continue;
            var supports = passage.supports().stream().map(building.placement()::world).toList();
            result.addAll(supports.subList(0, passage.thresholdIndex()));
            var p = supports.getFirst();
            for (int guard = 0; !p.equals(end); guard++) {
                if (guard > 256)
                    throw new IllegalArgumentException("building frontage connector exceeds bound");
                result.add(p);
                int x = p.x(), z = p.z();
                if (building.facing().x() != 0) {
                    if (x != end.x()) x += Integer.signum(end.x() - x);
                    else z += Integer.signum(end.z() - z);
                } else {
                    if (z != end.z()) z += Integer.signum(end.z() - z);
                    else x += Integer.signum(end.x() - x);
                }
                p = new BlockPosition(x, terrain.supportYAt(x, z), z);
            }
            result.add(end);
        }
        return Set.copyOf(result);
    }

    static boolean levelFootprint(
            TerrainSurfacePlan terrain, SettlementStructure building) {
        try { BuildingSitePlan.compile(terrain, building); return true; }
        catch (BuildingSitePlan.GeometryPending pending) { throw pending; }
        catch (IllegalArgumentException unsuitable) { return false; }
    }

    private static void addConnector(
            Set<BlockPosition> road,
            List<SettlementStructure> buildings,
            SettlementStructure owner,
            BlockPosition p,
            TerrainSurfacePlan terrain) {
        for (var building : buildings) {
            if (!building.id().equals(owner.id())
                    && building.placement().volume(building.definition().parcel()).contains(p))
                throw new IllegalArgumentException("frontage crosses neighbouring plot");
            var blocks = BuildingSpatialPlan.of(building).blocks();
            for (int h = 1; h <= 2; h++) {
                var cell = blocks.get(p.offset(0, h, 0));
                if (cell != null && !cell.material().emptySpace()
                        && !cell.position()
                                .equals(
                                        BuildingSpatialPlan.of(owner)
                                                .primaryEntrance()
                                                .thresholdSurface()
                                                .support()
                                                .offset(0, h, 0)))
                    throw new IllegalArgumentException(
                            "frontage is obstructed at " + p + " by " + building.id()
                                    + " local " + building.placement().local(cell.position())
                                    + " material " + cell.material());
            }
        }
        road.add(p);
    }

    private static void line(Set<TerrainColumn> points, int x, int z, int endX, int endZ) {
        while (true) {
            points.add(new TerrainColumn(x, z));
            if (x == endX && z == endZ) break;
            if (x != endX) x += Integer.signum(endX - x);
            else z += Integer.signum(endZ - z);
        }
    }

    private SettlementLayoutPlanner() {}
}
