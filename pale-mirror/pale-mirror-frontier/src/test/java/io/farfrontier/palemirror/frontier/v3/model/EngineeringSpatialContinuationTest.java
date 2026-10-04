package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.process.*;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import org.junit.jupiter.api.Test;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

/** Connected owner/body/recovery checks, not native Minecraft acceptance. */
class EngineeringSpatialContinuationTest {
    @Test void bothOwnersRetainEveryJourneyPurposeAcrossMidLegDepartureAndRecovery() {
        for (boolean repair : List.of(false, true)) for (var purpose : EngineeringJourneyPurpose.values()) {
            var state = journey(repair, purpose);
            var owner = owner(state, repair);
            var actor = owner.assembly().orElseThrow().safeAdvances().getFirst();
            var before = owner.assembly().orElseThrow().members().get(actor);
            var stale = advanced(state, owner, owner.assembly().orElseThrow().advance(actor), Optional.empty());
            var observed = detour(state, owner, actor);
            var inventory = state.inventory();
            var executions = state.actorExecutions();
            state = AmbientLeaseStateProcess.prepare(state, AmbientActorProcess.nextLease(state, actor, new SimInstant(400L)));
            state = ModeledActorBodyFacts.present(state, actor);
            state = AmbientLeaseStateProcess.transition(state, actor, AmbientLeaseStatus.HOT);
            state = ModeledActorBodyFacts.inspected(ModeledActorBodyFacts.present(state, actor), actor, observed.standingBody());
            state = ModeledActorBodyFacts.unloaded(state, actor);
            var checkpoint = owner(state, repair);
            var after = checkpoint.assembly().orElseThrow().members().get(actor);
            assertEquals(before.corridor(), after.corridor());
            assertEquals(before.cursor(), after.cursor(), "departure is not arrival");
            assertEquals(before.routeRevision() + 1, after.routeRevision());
            assertEquals(observed, after.currentSurface());
            assertEquals(before.nextSurface(), after.rejoin().orElseThrow().target());
            assertEquals(owner.engineeringTeam(), checkpoint.engineeringTeam());
            assertEquals(purpose, checkpoint.assembly().orElseThrow().purpose());
            assertEquals(inventory, state.inventory());
            assertEquals(executions, state.actorExecutions());
            var departed = state;
            assertThrows(IllegalArgumentException.class, () -> reduce(departed,
                    advanced(departed, checkpoint, checkpoint.assembly().orElseThrow().advance(actor), Optional.empty())),
                    "body departure does not silently close its still-retained presentation scope");
            state = AmbientLeaseStateProcess.transition(state, actor, AmbientLeaseStatus.DRAINING);
            state = AmbientLeaseStateProcess.release(state, new AmbientLeaseReleased(actor, observed.standingBody(),
                    state.actorLocations().get(actor).condition().health()));
            var recovered = new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(state));
            assertEquals(state, recovered);
            assertThrows(IllegalArgumentException.class, () -> reduce(recovered, stale), "old route receipt cannot advance the new rejoin");
            var progress = advanced(state, checkpoint, checkpoint.assembly().orElseThrow().advance(actor), Optional.empty());
            var codecs = FrontierWorldRuntimeDefinition.payloadCodecs();
            assertEquals(progress, codecs.decode(progress.type(), codecs.encode(progress)));
            state = recovered;
            for (int step = 0; step < after.rejoin().orElseThrow().path().size(); step++) {
                var current = owner(state, repair);
                if (current.assembly().orElseThrow().members().get(actor).rejoin().isEmpty()) break;
                state = reduce(state, advanced(state, current, current.assembly().orElseThrow().advance(actor), Optional.empty()));
            }
            var arrived = owner(state, repair).assembly().orElseThrow().members().get(actor);
            assertTrue(arrived.rejoin().isEmpty());
            assertEquals(before.cursor() + 1, arrived.cursor());
            assertEquals(arrived.currentSurface(), state.actorLocations().get(actor).supportingSurface());
        }
    }

    @Test void hotArrivalRequiresCapturedAuthorityAndIndependentInspectionForBothOwners() {
        for (boolean repair : List.of(false, true)) {
            var state = journey(repair, EngineeringJourneyPurpose.MUSTER_DEPOT);
            var owner = owner(state, repair);
            var actor = owner.assembly().orElseThrow().safeAdvances().getFirst();
            var next = owner.assembly().orElseThrow().advance(actor);
            state = AmbientLeaseStateProcess.prepare(state, AmbientActorProcess.nextLease(state, actor, new SimInstant(400L)));
            state = ModeledActorBodyFacts.present(state, actor);
            state = AmbientLeaseStateProcess.transition(state, actor, AmbientLeaseStatus.HOT);
            var scope = state.ambientLeases().get(actor);
            var execution = EngineeringExecutionAuthority.assemblyCurrent(state, owner).requireMember(actor);
            var body = ActorBodyAuthority.current(state, actor);
            var captured = new EngineeringHotArrival(new ActorActuationId(body, execution), scope.revision());
            var receipt = advanced(state, owner, next, Optional.of(captured));
            var uninspected = state;
            assertThrows(IllegalArgumentException.class, () -> reduce(uninspected, receipt));
            assertThrows(IllegalArgumentException.class, () -> reduce(uninspected, advanced(uninspected, owner, next, Optional.empty())));
            state = ModeledActorBodyFacts.inspected(state, actor, next.members().get(actor).currentSurface().standingBody());
            var inspected = state;
            var staleBody = new EngineeringHotArrival(new ActorActuationId(new ActorBodyId(actor, body.physicalEpoch() + 1), execution), scope.revision());
            assertThrows(IllegalArgumentException.class, () -> reduce(inspected, advanced(inspected, owner, next, Optional.of(staleBody))));
            var staleScope = new EngineeringHotArrival(captured.actuation(), scope.revision() + 1);
            assertThrows(IllegalArgumentException.class, () -> reduce(inspected, advanced(inspected, owner, next, Optional.of(staleScope))));
            var staleExecution = new EngineeringHotArrival(new ActorActuationId(body,
                    new ActorExecutionId(actor, execution.activityKind(), execution.activityOwnerId(), execution.generation() + 1)), scope.revision());
            assertThrows(IllegalArgumentException.class, () -> advanced(inspected, owner, next, Optional.of(staleExecution)));
            var codecs = FrontierWorldRuntimeDefinition.payloadCodecs();
            assertEquals(receipt, codecs.decode(receipt.type(), codecs.encode(receipt)));
            var moved = reduce(inspected, receipt);
            assertEquals(inspected.actorLocations(), moved.actorLocations(), "only common body inspection writes HOT pose");
            assertEquals(inspected.fencedRecovery(), moved.fencedRecovery(), "semantic arrival preserves physical incarnation");
            assertThrows(IllegalArgumentException.class, () -> reduce(moved, receipt));
        }
    }

    @Test void knownBlockedEdgeWaitsWithoutEmittingAnInvalidColdStep() {
        for (boolean repair : List.of(false, true)) {
            var state = journey(repair, EngineeringJourneyPurpose.MUSTER_DEPOT);
            var owner = owner(state, repair);
            var actor = owner.assembly().orElseThrow().safeAdvances().getFirst();
            var member = owner.assembly().orElseThrow().members().get(actor);
            var blocked = state.recordPhysicalDelta(new PhysicalDelta(member.nextSurface().support().offset(0, 1, 0),
                    PhysicalDeltaKind.UNKNOWN_SCAR, Optional.empty(), Optional.empty(), "player:engineering-path-block"));
            assertFalse(EngineeringJourneyKnowledge.openEdge(blocked, owner, actor));
            var receipt = advanced(blocked, owner, owner.assembly().orElseThrow().advance(actor), Optional.empty());
            assertThrows(IllegalArgumentException.class, () -> reduce(blocked, receipt));
            var planned = repair ? RouteMaintenanceProcess.planAssemblyProgress(blocked, RouteMaintenanceProcess.assemblyProgress(owner.id(), 500L))
                    : RouteConstructionProcess.planAssemblyProgress(blocked, RouteConstructionProcess.assemblyProgress(owner.id(), 500L));
            assertTrue(planned.stream().noneMatch(event -> event.payload().equals(receipt)));
            assertEquals(member, owner(blocked, repair).assembly().orElseThrow().members().get(actor));
        }
    }

    @Test void nonFlatRejoinPreservesTheOriginalRouteAndAwardsOnlyItsRetainedCheckpoint() {
        var corridor = List.of(new BlockPosition(0, 63, 0), new BlockPosition(1, 64, 0), new BlockPosition(2, 64, 0));
        var before = new EngineeringWorkAssembly.Member(corridor, 0);
        var approach = new io.farfrontier.palemirror.frontier.v3.model.navigation.TraversalRejoin(
                List.of(SurfaceAnchor.at(0, 63, 1), SurfaceAnchor.at(1, 64, 1), SurfaceAnchor.at(1, 64, 0)), 0);
        var mid = before.withRejoin(approach).advanceOne();
        assertEquals(0, mid.cursor());
        assertEquals(SurfaceAnchor.at(1, 64, 1), mid.currentSurface());
        var joined = mid.advanceOne();
        assertEquals(corridor, joined.corridor());
        assertEquals(1, joined.cursor());
        assertTrue(joined.rejoin().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> new io.farfrontier.palemirror.frontier.v3.model.navigation.TraversalRejoin(
                List.of(SurfaceAnchor.at(0, 62, 1), SurfaceAnchor.at(1, 64, 1), SurfaceAnchor.at(1, 64, 0)), 0));
    }

    private static FrontierWorldState journey(boolean repair, EngineeringJourneyPurpose purpose) {
        var state = FrontierV3FixtureCatalog.engineeringEquipmentConfiguration(new WorldId("frontier:engineering-spatial-" + repair + "-"
                + purpose.name().toLowerCase(java.util.Locale.ROOT)), 41L).initialState();
        if (repair) {
            state = FrontierWorldState.initial(state.bootstrap());
            var settlement = state.bootstrap().settlements().getFirst();
            var loss = state.routeTopology().supplyWaypoints(state.bootstrap(), settlement.id()).get(2);
            state = state.recordPhysicalDelta(new PhysicalDelta(loss, PhysicalDeltaKind.KNOWN_SEMANTIC_LOSS,
                    Optional.of(new PhysicalDeltaSemanticTarget(PhysicalDeltaSemanticTargetKind.ROUTE_NETWORK, FrontierRouteNetwork.OWNER)),
                    Optional.of(GrayboxSemanticPart.ROUTE_SURFACE), "player:engineering-spatial-repair"));
            var started = RouteMaintenanceProcess.plan(state, RouteMaintenanceProcess.scan(1, 100L)).stream().map(ProposedEvent::payload)
                    .filter(RouteMaintenanceStarted.class::isInstance).map(RouteMaintenanceStarted.class::cast).findFirst().orElseThrow();
            state = RouteMaintenanceStateSupport.reduceStarted(state, FrontierRouteNetwork.OWNER, started);
        }
        var owner = owner(state, repair);
        if (purpose == EngineeringJourneyPurpose.RETURN_DEPOT) {
            switch (owner) {
                case RouteConstruction construction -> {
                    var projects = new LinkedHashMap<>(state.routeConstructions());
                    projects.put(owner.id(), construction.withConfirmedCells(construction.workCells().size(), RouteConstructionStatus.READY));
                    state = state.withChanges(FrontierWorldStateUpdate.begin().routeConstructions(projects));
                }
                case RouteMaintenance maintenance -> {
                    var projects = new LinkedHashMap<>(state.routeMaintenances()); projects.put(owner.id(), maintenance.ready());
                    state = state.withChanges(FrontierWorldStateUpdate.begin().routeMaintenances(projects));
                }
            }
            owner = owner(state, repair);
        }
        var assembly = purpose == EngineeringJourneyPurpose.WORKSITE ? EngineeringWorksite.compile(state, owner) : EngineeringDepotService.compile(state, owner, purpose);
        return repair ? RouteMaintenanceStateSupport.reduceAssemblyStarted(state, FrontierRouteNetwork.OWNER,
                EngineeringExecutionEvents.maintenanceAssemblyStarted(state, owner.id(), assembly))
                : RouteConstructionStateSupport.reduceAssemblyStarted(state, FrontierRouteNetwork.OWNER,
                EngineeringExecutionEvents.constructionAssemblyStarted(state, owner.id(), assembly));
    }
    private static EngineeringWorkOrder owner(FrontierWorldState state, boolean repair) {
        return repair ? state.routeMaintenances().values().stream().findFirst().orElseThrow() : state.routeConstructions().values().stream().findFirst().orElseThrow();
    }
    private static FrontierPayload advanced(FrontierWorldState state, EngineeringWorkOrder owner, EngineeringWorkAssembly next, Optional<EngineeringHotArrival> arrival) {
        return switch (owner) {
            case RouteConstruction construction -> EngineeringExecutionEvents.constructionAssemblyAdvanced(state, owner.id(), next, arrival);
            case RouteMaintenance maintenance -> EngineeringExecutionEvents.maintenanceAssemblyAdvanced(state, owner.id(), next, arrival);
        };
    }
    private static FrontierWorldState reduce(FrontierWorldState state, FrontierPayload receipt) {
        if (receipt instanceof RouteConstructionAssemblyAdvanced advanced) return RouteConstructionStateSupport.reduceAssemblyAdvanced(state, FrontierRouteNetwork.OWNER, advanced);
        return RouteMaintenanceStateSupport.reduceAssemblyAdvanced(state, FrontierRouteNetwork.OWNER, (RouteMaintenanceAssemblyAdvanced) receipt);
    }
    private static SurfaceAnchor detour(FrontierWorldState state, EngineeringWorkOrder owner, SubjectId actor) {
        var member = owner.assembly().orElseThrow().members().get(actor);
        var knowledge = EngineeringJourneyKnowledge.view(state, owner);
        for (int dx : List.of(-2, 2, -3, 3)) for (int dz : List.of(-2, 2, -3, 3)) {
            var surface = knowledge.supportAt(member.currentSurface().x() + dx, member.currentSurface().z() + dz);
            try {
                if (EngineeringJourneyKnowledge.rejoin(state, owner, actor, surface).size() > 2) return surface;
            } catch (IllegalArgumentException unavailable) { /* choose another known modeled detour */ }
        }
        throw new IllegalStateException("engineering fixture has no known bounded detour");
    }
}
