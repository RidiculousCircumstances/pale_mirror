package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldPayloadCodecs;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class AmbientBodyConfirmationProcessTest {
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
            if (resident.equals(security)) state = AmbientBodyConfirmationProcess.reduce(state, confirmation(state, resident,
                    AmbientBodyConfirmed.Boundary.ADMISSION, lease.handoffBody()));
        }
        return new Fixture(state, security, medic, port);
    }
    private AmbientBodyConfirmed confirmation(FrontierWorldState state, SubjectId actor, AmbientBodyConfirmed.Boundary boundary, BodyPosition body) {
        return new AmbientBodyConfirmed(actor, state.ambientLeases().get(actor).revision(), boundary,
                state.actorLocations().get(actor).body(), body);
    }
    @Test void realSocketOccupantDoesNotWaitForAnUnmaterializedColdTake() {
        Fixture f = fixture();
        FrontierWorldState state = AmbientBodyConfirmationProcess.reduce(f.state(), confirmation(f.state(), f.security(),
                AmbientBodyConfirmed.Boundary.SERVICE_OCCUPANCY, f.port().serviceSurface().standingBody()));
        assertTrue(ServiceAccessCoordinator.depotAvailableForMeal(state, FrontierWorldState.depotId(f.port().settlementId()), f.security()));
        assertFalse(ServiceAccessCoordinator.depotAvailableForMeal(state, FrontierWorldState.depotId(f.port().settlementId()), f.medic()));
        ResidentMealHotArrived arrival = new ResidentMealHotArrived(f.security(), state.ambientLeases().get(f.security()).revision(),
                f.port().serviceSurface().standingBody());
        assertTrue(ResidentMealProcess.hotArrivalHasServiceTurn(state, f.security(), arrival));
        assertEquals(ResidentMeal.Phase.TAKE, ResidentMealProcess.reduceHotArrived(state, f.security(), arrival)
                .humanPopulation().meals().get(f.security()).phase());
    }
    @Test void alternativePlacementRetainsClaimAndRequiresApproachAcrossRecovery() {
        Fixture f = fixture();
        ResidentMeal previous = f.state().humanPopulation().meals().get(f.medic());
        SurfaceAnchor waiting = AmbientPlacementPolicy.candidates(f.state(), f.state().ambientLeases().get(f.medic())).stream()
                .filter(surface -> f.port().accessBoundary().cleared(surface.standingBody())).findFirst().orElseThrow();
        AmbientBodyConfirmed evidence = confirmation(f.state(), f.medic(), AmbientBodyConfirmed.Boundary.ADMISSION, waiting.standingBody());
        var codecs = FrontierWorldPayloadCodecs.create();
        assertEquals(evidence, codecs.decode(evidence.type(), codecs.encode(evidence)));
        FrontierWorldState next = AmbientBodyConfirmationProcess.reduce(f.state(), evidence);
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
    @Test void staleEpochForgedPreviousAndOutsidePlacementFailBeforeMutation() {
        Fixture f = fixture();
        AmbientBodyConfirmed valid = confirmation(f.state(), f.medic(), AmbientBodyConfirmed.Boundary.ADMISSION,
                f.port().serviceSurface().standingBody());
        for (AmbientBodyConfirmed invalid : List.of(
                new AmbientBodyConfirmed(valid.actorId(), valid.leaseRevision() + 1, valid.boundary(), valid.previousBody(), valid.observedBody()),
                new AmbientBodyConfirmed(valid.actorId(), valid.leaseRevision(), valid.boundary(), new BodyPosition(0, 65, 0), valid.observedBody()),
                new AmbientBodyConfirmed(valid.actorId(), valid.leaseRevision(), valid.boundary(), valid.previousBody(), new BodyPosition(0, 65, 0))))
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
        var hot = AmbientBodyConfirmationProcess.reduce(f.state(), confirmation(f.state(), f.medic(),
                AmbientBodyConfirmed.Boundary.ADMISSION, waiting.standingBody()));
        var draining = AmbientLeaseStateProcess.transition(hot, f.medic(), AmbientLeaseStatus.DRAINING);
        var cold = AmbientLeaseStateProcess.release(draining, new AmbientLeaseReleased(f.medic(),
                waiting.standingBody(), draining.actorLocations().get(f.medic()).condition().health()));
        var prepared = AmbientLeaseStateProcess.prepare(cold,
                AmbientActorProcess.nextLease(cold, f.medic(), new SimInstant(27_002L)));
        var candidates = AmbientPlacementPolicy.candidates(prepared, prepared.ambientLeases().get(f.medic()));
        assertEquals(waiting, candidates.getFirst());
        var alternative = candidates.stream().filter(surface -> !surface.equals(waiting)).findFirst().orElseThrow();
        var next = AmbientBodyConfirmationProcess.reduce(prepared, confirmation(prepared, f.medic(),
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
        state = AmbientBodyConfirmationProcess.reduce(state, confirmation(state, f.medic(), AmbientBodyConfirmed.Boundary.ADMISSION,
                f.port().serviceSurface().standingBody()));
        // Earlier security meal owns arbitration; the medic must leave rather than stand in its socket.
        assertFalse(ServiceAccessCoordinator.depotAvailableForMeal(state, FrontierWorldState.depotId(f.port().settlementId()), f.medic()));
        ResidentMeal meal = state.humanPopulation().meals().get(f.medic());
        List<SurfaceAnchor> route = ResidentMealKnownNavigation.path(state, meal);
        assertTrue(route.size() > 1);
        assertTrue(f.port().accessBoundary().cleared(route.getLast().standingBody()));
        assertTrue(f.port().accessBoundary().allowsWaitingRoute(route));
    }
    @Test void registeredCommandCommitsPlacementAndWakesTheExactServicePoint() {
        Fixture f = fixture();
        var definition = io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition.configuration(f.state().bootstrap());
        var configuration = new io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngineConfiguration<>(
                f.state().bootstrap().worldId(), f.state(), new SimInstant(27_001L), definition.commandPlanner(),
                definition.scheduledPlanner(), definition.reducer(), definition.stateCodec(), definition.projectionMapper(),
                definition.limits(), List.of(ResidentMealProcess.progress(f.state().humanPopulation().meals().get(f.medic()), 27_002L)),
                definition.transactionCommitter());
        var engine = io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngines.create(configuration);
        SurfaceAnchor waiting = AmbientPlacementPolicy.candidates(f.state(), f.state().ambientLeases().get(f.medic())).stream()
                .filter(surface -> f.port().accessBoundary().cleared(surface.standingBody())).findFirst().orElseThrow();
        AmbientBodyConfirmed evidence = confirmation(f.state(), f.medic(), AmbientBodyConfirmed.Boundary.ADMISSION, waiting.standingBody());
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
