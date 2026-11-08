package io.farfrontier.palemirror.frontier.v3.model.buildings;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.geometry.*;
import io.farfrontier.palemirror.frontier.v3.model.navigation.*;

import java.util.*;
import java.util.function.Function;

/**
 * Content-time validation on the ordinary collision policy and route search. No live world writes.
 */
public final class BuildingGeometryValidation {
    public static void validate(
            BuildingDefinition definition,
            Function<NavigationBlockState, Optional<NavigationCell>> shapes,
            Function<BuildingDefinition.Workplace, NavigationCell> equipmentShapes) {
        var view = geometry(definition, shapes, equipmentShapes);
        var geometry = view.walking();
        var fabricCollision = view.fabric();
        var identity = new SubjectId("authoring:geometry-validation");
        for (var passage : definition.passages().values()) {
            for (int i = 0; i < passage.supports().size(); i++) {
                var contact = new SurfaceAnchor(passage.supports().get(i));
                if (geometry.blocked(contact))
                    throw new IllegalArgumentException(
                            "blocked passage " + passage.id() + " at " + contact);
                if (i > 0
                        && !geometry.canTraverse(
                                new SurfaceAnchor(passage.supports().get(i - 1)), contact))
                    throw new IllegalArgumentException(
                            "non-traversable passage edge " + passage.id() + " at " + contact);
            }
        }
        for (var zone : definition.zones().values())
            if (zone.use() == BuildingDefinition.ZoneUse.EQUIPMENT)
                requireClearVolume(fabricCollision, zone.volume(), "equipment zone:" + zone.id());
        var start = new SurfaceAnchor(definition.primaryEntranceDefinition().supports().getFirst());
        for (var seat : definition.seats().values()) {
            for (var point : List.of(seat.approach(), seat.egress())) {
                var goal = new SurfaceAnchor(point);
                if (!geometry.clearStation(goal))
                    throw new IllegalArgumentException("blocked seat contact " + seat.id() + " at " + point);
                KnownPedestrianNavigation.route(geometry, start,
                        new MovementOrder(identity, identity, 0, 1, List.of(goal),
                                TraversalCapability.PEDESTRIAN, MovementOrder.ArrivalPolicy.EXACT_STATION));
            }
        }
        for (var work : definition.workplaces().values())
            for (var point : List.of(work.input(), work.worker())) {
                var goal = new SurfaceAnchor(point);
                if (!geometry.clearStation(goal))
                    throw new IllegalArgumentException(
                            "blocked workplace " + work.id() + " at " + point);
                KnownPedestrianNavigation.route(
                        geometry,
                        start,
                        new MovementOrder(
                                identity,
                                identity,
                                0,
                                1,
                                List.of(goal),
                                TraversalCapability.PEDESTRIAN,
                                MovementOrder.ArrivalPolicy.EXACT_STATION));
            }
    }

    public record View(PedestrianRouteGeometry walking, NavigationCollisionView fabric) { }

