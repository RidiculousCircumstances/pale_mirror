package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldPayloadCodecs;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class AmbientBodyConfirmationProcessTest {
    @Test void admissionAndRestartResolvePurposeFromTheRetainedActivityNotHistoricalLease() {
        Fixture f = fixture();
        var lease = f.state().ambientLeases().get(f.medic());
        var oldPurpose = new AmbientActorLease(lease.actorId(), lease.handoffBody(), lease.handoffInstant(),
                lease.revision(), lease.status(), AmbientGoalKind.PATROL, lease.handoffBody());
        var leases = new java.util.LinkedHashMap<>(f.state().ambientLeases());
        leases.put(f.medic(), oldPurpose);
        var prepared = f.state().withChanges(FrontierWorldStateUpdate.begin().ambientLeases(leases));
        var evidence = confirmation(prepared, f.medic(), AmbientBodyConfirmed.Boundary.ADMISSION, lease.handoffBody());
        var inspected = ModeledActorBodyFacts.inspected(prepared, f.medic(), evidence.observedBody());
        var hot = AmbientActorProcess.reduceLease(inspected, f.port().settlementId(), new SimInstant(27_001L), evidence);
        assertEquals(AmbientGoalKind.MEAL, hot.ambientLeases().get(f.medic()).goal());
        assertEquals(inspected.actorLocations(), hot.actorLocations(), "scope admission never rewrites the body");
        assertEquals(prepared.inventory(), hot.inventory());
        assertEquals(prepared.humanPopulation().meals(), hot.humanPopulation().meals());
        var historical = new java.util.LinkedHashMap<>(hot.ambientLeases());
        historical.put(f.medic(), oldPurpose.withStatus(AmbientLeaseStatus.UNKNOWN_AFTER_RESTART));
        var recovered = new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(
                hot.withChanges(FrontierWorldStateUpdate.begin().ambientLeases(historical))));
        var resumed = AmbientActorProcess.reduceLease(recovered, f.port().settlementId(), new SimInstant(27_002L),
                new AmbientLeaseTransition(f.medic(), AmbientLeaseStatus.HOT));
        assertEquals(AmbientGoalKind.MEAL, resumed.ambientLeases().get(f.medic()).goal());
        assertEquals(lease.revision(), resumed.ambientLeases().get(f.medic()).revision());
        assertEquals(recovered.actorLocations(), resumed.actorLocations());
        assertEquals(recovered.humanPopulation().meals(), resumed.humanPopulation().meals());
        assertEquals(recovered.inventory(), resumed.inventory());
    }
    @Test void inspectedLivingBodyMaySettleOutsidePlacementZoneWithoutInventingArrival() throws Exception {
        Fixture f = fixture();
        var lease = f.state().ambientLeases().get(f.medic());
        var start = lease.handoffBody().supportingSurface();
        var displaced = new SurfaceAnchor(new BlockPosition(start.x() + 24, start.y() - 1, start.z())).standingBody();
        var evidence = new AmbientBodyConfirmed(f.medic(), lease.revision(),
                AmbientBodyConfirmed.Boundary.INSPECTED_BODY_ADMISSION, lease.handoffBody(), displaced,
                ActorBodyAuthority.current(f.state(), f.medic()), java.util.Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> AmbientBodyConfirmationProcess.reduce(f.state(), evidence),
                "a scope cannot fabricate its body's displacement");
        var inspected = ModeledActorBodyFacts.inspected(f.state(), f.medic(), displaced);
        var codecs = FrontierWorldPayloadCodecs.create();
        var decoded = (AmbientBodyConfirmed) codecs.decode(evidence.type(), codecs.encode(evidence));
        var next = AmbientBodyConfirmationProcess.reduce(inspected, decoded);
        assertSame(inspected.actorLocations(), next.actorLocations());
        assertEquals(AmbientLeaseStatus.HOT, next.ambientLeases().get(f.medic()).status());
        assertEquals(ResidentMeal.Phase.MOVE, next.humanPopulation().meals().get(f.medic()).phase());
        assertEquals(inspected.inventory(), next.inventory());
        assertThrows(IllegalArgumentException.class, () -> AmbientBodyConfirmationProcess.reduce(next, decoded));
    }
    private record Fixture(FrontierWorldState state, SubjectId security, SubjectId medic, SettlementDepotServicePort port) { }
    private Fixture fixture() {
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:body-admission"), 20260918065L));
        Settlement settlement = state.bootstrap().settlements().get(6);
        SubjectId security = new SubjectId("resident:7-4"), medic = new SubjectId("resident:7-11");
        SettlementDepotServicePort port = SettlementDepotServicePort.forDepot(settlement.structures().stream()
                .filter(value -> value.kind() == StructureKind.DEPOT).findFirst().orElseThrow());
        state = state.withHumanPopulation(state.humanPopulation().accrueHunger(security, 27_000L).accrueHunger(medic, 27_000L));
        for (SubjectId resident : List.of(security, medic))
            state = ResidentMealProcess.reduceStarted(state, resident,
                    ResidentMealProcess.selectSourceAtYield(state, resident, resident.equals(security) ? 27_000L : 27_001L).orElseThrow());
        // Retained COLD take at the socket; the security worker is still approaching in HOT.
        ResidentMeal meal = state.humanPopulation().meals().get(medic);
        state = state.withHumanPopulation(state.humanPopulation().advanceMeal(meal, meal.advance(ResidentMeal.Phase.TAKE)))
                .withActorBody(medic, port.serviceSurface().standingBody());
        for (SubjectId resident : List.of(security, medic)) {
            AmbientActorLease lease = AmbientActorProcess.nextLease(state, resident, new SimInstant(27_001L));
            state = AmbientLeaseStateProcess.prepare(state, lease);
            state = ModeledActorBodyFacts.present(state, resident);
            if (resident.equals(security)) state = confirmed(state, confirmation(state, resident,
                    AmbientBodyConfirmed.Boundary.ADMISSION, lease.handoffBody()));
        }
        return new Fixture(state, security, medic, port);
    }
    private AmbientBodyConfirmed confirmation(FrontierWorldState state, SubjectId actor, AmbientBodyConfirmed.Boundary boundary, BodyPosition body) {
        return new AmbientBodyConfirmed(actor, state.ambientLeases().get(actor).revision(), boundary,
                boundary == AmbientBodyConfirmed.Boundary.ADMISSION ? state.ambientLeases().get(actor).handoffBody()
                        : state.actorLocations().get(actor).body(), body, ActorBodyAuthority.current(state, actor),
                boundary == AmbientBodyConfirmed.Boundary.ADMISSION
                        ? AmbientPlacementPolicy.selectedRoute(state, state.ambientLeases().get(actor), body.supportingSurface())
                        : java.util.Optional.empty());
    }
    private FrontierWorldState confirmed(FrontierWorldState state, AmbientBodyConfirmed evidence) {
        var inspected = ModeledActorBodyFacts.inspected(state, evidence.actorId(), evidence.observedBody());
        var next = AmbientBodyConfirmationProcess.reduce(inspected, evidence);
        assertSame(inspected.actorLocations(), next.actorLocations(), "scope acknowledgement cannot write physical position");
        return next;
    }
    @Test void realSocketOccupantDoesNotWaitForAnUnmaterializedColdTake() {
        Fixture f = fixture();
        FrontierWorldState state = confirmed(f.state(), confirmation(f.state(), f.security(),
                AmbientBodyConfirmed.Boundary.SERVICE_OCCUPANCY, f.port().serviceSurface().standingBody()));
        assertTrue(ResidentMealServiceAccess.available(state, FrontierWorldState.depotId(f.port().settlementId()), f.security()));
        assertFalse(ResidentMealServiceAccess.available(state, FrontierWorldState.depotId(f.port().settlementId()), f.medic()));
        ResidentMealHotArrived arrival = new ResidentMealHotArrived(f.security(), state.ambientLeases().get(f.security()).revision(),
                f.port().serviceSurface().standingBody(), ModeledActorBodyFacts.hotObservation(state,
                        state.humanPopulation().meals().get(f.security()).executionId(), state.ambientLeases().get(f.security()).revision()));
        assertTrue(ResidentMealProcess.hotArrivalHasServiceTurn(state, f.security(), arrival));
        assertEquals(ResidentMeal.Phase.TAKE, ResidentMealProcess.reduceHotArrived(state, f.security(), arrival)
                .humanPopulation().meals().get(f.security()).phase());
    }
    @Test void alternativePlacementRetainsClaimAndRequiresApproachAcrossRecovery() throws Exception {
        Fixture f = fixture();
        ResidentMeal previous = f.state().humanPopulation().meals().get(f.medic());
        SurfaceAnchor waiting = AmbientPlacementPolicy.candidates(f.state(), f.state().ambientLeases().get(f.medic())).stream()
                .filter(surface -> f.port().accessBoundary().cleared(surface.standingBody())).findFirst().orElseThrow();
        AmbientBodyConfirmed evidence = confirmation(f.state(), f.medic(), AmbientBodyConfirmed.Boundary.ADMISSION, waiting.standingBody());
        var codecs = FrontierWorldPayloadCodecs.create();
        assertEquals(evidence, codecs.decode(evidence.type(), codecs.encode(evidence)));
        var exactEvidence = confirmation(f.state(), f.medic(), AmbientBodyConfirmed.Boundary.ADMISSION,
                f.state().ambientLeases().get(f.medic()).handoffBody());
        var currentBytes = codecs.encode(exactEvidence);
        assertThrows(IllegalArgumentException.class, () -> codecs.decode(exactEvidence.type(),
                java.util.Arrays.copyOf(currentBytes, currentBytes.length - 1)), "retired payloads without the explicit route field are rejected");
        assertThrows(IllegalArgumentException.class, () -> AmbientBodyConfirmationProcess.reduce(f.state(), evidence),
                "scope receipt cannot substitute for an independent body observation");
        var inspected = ModeledActorBodyFacts.inspected(f.state(), f.medic(), evidence.observedBody());
        var recoveredBeforeAdmission = new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(inspected));
        FrontierWorldState next;
        try (var binding = io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRoutePlanning.bind((geometry, from, to) -> {
            throw new AssertionError("placement replay must not rediscover candidates or routes");
        })) {
            next = AmbientBodyConfirmationProcess.reduce(recoveredBeforeAdmission,
                    (AmbientBodyConfirmed) codecs.decode(evidence.type(), codecs.encode(evidence)));
            assertThrows(IllegalArgumentException.class, () -> AmbientBodyConfirmationProcess.reduce(next, evidence));
            assertThrows(IllegalArgumentException.class, () -> AmbientBodyConfirmationProcess.reduce(recoveredBeforeAdmission,
                    new AmbientBodyConfirmed(evidence.actorId(), evidence.leaseRevision(), evidence.boundary(), evidence.previousBody(),
                            evidence.observedBody(), evidence.bodyId(), java.util.Optional.empty())));
            var reversed = new io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRouteReceipt(
                    evidence.placementRoute().orElseThrow().route().reversed());
            assertThrows(IllegalArgumentException.class, () -> AmbientBodyConfirmationProcess.reduce(recoveredBeforeAdmission,
                    new AmbientBodyConfirmed(evidence.actorId(), evidence.leaseRevision(), evidence.boundary(), evidence.previousBody(),
                            evidence.observedBody(), evidence.bodyId(), java.util.Optional.of(reversed))));
        }
        assertEquals(waiting.standingBody(), next.actorLocations().get(f.medic()).body());
        assertEquals(AmbientLeaseStatus.HOT, next.ambientLeases().get(f.medic()).status());
        assertEquals(ResidentMeal.Phase.MOVE, next.humanPopulation().meals().get(f.medic()).phase());
        assertEquals(previous.claimId(), next.humanPopulation().meals().get(f.medic()).claimId());
        assertEquals(f.state().inventory(), next.inventory(), "placement cannot transfer or consume bread");
        FrontierWorldState recovered = new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(next));
        assertEquals(next.ambientLeases(), recovered.ambientLeases());
        assertEquals(next.humanPopulation().meals(), recovered.humanPopulation().meals());
        assertEquals(waiting.standingBody(), recovered.actorLocations().get(f.medic()).body());
        FrontierWorldState draining = AmbientLeaseStateProcess.transition(recovered, f.medic(), AmbientLeaseStatus.DRAINING);
        FrontierWorldState cold = AmbientLeaseStateProcess.release(draining, new AmbientLeaseReleased(f.medic(), waiting.standingBody(),
                draining.actorLocations().get(f.medic()).condition().health()));
        assertEquals(waiting, ResidentMealKnownNavigation.path(cold, cold.humanPopulation().meals().get(f.medic())).getFirst());
    }
    @Test void pendingPlacementDoesNotBecomeAbsenceOrEscapeIntoAnotherCandidate() throws Exception {
        Fixture f = fixture(); var lease = f.state().ambientLeases().get(f.medic());
        var waiting = AmbientPlacementPolicy.candidates(f.state(), lease).stream()
                .filter(surface -> f.port().accessBoundary().cleared(surface.standingBody())).findFirst().orElseThrow();
        for (var status : List.of(io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRouteResult.Status.PLANNING,
                io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRouteResult.Status.UNKNOWN_GEOMETRY,
                io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRouteResult.Status.SAFETY_LIMIT)) {
            var queries = new java.util.concurrent.atomic.AtomicInteger();
            try (var binding = io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRoutePlanning.bind((geometry, from, to) -> {
                queries.incrementAndGet(); return new io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRouteResult(status,
                        List.of(), 0L, 0, "placement dependency");
            })) {
                assertEquals(lease.handoffBody().supportingSurface(), AmbientPlacementPolicy.candidates(f.state(), lease).getFirst());
                assertEquals(0, queries.get(), "geometric proposals cannot start speculative route searches");
                var failure = assertThrows(io.farfrontier.palemirror.frontier.v3.model.navigation.KnownPedestrianNavigation.RouteUnavailable.class,
                        () -> AmbientPlacementPolicy.selectedRoute(f.state(), lease, waiting));
                assertEquals(status, failure.status()); assertEquals(1, queries.get());
                assertTrue(AmbientPlacementPolicy.selectedRoute(f.state(), lease, lease.handoffBody().supportingSurface()).isEmpty());
                assertEquals(1, queries.get(), "an unchanged exact placement needs no route calculation");
            }
        }
    }
    @Test void staleEpochForgedPreviousAndOutsidePlacementFailBeforeMutation() {
        Fixture f = fixture();
        AmbientBodyConfirmed valid = confirmation(f.state(), f.medic(), AmbientBodyConfirmed.Boundary.ADMISSION,
                f.port().serviceSurface().standingBody());
        for (AmbientBodyConfirmed invalid : List.of(
                new AmbientBodyConfirmed(valid.actorId(), valid.leaseRevision() + 1, valid.boundary(), valid.previousBody(), valid.observedBody(), valid.bodyId(), valid.placementRoute()),
                new AmbientBodyConfirmed(valid.actorId(), valid.leaseRevision(), valid.boundary(), new BodyPosition(0, 65, 0), valid.observedBody(), valid.bodyId(), valid.placementRoute()),
                new AmbientBodyConfirmed(valid.actorId(), valid.leaseRevision(), valid.boundary(), valid.previousBody(), new BodyPosition(0, 65, 0), valid.bodyId(), valid.placementRoute()),
                new AmbientBodyConfirmed(valid.actorId(), valid.leaseRevision(), valid.boundary(), valid.previousBody(), valid.observedBody(),
                        new io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyId(valid.actorId(), valid.bodyId().physicalEpoch() + 1), valid.placementRoute())))
            assertThrows(IllegalArgumentException.class, () -> AmbientBodyConfirmationProcess.reduce(f.state(), invalid));
        assertThrows(IllegalArgumentException.class, () -> AmbientBodyConfirmed.Boundary.fromWireTag(99));
        assertThrows(IllegalArgumentException.class, () -> AmbientBodyConfirmationProcess.reduce(f.state(), confirmation(f.state(), f.medic(),
                AmbientBodyConfirmed.Boundary.SERVICE_OCCUPANCY, f.port().exteriorApproach().standingBody())));
        assertInstanceOf(io.farfrontier.palemirror.frontier.v3.kernel.CommandPlan.Rejected.class,
                AmbientActorProcess.plan(f.state(), new AmbientLeaseTransition(f.medic(), AmbientLeaseStatus.HOT)));
    }
    @Test void returningToAnOccupiedWaitingSpotAllowsAnotherDeclaredConnectedSpot() {
        Fixture f = fixture();
        var lease = f.state().ambientLeases().get(f.medic());
        var waiting = AmbientPlacementPolicy.candidates(f.state(), lease).stream()
                .filter(surface -> f.port().accessBoundary().cleared(surface.standingBody())).findFirst().orElseThrow();
        var hot = confirmed(f.state(), confirmation(f.state(), f.medic(),
                AmbientBodyConfirmed.Boundary.ADMISSION, waiting.standingBody()));
        var draining = AmbientLeaseStateProcess.transition(hot, f.medic(), AmbientLeaseStatus.DRAINING);
        var cold = AmbientLeaseStateProcess.release(draining, new AmbientLeaseReleased(f.medic(),
                waiting.standingBody(), draining.actorLocations().get(f.medic()).condition().health()));
        var prepared = AmbientLeaseStateProcess.prepare(cold,
                AmbientActorProcess.nextLease(cold, f.medic(), new SimInstant(27_002L)));
        var candidates = AmbientPlacementPolicy.candidates(prepared, prepared.ambientLeases().get(f.medic()));
        assertEquals(waiting, candidates.getFirst());
        var alternative = candidates.stream().filter(surface -> !surface.equals(waiting)).findFirst().orElseThrow();
        var next = confirmed(prepared, confirmation(prepared, f.medic(),
                AmbientBodyConfirmed.Boundary.ADMISSION, alternative.standingBody()));
        assertEquals(alternative.standingBody(), next.actorLocations().get(f.medic()).body());
        assertEquals(ResidentMeal.Phase.MOVE, next.humanPopulation().meals().get(f.medic()).phase());
        assertEquals(prepared.humanPopulation().meals().get(f.medic()).claimId(),
                next.humanPopulation().meals().get(f.medic()).claimId());
        assertEquals(prepared.inventory(), next.inventory(), "placement does not take or consume food");
        assertEquals(next.ambientLeases(), new FrontierWorldStateCodec().decode(
                new FrontierWorldStateCodec().encode(next)).ambientLeases());
    }
    @Test void unauthorizedResidentAtStationGetsAnExitRatherThanASingletonWait() {
        Fixture f = fixture();
        FrontierWorldState state = f.state().withActorBody(f.security(), f.port().serviceSurface().standingBody());
        state = confirmed(state, confirmation(state, f.medic(), AmbientBodyConfirmed.Boundary.ADMISSION,
                f.port().serviceSurface().standingBody()));
        // Earlier security meal owns arbitration; the medic must leave rather than stand in its socket.
        assertFalse(ResidentMealServiceAccess.available(state, FrontierWorldState.depotId(f.port().settlementId()), f.medic()));
        ResidentMeal meal = state.humanPopulation().meals().get(f.medic());
        List<SurfaceAnchor> route = ResidentMealKnownNavigation.path(state, meal);
        assertTrue(route.size() > 1);
        assertTrue(f.port().accessBoundary().cleared(route.getLast().standingBody()));
        assertTrue(f.port().accessBoundary().allowsWaitingRoute(route));
    }
    @Test void registeredCommandCommitsPlacementAndWakesTheExactServicePoint() {
        Fixture f = fixture();
        SurfaceAnchor waiting = AmbientPlacementPolicy.candidates(f.state(), f.state().ambientLeases().get(f.medic())).stream()
                .filter(surface -> f.port().accessBoundary().cleared(surface.standingBody())).findFirst().orElseThrow();
        AmbientBodyConfirmed evidence = confirmation(f.state(), f.medic(), AmbientBodyConfirmed.Boundary.ADMISSION, waiting.standingBody());
        var inspected = ModeledActorBodyFacts.inspected(f.state(), f.medic(), waiting.standingBody());
        var definition = io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition.configuration(f.state().bootstrap());
        var configuration = new io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngineConfiguration<>(
                f.state().bootstrap().worldId(), inspected, new SimInstant(27_001L), definition.commandPlanner(),
                definition.scheduledPlanner(), definition.reducer(), definition.stateCodec(), definition.projectionMapper(),
                definition.limits(), List.of(ResidentMealProcess.progress(f.state().humanPopulation().meals().get(f.medic()), 27_002L)),
                definition.transactionCommitter());
        var engine = io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngines.create(configuration);
        CommandId id = new CommandId("command:body-confirmation");
        CommandResult result = engine.submit(new FrontierCommand(1, id, f.state().bootstrap().worldId(), engine.checkpoint().revision(),
                new SimInstant(27_001L), io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR,
                CauseChain.root(id), evidence));
        assertInstanceOf(CommandResult.Accepted.class, result, result::toString);
        FrontierWorldState next = new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
        assertEquals(waiting.standingBody(), next.actorLocations().get(f.medic()).body());
        assertEquals(ResidentMeal.Phase.MOVE, next.humanPopulation().meals().get(f.medic()).phase());
        assertEquals(AmbientLeaseStatus.HOT, next.ambientLeases().get(f.medic()).status());
        FrontierEvent event = new FrontierEvent(1, new EventId("event:body-confirmation"), new TransactionId("transaction:body-confirmation"), f.state().bootstrap().worldId(),
                engine.checkpoint().revision(), new SimInstant(27_001L), f.port().settlementId(), CauseChain.root(id), evidence);
        assertTrue(io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition.wakeKeys(f.state(), next, event)
                .contains(FrontierWorldState.depotId(f.port().settlementId())));
        assertThrows(IllegalArgumentException.class, () -> AmbientBodyConfirmationProcess.reduce(next, evidence),
                "late admission cannot reopen an already HOT lease");
    }
}
