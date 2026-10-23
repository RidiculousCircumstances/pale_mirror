package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.model.navigation.ActorMovementColdAdvanced;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ServiceAccessCapabilitiesTest {
    @Test void coldIncomingWorkCannotDeadlockAnIncumbentsExitButParkingAndPhysicalBodiesRemainExclusive() {
        // This tests service transit, not a full field cycle and its independent work clock.
        var state = ProductionProcessTest.productionTask(FrontierWorldState.initial(FrontierBootstrapper.create(
                new io.farfrontier.palemirror.frontier.v3.api.WorldId("frontier:service-transit"), 91L)), StrategicTaskStatus.PENDING);
        var task = state.strategicPlans().tasks().values().iterator().next();
        var started = io.farfrontier.palemirror.frontier.v3.process.ProductionProcess.planStart(state,
                io.farfrontier.palemirror.frontier.v3.process.ProductionProcess.start(task, 200L)).stream()
                .map(io.farfrontier.palemirror.frontier.v3.api.ProposedEvent::payload)
                .filter(ProductionStarted.class::isInstance).map(ProductionStarted.class::cast).findFirst().orElseThrow();
        state = io.farfrontier.palemirror.frontier.v3.process.StrategicObjectiveProcess.reduceTaskTransition(state,
                task.ownerId(), new StrategicTaskTransition(task.id(), StrategicTaskStatus.ACTIVE));
        state = io.farfrontier.palemirror.frontier.v3.process.ProductionProcess.reduceStarted(state, task.ownerId(), started);
        var job = started.job(); var pointId = StationWorkStorage.source(job);
        var point = ServiceBoundaryComposition.geometry(state, ServiceBoundaryComposition.declaration(state, pointId).identity());
        var exit = point.egressSurfaces().stream().sorted(java.util.Comparator.comparingInt(SurfaceAnchor::x)
                .thenComparingInt(SurfaceAnchor::z)).findFirst().orElseThrow();
        var actor = state.humanPopulation().residents().values().stream()
                .filter(resident -> !resident.id().equals(job.workerId())).findFirst().orElseThrow().id();
        state = state.withActorBody(job.workerId(), exit.standingBody());
        assertTrue(ServiceAccessCapabilities.current(state, pointId).stream().anyMatch(demand ->
                demand.identity().actorId().equals(job.workerId()) && demand.presence() == ServiceAccessDemand.Presence.APPROACH));
        assertTrue(ServiceDestinationClaims.excludedFor(state, actor).contains(exit), "parking still reserves distinct final places");
        assertFalse(ServiceDestinationClaims.forImmediateExit(state, actor, pointId).contains(exit),
                "an incoming COLD worker waiting for the incumbent is transit, not a physical wall");
        var stationary = state.withActorBody(actor, exit.standingBody());
        assertTrue(ServiceDestinationClaims.forImmediateExit(stationary, job.workerId(), pointId).contains(exit),
                "unassigned stationary occupants are not incoming service transit");
        var lease = SceneLease.forCause(new SceneLeaseId("lease:incoming-physical-body"), state.bootstrap().worldId(),
                new ProductionWorkSceneCause(job.id()), exit.support(), new io.farfrontier.palemirror.frontier.v3.api.SimInstant(300L),
                1L, SceneLeaseStatus.PREPARED, List.of(new SceneMember(job.workerId(),
                        SceneLease.deterministicEntityId(state.bootstrap().worldId(), job.workerId()))), java.util.Set.of(), java.util.Optional.empty());
        var physical = state.prepareSceneLease(lease);
        assertFalse(ActorExecutionCoordinator.coldAvailable(physical, job.workerId()));
        assertTrue(ServiceDestinationClaims.forImmediateExit(physical, actor, pointId).contains(exit),
                "physical or unresolved authority cannot be bypassed by abstract COLD clearance");
        var codec = new io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec();
        var recovered = codec.decode(codec.encode(state));
        assertFalse(ServiceDestinationClaims.forImmediateExit(recovered, actor, pointId).contains(exit));
    }
    @Test void temporaryFootprintIndexMatchesDeclaredGeometryAndTracksDamageWithoutCachingActors() {
        var initial = FrontierWorldState.initial(FrontierBootstrapper.create(
                new io.farfrontier.palemirror.frontier.v3.api.WorldId("frontier:temporary-footprint-index"), 91L));
        var points = ServiceBoundaryComposition.geometries(initial);
        for (var point : points) {
            var samples = new java.util.HashSet<SurfaceAnchor>(point.boundary().occupiedSurfaces());
            samples.addAll(point.waitingSurfaces()); samples.addAll(point.egressSurfaces());
            for (var surface : samples) {
                var nearby = ServiceBoundaryComposition.nearbyDeclarations(initial, new TerrainColumn(surface.x(), surface.z()));
                assertTrue(nearby.size() < points.size(), "local turnover cannot require every world's service geometry");
                var expected = ServiceBoundaryComposition.operationalDeclarations(initial).values().stream()
                        .sorted(java.util.Comparator.<ServiceBoundaryProvider.Declaration, Boolean>comparing(value ->
                                !value.boundary().occupied(surface.standingBody()))
                                .thenComparing(ServiceBoundaryProvider.Declaration::pointId))
                        .filter(value -> ServiceAreaDestinations.temporary(
                                ServiceBoundaryComposition.geometry(initial, value.identity()), surface)).findFirst();
                assertEquals(expected, ServiceBoundaryComposition.turnoverPoint(initial, surface));
                assertTrue(expected.isEmpty() || nearby.contains(expected.orElseThrow()),
                        "declaration-only broad phase cannot omit a valid waiting/egress surface");
            }
        }
        var home = initial.bootstrap().settlements().getFirst();
        var actor = home.residents().getFirst().id();
        var point = ServiceBoundaryComposition.geometry(initial, ServiceBoundaryComposition.declaration(initial, FrontierWorldState.depotId(home.id())).identity());
        var waiting = point.waitingSurfaces().getFirst();
        var occupied = initial.withActorBody(actor, waiting.standingBody());
        assertSame(points, ServiceBoundaryComposition.geometries(occupied), "bodies are not geometry dependencies");
        assertTrue(ServiceAccessCoordinator.turnoverPoint(occupied, actor).isPresent());
        var farAway = occupied.withActorBody(actor, new SurfaceAnchor(home.anchor().offset(40, 0, 40)).standingBody());
        assertTrue(ServiceAccessCoordinator.turnoverPoint(farAway, actor).isEmpty(), "lookup reads the current actor body");
        var changed = initial.recordPhysicalDelta(new PhysicalDelta(waiting.support().offset(0, 1, 0),
                PhysicalDeltaKind.UNKNOWN_SCAR, java.util.Optional.empty(), java.util.Optional.empty(), "test obstacle"));
        assertNotSame(points, ServiceBoundaryComposition.geometries(changed));
        assertTrue(ServiceBoundaryComposition.turnoverPoint(changed, waiting).isEmpty(),
                "an obstructed old waiting surface cannot remain in the derived index");
        var codec = new io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec();
        var recovered = codec.decode(codec.encode(changed));
        assertTrue(ServiceBoundaryComposition.turnoverPoint(recovered, waiting).isEmpty());
        assertEquals(ServiceBoundaryComposition.geometries(changed), ServiceBoundaryComposition.geometries(recovered));
    }

    @Test void foreignVisitorWakesTheActualPointIncludingAfterItsExitContextIsRemoved() {
        var initial = FrontierWorldState.initial(FrontierBootstrapper.create(
                new io.farfrontier.palemirror.frontier.v3.api.WorldId("frontier:foreign-service-wake"), 20260918065L));
        var host = initial.bootstrap().settlements().get(9);
        var visitor = initial.bootstrap().settlements().get(10).residents().getFirst().id();
        var depot = host.structures().stream().filter(s -> s.kind() == StructureKind.DEPOT).findFirst().orElseThrow();
        var port = SettlementDepotServicePort.forDepot(depot);
        var point = FrontierWorldState.depotId(host.id());
        var atService = initial.withActorBody(visitor, port.serviceSurface().standingBody());
        assertTrue(ServiceAccessCoordinator.wakePoints(atService, visitor).contains(point),
                "a visitor's service footprint is independent of residence");
        var away = atService.withActorBody(visitor, port.serviceSurface().standingBody().offset(8, 0, 0));
        assertFalse(ServiceAccessCoordinator.wakePoints(away, visitor).contains(point));
        assertEquals(java.util.Set.of(point), ServiceAccessCoordinator.wakePoints(atService, away),
                "a collective transition addresses changed bodies even if its event subject is a group");
        assertTrue(ServiceAccessCoordinator.wakePoints(away, away).isEmpty());
        var order = new io.farfrontier.palemirror.frontier.v3.model.navigation.MovementOrder(visitor, visitor, 0, 1,
                List.of(away.actorLocations().get(visitor).supportingSurface()), TraversalCapability.PEDESTRIAN,
                io.farfrontier.palemirror.frontier.v3.model.navigation.MovementOrder.ArrivalPolicy.EXACT_STATION);
        var execution = away.actorExecutions().next(visitor,
                io.farfrontier.palemirror.frontier.v3.model.execution.ActorActivityKind.SERVICE_EXIT, visitor);
        var movement = new io.farfrontier.palemirror.frontier.v3.model.navigation.ActorMovement(order, 1,
                new io.farfrontier.palemirror.frontier.v3.model.navigation.ActorMovementContext.ServiceExit(host.id(), point), execution);
        var departing = away.withChanges(FrontierWorldStateUpdate.begin()
                .actorMovements(java.util.Map.of(visitor, movement))
                .actorExecutions(away.actorExecutions().begin(execution, 0)));
        var cause = io.farfrontier.palemirror.frontier.v3.api.CauseChain.root(
                new io.farfrontier.palemirror.frontier.v3.api.CommandId("command:foreign-exit"));
        var event = new io.farfrontier.palemirror.frontier.v3.api.FrontierEvent(1,
                new io.farfrontier.palemirror.frontier.v3.api.EventId("event:foreign-exit"),
                new io.farfrontier.palemirror.frontier.v3.api.TransactionId("transaction:foreign-exit"),
                initial.bootstrap().worldId(), io.farfrontier.palemirror.frontier.v3.api.Revision.ZERO.next(),
                new io.farfrontier.palemirror.frontier.v3.api.SimInstant(2), visitor, cause,
                new ActorMovementColdAdvanced(visitor, 1, 2, execution));
        var completed = io.farfrontier.palemirror.frontier.v3.process.ActorMovementProcess.reduceColdAdvanced(
                departing, visitor, (ActorMovementColdAdvanced) event.payload());
        assertFalse(completed.actorMovements().containsKey(visitor));
        assertTrue(io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition
                .wakeKeys(departing, completed, event).contains(point),
                "the removed exit still wakes its actual service point, not just the visitor's home depot");
        assertTrue(io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition
                .wakeKeys(atService, away, event).contains(point), "ordinary physical departure also wakes the occupied point");
    }

    @Test void derivedPointIndexReusesOneStateAndInvalidatesOnMutation() {
        var state = FrontierWorldState.initial(FrontierBootstrapper.create(
                new io.farfrontier.palemirror.frontier.v3.api.WorldId("frontier:access-index"), 91L));
        var point = FrontierWorldState.depotId(state.bootstrap().settlements().getFirst().id());
        var first = ServiceAccessCapabilities.current(state, point);
        assertSame(first, ServiceAccessCapabilities.current(state, point));
        var resident = state.bootstrap().settlements().getFirst().residents().getFirst().id();
        var changed = state.withActorBody(resident, state.actorLocations().get(resident).body().offset(1, 0, 0));
        assertEquals(new ServiceAccessCapabilities(List.of(new ResidentMealServiceAccess(),
                new ProductionServiceAccess(), new HarvestServiceAccess(), new ShipmentServiceAccess(),
                new ExpeditionSupplyServiceAccess(), new ExtractionServiceAccess(), new EngineeringEquipmentServiceAccess(),
                new EquipmentTransferServiceAccess(ServiceAccessDemand.Kind.DEFENDER_EQUIPMENT))).evaluate(changed, point),
                ServiceAccessCapabilities.current(changed, point));
        assertSame(ServiceAccessCapabilities.current(changed, point), ServiceAccessCapabilities.current(changed, point));
        var depot = state.bootstrap().settlements().getFirst().structures().stream()
                .filter(structure -> structure.kind() == StructureKind.DEPOT).findFirst().orElseThrow();
        assertSame(SettlementDepotServicePort.forDepot(depot), SettlementDepotServicePort.forDepot(depot));
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        var production = new ServiceAccessCapability() {
            @Override public java.util.Set<SubjectId> wakePoints(FrontierWorldState previous, FrontierWorldState next) {
                return java.util.Set.of();
            }
            @Override public ServiceAccessDemand.Kind kind() { return ServiceAccessDemand.Kind.PRODUCTION; }
            @Override public ServiceAccessDemand.Priority priority() { return ServiceAccessDemand.Priority.WORK; }
            @Override public List<ServiceAccessDemand> demands(FrontierWorldState current, SubjectId requested) {
                calls.incrementAndGet(); return List.of();
            }
        };
        var counted = new ServiceAccessCapabilities(List.of(new ResidentMealServiceAccess(), new HarvestServiceAccess(),
                new ShipmentServiceAccess(), new ExpeditionSupplyServiceAccess(), new ExtractionServiceAccess(), new EngineeringEquipmentServiceAccess(),
                new EquipmentTransferServiceAccess(ServiceAccessDemand.Kind.DEFENDER_EQUIPMENT), production));
        counted.indexed(state, point); counted.indexed(state, point);
        assertEquals(1, calls.get(), "family demand scans occur once per immutable state and queried point");
        counted.indexed(changed, point);
        assertEquals(2, calls.get(), "a changed authoritative input cannot reuse stale demands");
    }
    private static final SubjectId POINT = new SubjectId("container:1-depot");
    private static final ServiceAccessDemand.Identity IDENTITY = new ServiceAccessDemand.Identity(
            ServiceAccessDemand.Kind.PRODUCTION, new SubjectId("job:test-production"), new SubjectId("resident:1-1"), POINT);

    @Test void closedRegistryRejectsMissingAndDuplicateOwners() {
        assertThrows(IllegalArgumentException.class, () -> new ServiceAccessCapabilities(List.of(new ResidentMealServiceAccess())));
        assertThrows(IllegalArgumentException.class, () -> new ServiceAccessCapabilities(List.of(
                new ResidentMealServiceAccess(), new ProductionServiceAccess(), new HarvestServiceAccess(), new ProductionServiceAccess())));
    }

    @Test void wrongFamilyPointPriorityAndDuplicateDemandFailBeforeArbitration() {
        var state = FrontierWorldState.initial(FrontierBootstrapper.create(
                new io.farfrontier.palemirror.frontier.v3.api.WorldId("frontier:invalid-service-demand"), 91L));
        var valid = new ServiceAccessDemand(IDENTITY, ServiceAccessDemand.Priority.WORK,
                ServiceAccessDemand.Presence.OCCUPIED, 0, true);
        assertEquals(List.of(valid), registry(List.of(valid)).evaluate(state, POINT));
        var wrongFamily = new ServiceAccessDemand(new ServiceAccessDemand.Identity(ServiceAccessDemand.Kind.FIELD_HARVEST,
                IDENTITY.ownerId(), IDENTITY.actorId(), POINT), valid.priority(), valid.presence(), 0, true);
        var wrongPoint = new ServiceAccessDemand(new ServiceAccessDemand.Identity(IDENTITY.kind(), IDENTITY.ownerId(),
                IDENTITY.actorId(), new SubjectId("container:2-depot")), valid.priority(), valid.presence(), 0, true);
        var wrongPriority = new ServiceAccessDemand(IDENTITY, ServiceAccessDemand.Priority.SELF_CARE, valid.presence(), 0, true);
        for (var invalid : List.of(wrongFamily, wrongPoint, wrongPriority))
            assertThrows(IllegalArgumentException.class, () -> registry(List.of(invalid)).evaluate(state, POINT));
        assertThrows(IllegalArgumentException.class, () -> registry(List.of(valid, valid)).evaluate(state, POINT));
    }

    @Test void incompleteNominalIdentityCannotBeConstructed() {
        assertThrows(NullPointerException.class, () -> new ServiceAccessDemand.Identity(null, IDENTITY.ownerId(), IDENTITY.actorId(), POINT));
        assertThrows(NullPointerException.class, () -> new ServiceAccessDemand.Identity(IDENTITY.kind(), null, IDENTITY.actorId(), POINT));
        assertThrows(NullPointerException.class, () -> new ServiceAccessDemand.Identity(IDENTITY.kind(), IDENTITY.ownerId(), null, POINT));
        assertThrows(NullPointerException.class, () -> new ServiceAccessDemand.Identity(IDENTITY.kind(), IDENTITY.ownerId(), IDENTITY.actorId(), null));
    }

    private static ServiceAccessCapabilities registry(List<ServiceAccessDemand> demands) {
        return new ServiceAccessCapabilities(java.util.Arrays.stream(ServiceAccessDemand.Kind.values())
                .map(kind -> provider(kind, kind == ServiceAccessDemand.Kind.PRODUCTION ? demands : List.of())).toList());
    }

    private static ServiceAccessCapability provider(ServiceAccessDemand.Kind kind, List<ServiceAccessDemand> demands) {
        return new ServiceAccessCapability() {
            @Override public java.util.Set<SubjectId> wakePoints(FrontierWorldState previous, FrontierWorldState next) {
                return java.util.Set.of();
            }
            @Override public ServiceAccessDemand.Kind kind() { return kind; }
            @Override public ServiceAccessDemand.Priority priority() {
                return kind == ServiceAccessDemand.Kind.MEAL ? ServiceAccessDemand.Priority.SELF_CARE : ServiceAccessDemand.Priority.WORK;
            }
            @Override public List<ServiceAccessDemand> demands(FrontierWorldState state, SubjectId pointId) { return demands; }
        };
    }
}
