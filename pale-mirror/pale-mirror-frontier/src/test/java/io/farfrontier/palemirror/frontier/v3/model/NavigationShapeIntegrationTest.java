package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.geometry.*;
import io.farfrontier.palemirror.frontier.v3.model.navigation.*;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NavigationShapeIntegrationTest {
    @Test void typedUnknownAndNeighbourDependenciesSurviveCodecAndColdChangesInvalidateCapturedShapes() {
        var state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:context-shape"), 41));
        var origin = new BlockPosition(20, 64, 20); var neighbour = origin.offset(1, 0, 0);
        var stone = new NavigationBlockState("minecraft:stone", Map.of());
        var air = new NavigationBlockState("minecraft:air", Map.of());
        var evidence = NavigationObservation.known(new NavigationCell(NavigationShape.FULL, true, Optional.of(stone)),
                Map.of(neighbour, Optional.of(stone)));
        var update = new NavigationGeometryObserved(0, Map.of(origin, evidence));
        var codecs = FrontierWorldRuntimeDefinition.payloadCodecs();
        assertEquals(update, codecs.decode(update.type(), codecs.encode(update)));
        state = update.apply(state, NavigationGeometryObserved.OWNER);
        var codec = new FrontierWorldStateCodec(); state = codec.decode(codec.encode(state));
        assertEquals(evidence, state.navigationGeometry().cells().get(origin));
        var base = (BoundedPedestrianApproach.SurveyedSurface) (x, z) -> SurfaceAnchor.at(x, 63, z);
        var before = RetainedNavigationGeometry.overlay(base, state.navigationGeometry(), Map.of(origin, stone, neighbour, stone));
        assertTrue(before.collisionAt(origin).isPresent());
        var after = RetainedNavigationGeometry.overlay(base, state.navigationGeometry(), Map.of(origin, stone, neighbour, air));
        assertFalse(after.blockKnown(origin), "COLD neighbour change must not reuse the previous physical shape");
        var replaced = RetainedNavigationGeometry.overlay(base, state.navigationGeometry(), Map.of(origin, air, neighbour, air));
        assertTrue(replaced.blockKnown(origin), "a replaced source cannot inherit its old contextual shape's unknown fence");
        for (var kind : NavigationObservation.Kind.values()) if (kind != NavigationObservation.Kind.KNOWN) {
            var change = new NavigationGeometryObserved(1, Map.of(origin, NavigationObservation.unavailable(kind)));
            assertEquals(change, codecs.decode(change.type(), codecs.encode(change)));
        }
        assertThrows(IllegalArgumentException.class, () -> NavigationObservation.known(NavigationCell.AIR,
                Map.of(neighbour, Optional.empty())));
    }
    @Test void knownFluidBesideFarmlandDoesNotInvalidateDryContactButCannotBeCrossed() {
        var support = SurfaceAnchor.at(0, 63, 0);
        var farmland = new NavigationCell(new NavigationShape(List.of(new NavigationShape.Box(0, 0, 0, 1, .9375, 1))), true);
        var water = new NavigationCell(NavigationShape.FULL, false);
        NavigationCollisionView view = p -> Optional.of(p.equals(new BlockPosition(1, 63, 0)) ? water
                : p.y() == 63 ? farmland : NavigationCell.AIR);
        assertTrue(NavigationBodyGeometry.known(view, support, UnitNavigationProfile.VILLAGER));
        assertFalse(NavigationBodyGeometry.contacts(view, support, UnitNavigationProfile.VILLAGER, .5, .5).isEmpty());
        assertTrue(NavigationBodyGeometry.contacts(view, SurfaceAnchor.at(1, 63, 0), UnitNavigationProfile.VILLAGER, .5, .5).isEmpty());
    }
    private static final NavigationCell SLAB = new NavigationCell(new NavigationShape(List.of(
            new NavigationShape.Box(0, 0, 0, 1, .5, 1))), true);
    @Test void declaredColdShapesUseTheRegisteredProcessAndStableCodecWithoutLoadedCells() {
        var state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:shape-catalog"), 41));
        var block = new NavigationBlockState("minecraft:stone_slab", Map.of("type", "bottom", "waterlogged", "false"));
        var shape = new NavigationCell(SLAB.shape(), true, Optional.of(block));
        var update = new NavigationGeometryObserved(0, Map.of(), Map.of(block, NavigationObservation.known(shape)));
        var registry = FrontierWorldRuntimeDefinition.processRegistry();
        var owner = registry.requireCommandOwner(update.type());
        assertEquals("navigation-geometry", owner);
        assertEquals(owner, registry.requireReducedEventOwner(update.type()));
        var codecs = FrontierWorldRuntimeDefinition.payloadCodecs();
        var decoded = codecs.decode(update.type(), codecs.encode(update)); assertEquals(update, decoded);
        var commandId = new CommandId("command:shape-catalog");
        var command = new FrontierCommand(FrontierCommand.SCHEMA_VERSION, commandId, state.bootstrap().worldId(),
                new Revision(0), new SimInstant(0), FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR, CauseChain.root(commandId), decoded);
        assertInstanceOf(io.farfrontier.palemirror.frontier.v3.kernel.CommandPlan.Accepted.class,
                io.farfrontier.palemirror.frontier.v3.process.FrontierWorldProcessCatalog.planCommand(owner, state, command));
        var event = new FrontierEvent(FrontierEvent.SCHEMA_VERSION, new EventId("event:shape-catalog"),
                new TransactionId("transaction:shape-catalog"), state.bootstrap().worldId(), new Revision(1), new SimInstant(0),
                NavigationGeometryObserved.OWNER, CauseChain.root(commandId), decoded);
        var changed = io.farfrontier.palemirror.frontier.v3.process.FrontierWorldProcessCatalog.reduce(owner, state, event);
        var codec = new FrontierWorldStateCodec(); var recovered = codec.decode(codec.encode(changed));
        assertEquals(changed.navigationGeometry(), recovered.navigationGeometry());
        var source = new BlockPosition(20, 64, 20);
        var base = (BoundedPedestrianApproach.SurveyedSurface) (x, z) -> SurfaceAnchor.at(x, 64, z);
        var overlay = RetainedNavigationGeometry.overlay(base, recovered.navigationGeometry(), Map.of(source, block));
        assertEquals(Optional.of(shape), overlay.collisionAt(source));
        assertTrue(recovered.navigationGeometry().cells().isEmpty(), "COLD catalog needs no physical visit");
        assertThrows(IllegalArgumentException.class, () -> new NavigationGeometryState(0, Map.of(), Map.of(block, NavigationObservation.known(NavigationCell.SOLID))));
        var old = codec.encode(changed); old[4] = 1; old[5] = 31; // old schema287
        assertThrows(IllegalArgumentException.class, () -> codec.decode(old));
    }
    @Test void widthAndOverheadGeometryAreIndependentOfSupportAndShapeDefinitions() {
        var support = SurfaceAnchor.at(0, 64, 0);
        NavigationCollisionView passage = p -> Optional.of(p.y() == 64 || Math.abs(p.z()) == 1 && p.y() >= 65 && p.y() <= 66
                ? NavigationCell.SOLID : NavigationCell.AIR);
        assertFalse(NavigationBodyGeometry.contacts(passage, support, UnitNavigationProfile.VILLAGER, .5, .5).isEmpty());
        assertTrue(NavigationBodyGeometry.contacts(passage, support, UnitNavigationProfile.DONKEY, .5, .5).isEmpty());
        NavigationCollisionView ceiling = p -> p.equals(new BlockPosition(0, 66, 0)) ? Optional.of(SLAB) : passage.cell(p);
        assertTrue(NavigationBodyGeometry.contacts(ceiling, support, UnitNavigationProfile.VILLAGER, .5, .5).isEmpty());
    }
    @Test void fractionalContactAndSweptVolumeUseRealShapeNotWholeOccupiedVoxel() {
        var from = SurfaceAnchor.at(0, 64, 0); var to = SurfaceAnchor.at(1, 64, 0);
        NavigationCollisionView view = p -> Optional.of(p.equals(from.support()) ? SLAB
                : p.y() == 64 ? NavigationCell.SOLID : NavigationCell.AIR);
        var start = NavigationBodyGeometry.contacts(view, from, UnitNavigationProfile.VILLAGER, .5, .5).getFirst();
        var end = NavigationBodyGeometry.contacts(view, to, UnitNavigationProfile.VILLAGER, .5, .5).getFirst();
        assertEquals(64.5, start.feetY()); assertEquals(65, end.feetY());
        assertTrue(NavigationBodyGeometry.traversable(view, UnitNavigationProfile.VILLAGER, start, end));
        var thinWall = new NavigationCell(new NavigationShape(List.of(new NavigationShape.Box(.05, 0, 0, .07, 1, 1))), true);
        NavigationCollisionView blocked = p -> p.equals(new BlockPosition(1, 65, 0)) ? Optional.of(thinWall) : view.cell(p);
        assertFalse(NavigationBodyGeometry.traversable(blocked, UnitNavigationProfile.VILLAGER, start, end), "continuous sweep must see a thin wall");
    }
    @Test void shapeObservationSurvivesOrdinaryStateUpdatesAndRecoveryAndInvalidatesOnlyLocalGraphs() {
        var state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:shapes"), 41));
        var position = new BlockPosition(20, 64, 20);
        var observed = new NavigationGeometryObserved(0, Map.of(position, NavigationObservation.known(SLAB)));
        state = observed.apply(state, NavigationGeometryObserved.OWNER);
        var unchanged = state.withChanges(FrontierWorldStateUpdate.begin().inventory(state.inventory()));
        assertSame(state.navigationGeometry(), unchanged.navigationGeometry());
        var codec = new FrontierWorldStateCodec(); var recovered = codec.decode(codec.encode(unchanged));
        assertEquals(state.navigationGeometry(), recovered.navigationGeometry());
        var previous = recovered;
        assertThrows(IllegalArgumentException.class, () -> observed.apply(previous, NavigationGeometryObserved.OWNER));
        assertThrows(IllegalArgumentException.class, () -> observed.apply(previous, new SubjectId("foreign:geometry")));
        var before = KnownPedestrianRouteKnowledge.forFrontier(recovered).geometry().regionalRevision().orElseThrow();
        var next = new NavigationGeometryObserved(1, Map.of(position, NavigationObservation.known(NavigationCell.AIR))).apply(recovered, NavigationGeometryObserved.OWNER);
        var after = KnownPedestrianRouteKnowledge.forFrontier(next).geometry().regionalRevision().orElseThrow();
        assertTrue(after.changesSince(before).contains(new PedestrianGeometryRevision.Region(1, 1)));
        assertFalse(after.changesSince(before).contains(new PedestrianGeometryRevision.Region(20, 20)));
    }
    @Test void retainedOverlayDrivesTheActualProfiledPlannerAndUnknownNeverMeansAir() {
        var bootstrap = FrontierBootstrapper.create(new WorldId("frontier:shape-route"), 41);
        var from = SurfaceAnchor.at(20, 64, 20); var to = SurfaceAnchor.at(21, 64, 20);
        var observations = NavigationGeometryState.empty().observe(0, Map.of(from.support(), NavigationObservation.known(SLAB)));
        var base = (BoundedPedestrianApproach.SurveyedSurface) (x, z) -> SurfaceAnchor.at(x, 64, z);
        var overlay = RetainedNavigationGeometry.overlay(base, observations, Map.of());
        var geometry = new ProfiledPedestrianGeometry(KnownPedestrianNavigation.geometry(bootstrap, Set.of(), overlay),
                UnitNavigationProfile.VILLAGER, ignored -> false);
        assertTrue(geometry.canTraverse(from, to));
        var missing = observations.observe(1, Map.of(from.support().offset(0, 1, 0), NavigationObservation.unavailable(NavigationObservation.Kind.WRITE_PENDING)));
        var unknown = new ProfiledPedestrianGeometry(KnownPedestrianNavigation.geometry(bootstrap, Set.of(),
                RetainedNavigationGeometry.overlay(base, missing, Map.of())), UnitNavigationProfile.VILLAGER, ignored -> false);
        assertFalse(unknown.clearanceKnown(from));
        var failure = assertThrows(KnownPedestrianNavigation.RouteUnavailable.class, () -> KnownPedestrianNavigation.plannedBetween(unknown, from, to));
        assertEquals(PedestrianRouteResult.Status.UNKNOWN_GEOMETRY, failure.status());
    }
    @Test void coldSourceTransitionCannotBeOverwrittenByOldPhysicalShape() {
        var p = new BlockPosition(20, 64, 20);
        var stone = new NavigationBlockState("minecraft:stone", Map.of());
        var air = new NavigationBlockState("minecraft:air", Map.of());
        var samples = NavigationGeometryState.empty().observe(0, Map.of(p, NavigationObservation.known(new NavigationCell(NavigationShape.FULL, true, Optional.of(stone)))));
        var base = (BoundedPedestrianApproach.SurveyedSurface) (x, z) -> SurfaceAnchor.at(x, 63, z);
        var overlay = RetainedNavigationGeometry.overlay(base, samples, Map.of(p, air));
        assertTrue(overlay.collisionAt(p).isEmpty());
        assertFalse(overlay.surfacesAt(20, 20).contains(new SurfaceAnchor(p)));
    }
}
