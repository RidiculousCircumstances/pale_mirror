package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ResidentMealDisplacedPickupTest {
    @Test void displacedPickupReapproachesWithoutFoodTeleportOrLostReservation() {
        var initial = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:meal-displaced-pickup"), 421L));
        var home = initial.bootstrap().settlements().getFirst();
        var id = home.residents().getFirst().id();
        var depot = FrontierWorldState.depotId(home.id());
        var resources = initial.inventory().fungibleResources().transformCold(ReferenceContainerCustody.scopeId(depot),
                java.util.Map.of(new SubjectId("lot:bootstrap-1-wheat"), 64), java.util.Map.of(),
                new ResourceLot(new SubjectId("lot:displaced-meal-bread"), home.id(), ResidentMeal.BREAD_KIND, 64, "test", java.util.List.of()));
        var state = initial.withChanges(FrontierWorldStateUpdate.begin().inventory(initial.inventory().withFungibleResources(resources))
                .humanPopulation(initial.humanPopulation().accrueHunger(id, 27_000L)));
        var started = ResidentMealProcess.selectSourceAtYield(state, id, 27_000L).orElseThrow();
        state = ResidentMealProcess.reduceStarted(state, id, started);
        long now = 27_001L;
        for (int turn = 0; state.humanPopulation().meals().get(id).phase() != ResidentMeal.Phase.TAKE && turn < 8; turn++) {
            var current = state.humanPopulation().meals().get(id);
            now = current.coldTravel().map(route -> route.arrivalTick()).orElse(now + 1);
            state = ResidentMealProcess.reduceColdStep(state, id, ResidentMealProcess.planColdStep(state, id, now).orElseThrow());
        }
        var pickup = state.humanPopulation().meals().get(id);
        assertEquals(ResidentMeal.Phase.TAKE, pickup.phase());
        var atStation = state;
        assertDoesNotThrow(() -> ActorItemCustody.transferCold(atStation, ResidentMealProcess.takeOrder(atStation, pickup)));
        var physicalStation = ModeledActorBodyFacts.present(atStation, id);
        assertThrows(IllegalArgumentException.class, () -> ActorItemCustody.transferCold(physicalStation,
                ResidentMealProcess.takeOrder(physicalStation, pickup)), "body custody excludes COLD even without an ambient lease");
        var station = ResidentMealProcess.serviceSurface(state, pickup);
        var displaced = pickup.clearingSurface().standingBody();
        assertNotEquals(station.standingBody(), displaced);
        state = state.withActorBody(id, displaced);
        var before = state;
        now++;
        assertTrue(ResidentMealProcess.planColdStep(state, id, now).isEmpty());
        var event = assertInstanceOf(ResidentMealApproachRequired.class,
                ResidentMealProcess.planProgress(state, ResidentMealProcess.progress(pickup, now)).getFirst().payload());
        var codecs = FrontierWorldRuntimeDefinition.payloadCodecs();
        assertEquals(event, codecs.decode(event.type(), codecs.encode(event)));
        DiagnosticProducerContract.requireAdmitted(event);
        assertThrows(IllegalArgumentException.class, () -> ResidentMealApproach.reduce(before, id,
                new ResidentMealApproachRequired(event.execution(), station.standingBody())));
        var hot = ModeledActorBodyFacts.present(before, id);
        assertTrue(ResidentMealApproach.plan(hot, pickup).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> ResidentMealApproach.reduce(hot, id, event));
        var pending = before.withHumanPopulation(before.humanPopulation().advanceMeal(pickup, pickup.prepare(
                new ResidentMealPhysicalStep(ResidentMeal.Phase.TAKE, 0, pickup.portion().quantity(),
                        1L, 1L, 1L, pickup.executionId()))));
        assertTrue(ResidentMealApproach.plan(pending, pending.humanPopulation().meals().get(id)).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> ResidentMealApproach.reduce(pending, id, event));
        state = ResidentMealApproach.reduce(state, id, event);
        assertEquals(before.inventory(), state.inventory());
        assertEquals(before.actorLocations(), state.actorLocations());
        var resumed = state.humanPopulation().meals().get(id);
        assertEquals(ResidentMeal.Phase.MOVE, resumed.phase());
        assertEquals(pickup.executionId(), resumed.executionId());
        assertEquals(pickup.claimId(), resumed.claimId());
        var codec = new FrontierWorldStateCodec();
        state = codec.decode(codec.encode(state));
        for (int turn = 0; state.humanPopulation().meals().containsKey(id) && turn < 20; turn++) {
            var current = state.humanPopulation().meals().get(id);
            now = current.coldTravel().map(route -> route.arrivalTick()).orElse(now + 1);
            state = ResidentMealProcess.reduceColdStep(state, id, ResidentMealProcess.planColdStep(state, id, now).orElseThrow());
        }
        assertFalse(state.humanPopulation().meals().containsKey(id));
        assertFalse(state.inventory().fungibleResources().claims().containsKey(pickup.claimId()));
        assertEquals(64 - pickup.portion().quantity(), state.inventory().fungibleResources().totalQuantity(home.id(), ResidentMeal.BREAD_KIND));
    }
}
