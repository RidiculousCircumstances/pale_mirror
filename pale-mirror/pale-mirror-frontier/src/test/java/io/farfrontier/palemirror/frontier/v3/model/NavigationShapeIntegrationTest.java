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
    @Test void recessedPathEntersTheLowEdgeOfAStairWithoutCrossingAHeadObstacle() {
        var stair=new NavigationCell(new NavigationShape(List.of(
                new NavigationShape.Box(0,0,0,1,.5,1),new NavigationShape.Box(.5,.5,0,1,1,1))),true);
        var path=new NavigationCell(new NavigationShape(List.of(new NavigationShape.Box(0,0,0,1,.9375,1))),true);
        var from=SurfaceAnchor.at(20,63,20);var to=SurfaceAnchor.at(21,64,20);
        for(boolean blocked:List.of(false,true)) {
            PedestrianRouteGeometry raw=new PedestrianRouteGeometry() {
                public WorldBounds bounds(){return new WorldBounds(18,18,8,8);}
                public SurfaceAnchor supportAt(int x,int z){return x==21&&z==20?to:SurfaceAnchor.at(x,63,z);}
                public boolean blocked(SurfaceAnchor surface){return false;}
                public Optional<NavigationCollisionView> collisions(){return Optional.of(p -> Optional.of(
                        p.equals(to.support())?stair:p.equals(from.support())?path:
                        blocked&&p.equals(to.support().offset(0,1,0))?NavigationCell.SOLID:
                        p.y()<=63?NavigationCell.SOLID:NavigationCell.AIR));}
            };
            var geometry=new ProfiledPedestrianGeometry(raw,UnitNavigationProfile.VILLAGER,ignored -> false);
            assertEquals(!blocked,geometry.canTraverse(from,to));
            assertEquals(!blocked,geometry.canTraverse(to,from));
        }
    }
    @Test void manualPassageRetainsActualCollisionAndExplicitTraversalCapabilityThroughRecovery() {
        var state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:manual-passage"), 41));
        var position = new BlockPosition(20, 64, 20);
        var closed = new NavigationCell(NavigationShape.FULL, true,
                Optional.of(new NavigationBlockState("fixture:manual-passage", Map.of())), Optional.of(NavigationShape.EMPTY));
        state = new NavigationGeometryObserved(0, Map.of(position, NavigationObservation.known(closed)))
                .apply(state, NavigationGeometryObserved.OWNER);
        var codec = new FrontierWorldStateCodec(); var recovered = codec.decode(codec.encode(state));
        assertEquals(closed, recovered.navigationGeometry().cells().get(position).cell().orElseThrow());
        assertEquals(NavigationShape.FULL, closed.shape(), "planning cannot rewrite the observed physical shape");
        assertEquals(NavigationShape.EMPTY, closed.passageTransit().shape());
        assertTrue(recovered.actorLocations().values().stream().filter(a -> a.kind() == ActorKind.RESIDENT)
                .allMatch(a -> a.navigationProfile().operatesPassages()));
        assertFalse(UnitNavigationProfile.DONKEY.operatesPassages());
        assertFalse(UnitNavigationProfile.ZOMBIE.operatesPassages());
        PedestrianRouteGeometry raw = new PedestrianRouteGeometry() {
            public WorldBounds bounds() { return new WorldBounds(18,18,5,5); }
            public SurfaceAnchor supportAt(int x, int z) { return SurfaceAnchor.at(x,63,z); }
            public boolean blocked(SurfaceAnchor surface) { return false; }
            public Optional<NavigationCollisionView> collisions() { return Optional.of(p -> Optional.of(p.y()==63
                    ? NavigationCell.SOLID : p.x()==20 && p.z()==20 && (p.y()==64 || p.y()==65) ? closed : NavigationCell.AIR)); }
        };
        var station = SurfaceAnchor.at(20,63,20);
        var operatorGeometry = new ProfiledPedestrianGeometry(raw, UnitNavigationProfile.VILLAGER, ignored -> false);
        assertTrue(operatorGeometry.clearBody(station), "a capable traveller can plan through a manual passage");
        assertFalse(operatorGeometry.clearStation(station), "conditional transit never grants exact physical placement");
        var nonOperator = new UnitNavigationProfile(600_000,1_950_000,1,1,Set.of(TraversalCapability.PEDESTRIAN));
        assertFalse(new ProfiledPedestrianGeometry(raw, nonOperator, ignored -> false).clearStation(station),
                "body size alone must not grant interaction capability");
        byte[] unsupported = codec.encode(recovered); unsupported[4] = 1; unsupported[5] = 80; // schema336 lacks these declarations
        assertThrows(IllegalArgumentException.class, () -> codec.decode(unsupported));
    }
    @Test void warmedBoundsCheckRejectsChangedGeometryAndDoesNotPoisonItsPredecessor() {
        var initial = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:geometry-bounds"), 41));
        var bounds = initial.bootstrap().bounds(); var inside = new BlockPosition(bounds.minX(), 60, bounds.minZ());
        var state = initial.withChanges(FrontierWorldStateUpdate.begin().navigationGeometry(new NavigationGeometryState(1,
                Map.of(inside, NavigationObservation.known(NavigationCell.SOLID)))));
        var outside = new BlockPosition(bounds.maxXExclusive(), 60, bounds.minZ());
        var invalid = new NavigationGeometryState(2, Map.of(outside, NavigationObservation.known(NavigationCell.SOLID)));
        assertThrows(IllegalArgumentException.class, () -> state.withChanges(FrontierWorldStateUpdate.begin().navigationGeometry(invalid)));
        assertEquals(state, state.withChanges(FrontierWorldStateUpdate.begin().navigationGeometry(state.navigationGeometry())));
        var codec = new FrontierWorldStateCodec(); assertEquals(state, codec.decode(codec.encode(state)));
    }
    @Test void backgroundShapeSnapshotCannotEvictUnchangedOwnerProjection() throws Exception {
        var point = new BlockPosition(20, 64, 20);
        var partial = new NavigationGeometryState(0, Map.of(point, NavigationObservation.known(SLAB)), Map.of());
        var full = new NavigationGeometryState(1, Map.of(point, NavigationObservation.known(NavigationCell.SOLID)), Map.of());
        var owner = RetainedNavigationGeometry.facts(partial, Map.of());
        var worker = new java.util.concurrent.FutureTask<Void>(() -> {
            assertEquals(owner, RetainedNavigationGeometry.facts(partial, Map.of()));
            assertEquals(Optional.of(NavigationCell.SOLID), RetainedNavigationGeometry.facts(full, Map.of()).get(point));
            return null;
        });
        var thread = new Thread(worker); thread.start(); worker.get(); thread.join();
        assertSame(owner, RetainedNavigationGeometry.facts(partial, Map.of()), "background input cannot force the owner to rebuild unchanged facts");
        assertEquals(Optional.of(SLAB), owner.get(point));
        assertEquals(Optional.of(NavigationCell.SOLID), RetainedNavigationGeometry.facts(full, Map.of()).get(point));
    }
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
        var revision = PedestrianGeometryRevision.initial();
        var geometry = new ProfiledPedestrianGeometry(KnownPedestrianNavigation.geometry(bootstrap, Set.of(), overlay, revision, Optional.of(revision)),
                UnitNavigationProfile.VILLAGER, ignored -> false);
        assertTrue(geometry.canTraverse(from, to));
        var missing = observations.observe(1, Map.of(from.support().offset(0, 1, 0), NavigationObservation.unavailable(NavigationObservation.Kind.WRITE_PENDING)));
        var changed = revision.changed(Set.of(new PedestrianGeometryRevision.Region(1, 1)));
        var unknown = new ProfiledPedestrianGeometry(KnownPedestrianNavigation.geometry(bootstrap, Set.of(),
                RetainedNavigationGeometry.overlay(base, missing, Map.of()), changed, Optional.of(changed)), UnitNavigationProfile.VILLAGER, ignored -> false);
        assertFalse(unknown.clearanceKnown(from));
        assertTrue(geometry.canTraverse(from, to), "reading a newer unknown revision cannot poison the captured predecessor's facts");
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