    /** Shared content geometry for validation and connected interaction-port discovery. */
    public static View geometry(BuildingDefinition definition,
            Function<NavigationBlockState, Optional<NavigationCell>> shapes,
            Function<BuildingDefinition.Workplace, NavigationCell> equipmentShapes) {
        var blocks = definition.blocks();
        var columns = new HashMap<TerrainColumn, List<SurfaceAnchor>>();
        var cells = new HashMap<BlockPosition, NavigationCell>();
        blocks.forEach(
                (p, b) -> {
                    var cell =
                            shapes.apply(b.state())
                                    .orElseThrow(
                                            () ->
                                                    new IllegalArgumentException(
                                                            "unknown native collision at "
                                                                    + p
                                                                    + ": "
                                                                    + b.state()));
                    cells.put(p, cell);
                    if (!cell.shape().boxes().isEmpty())
                        columns.computeIfAbsent(
                                        new TerrainColumn(p.x(), p.z()),
                                        ignored -> new ArrayList<>())
                                .add(new SurfaceAnchor(p));
                });
        var fabricCells = Map.copyOf(cells);
        NavigationCollisionView fabricCollision = p -> definition.parcel().contains(p)
                ? Optional.of(fabricCells.getOrDefault(p, NavigationCell.AIR)) : Optional.empty();
        for (var work : definition.workplaces().values()) {
            requireClearVolume(fabricCollision, work.equipment(), "equipment:" + work.id());
            requireClearVolume(fabricCollision, work.maintenance(), "maintenance:" + work.id());
            var position = work.socket().offset(0, 1, 0);
            var shape = Objects.requireNonNull(equipmentShapes.apply(work), "registered equipment geometry");
            cells.put(position, shape);
            if (!shape.shape().boxes().isEmpty()) columns.computeIfAbsent(
                    new TerrainColumn(position.x(), position.z()), ignored -> new ArrayList<>()).add(new SurfaceAnchor(position));
        }
        NavigationCollisionView collisions =
                p ->
                        definition.parcel().contains(p)
                                ? Optional.of(cells.getOrDefault(p, NavigationCell.AIR))
                                : Optional.empty();
        var parcel = definition.parcel();
        PedestrianRouteGeometry terrain =
                new PedestrianRouteGeometry() {
                    @Override
                    public WorldBounds bounds() {
                        return new WorldBounds(
                                parcel.min().x(),
                                parcel.min().z(),
                                parcel.max().x() - parcel.min().x(),
                                parcel.max().z() - parcel.min().z());
                    }

                    @Override
                    public SurfaceAnchor supportAt(int x, int z) {
                        var all = surfacesAt(x, z);
                        return all.isEmpty() ? null : all.getLast();
                    }

                    @Override
                    public List<SurfaceAnchor> surfacesAt(int x, int z) {
                        return columns.getOrDefault(new TerrainColumn(x, z), List.of());
                    }

                    @Override
                    public boolean supportAvailable(SurfaceAnchor s) {
                        return knowsSupport(s) && cells.containsKey(s.support())
                                && !cells.get(s.support()).shape().boxes().isEmpty();
                    }
                    @Override
                    public boolean blocked(SurfaceAnchor s) {
                        return NavigationBodyGeometry.contacts(
                                        collisions, s, UnitNavigationProfile.VILLAGER, .5, .5)
                                .isEmpty();
                    }

                    @Override
                    public boolean clearanceKnown(SurfaceAnchor s) {
                        return NavigationBodyGeometry.known(
                                collisions, s, UnitNavigationProfile.VILLAGER);
                    }

                    @Override
                    public Optional<NavigationCollisionView> collisions() {
                        return Optional.of(collisions);
                    }
                };
        var geometry =
                new ProfiledPedestrianGeometry(
                        terrain,
                        UnitNavigationProfile.VILLAGER,
                        NavigationObstacleView.indexed(Set.of()));
        return new View(geometry, fabricCollision);
    }

    private static void requireClearVolume(
            NavigationCollisionView collisions, BuildingVolume bounds, String label) {
        for (int x = bounds.min().x() - 1; x <= bounds.max().x(); x++)
            for (int y = bounds.min().y() - 1; y <= bounds.max().y(); y++)
                for (int z = bounds.min().z() - 1; z <= bounds.max().z(); z++) {
                    var cell =
                            collisions
                                    .cell(new BlockPosition(x, y, z))
                                    .orElseThrow(
                                            () ->
                                                    new IllegalArgumentException(
                                                            "unknown installation volume "
                                                                    + label));
                    if (!cell.dry())
                        throw new IllegalArgumentException("unsafe installation volume " + label);
                    for (var box : cell.shape().boxes())
                        if (box.overlaps(
                                bounds.min().x() - x,
                                bounds.min().y() - y,
                                bounds.min().z() - z,
                                bounds.max().x() - x,
                                bounds.max().y() - y,
                                bounds.max().z() - z))
                            throw new IllegalArgumentException(
                                    "occupied installation volume "
                                            + label
                                            + " at "
                                            + new BlockPosition(x, y, z));
                }
    }

    private BuildingGeometryValidation() {}
}
