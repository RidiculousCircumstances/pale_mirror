package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ResidentMealProcessTest {
    @Test void coldMealMovesOneClaimThroughHandAndConsumesExactlyOneBreadAcrossRestart() {
        FrontierWorldState initial = FrontierWorldState.initial(FrontierBootstrapper.create(
                new WorldId("frontier:resident-meal-cold"), 421L));
        Settlement settlement = initial.bootstrap().settlements().getFirst();
        SubjectId resident = settlement.residents().getFirst().id();
        SubjectId depot = FrontierWorldState.depotId(settlement.id());
        SettlementStructure depotStructure = settlement.structures().stream()
                .filter(value -> value.kind() == StructureKind.DEPOT).findFirst().orElseThrow();
        SurfaceAnchor service = SettlementDepotServicePort.forDepot(depotStructure).serviceSurface();
        var actors = new LinkedHashMap<>(initial.actorLocations());
        actors.put(resident, ActorLocation.standingOn(service));
        SubjectId bread = new SubjectId("lot:resident-meal-cold-bread");
        SubjectId depotAccount = ReferenceContainerCustody.scopeId(depot);
        var ledger = initial.inventory().fungibleResources().transformCold(depotAccount,
                Map.of(new SubjectId("lot:bootstrap-1-wheat"), 64), Map.of(),
                new ResourceLot(bread, settlement.id(), "minecraft:bread", 64, "test", List.of()));
        FrontierWorldState stocked = initial.withChanges(FrontierWorldStateUpdate.begin()
                .actorLocations(actors).inventory(initial.inventory().withFungibleResources(ledger))
                .humanPopulation(initial.humanPopulation().accrueHunger(resident, 24_000L)));
        ResidentMealStarted started = ResidentMealProcess.selectSourceAtYield(stocked, resident, 24_000L).orElseThrow();
        FrontierWorldState state = ResidentMealProcess.reduceStarted(stocked, resident, started);
        ResidentMeal meal = started.meal();
        var due = ResidentMealProcess.progress(meal, 24_001L);
        for (ResidentMeal.Phase phase : List.of(ResidentMeal.Phase.MOVE, ResidentMeal.Phase.TAKE)) {
            var events = ResidentMealProcess.planProgress(state, due);
            var step = (ResidentMealColdStep) events.getFirst().payload();
            assertEquals(phase, step.expectedPhase());
            assertEquals(due.dueAt().ticks(), step.atTick());
            assertEquals(step, FrontierWorldRuntimeDefinition.payloadCodecs().decode(step.type(),
                    FrontierWorldRuntimeDefinition.payloadCodecs().encode(step)));
            state = ResidentMealProcess.reduceColdStep(state, resident, step);
            due = ((io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect.Rescheduled)
                    events.getLast().payload()).replacement();
        }
        assertEquals(ResidentMeal.Phase.CONSUME, state.humanPopulation().meals().get(resident).phase());
        assertEquals(new ResourceCustody.Actor(resident),
                state.inventory().fungibleResources().accounts().get(meal.actorAccountId()).custody());
        assertEquals(64, state.inventory().fungibleResources().totalQuantity(settlement.id(), "minecraft:bread"));
        state = new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(state));
        var consumeEvents = ResidentMealProcess.planProgress(state, due);
        ResidentMealColdStep consume = (ResidentMealColdStep) consumeEvents.getFirst().payload();
        assertEquals(ResidentMeal.Phase.CONSUME, consume.expectedPhase());
        state = ResidentMealProcess.reduceColdStep(state, resident, consume);
        due = ((io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect.Rescheduled)
                consumeEvents.getLast().payload()).replacement();
        assertEquals(63, state.inventory().fungibleResources().totalQuantity(settlement.id(), "minecraft:bread"));
        assertFalse(state.inventory().fungibleResources().claims().containsKey(meal.claimId()));
        assertFalse(state.inventory().fungibleResources().accounts().containsKey(meal.actorAccountId()));
        assertEquals(ResidentNutritionStatus.NOURISHED, state.humanPopulation().nutrition(resident).status());
        FrontierWorldState afterConsumption = state;
        assertThrows(IllegalArgumentException.class, () -> ResidentMealProcess.reduceColdStep(afterConsumption,
                resident, consume));
        var returnEvents = ResidentMealProcess.planProgress(state, due);
        assertInstanceOf(io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect.Consumed.class,
                returnEvents.getLast().payload());
        state = ResidentMealProcess.reduceColdStep(state, resident,
                (ResidentMealColdStep) returnEvents.getFirst().payload());
        assertFalse(state.humanPopulation().meals().containsKey(resident));
        assertEquals(63, state.inventory().fungibleResources().totalQuantity(settlement.id(), "minecraft:bread"));
    }

    @Test void oneHungryIdleResidentReservesOneDepotBreadUnitAndSurvivesRestart() {
        FrontierWorldState initial = FrontierWorldState.initial(FrontierBootstrapper.create(
                new WorldId("frontier:resident-meal-start"), 420L));
        SubjectId settlement = initial.bootstrap().settlements().getFirst().id();
        SubjectId resident = initial.bootstrap().settlements().getFirst().residents().getFirst().id();
        SubjectId depot = FrontierWorldState.depotId(settlement);
        SubjectId account = ReferenceContainerCustody.scopeId(depot);
        SubjectId wheat = new SubjectId("lot:bootstrap-1-wheat");
        SubjectId bread = new SubjectId("lot:resident-meal-test-bread");
        ResourceLot output = new ResourceLot(bread, settlement, "minecraft:bread", 64,
                "test-station-output", List.of());
        FungibleResourceLedger resources = initial.inventory().fungibleResources()
                .transformCold(account, Map.of(wheat, 64), Map.of(), output);
        FrontierWorldState state = initial.withChanges(FrontierWorldStateUpdate.begin()
                .inventory(initial.inventory().withFungibleResources(resources))
                .humanPopulation(initial.humanPopulation().accrueHunger(resident, 24_000L)));
        ResidentMealStarted start = ResidentMealProcess.selectSourceAtYield(state, resident, 24_000L).orElseThrow();
        assertEquals(start, FrontierWorldRuntimeDefinition.payloadCodecs().decode(start.type(),
                FrontierWorldRuntimeDefinition.payloadCodecs().encode(start)));
        FrontierWorldState reserved = ResidentMealProcess.reduceStarted(state, resident, start);
        assertEquals(64, reserved.inventory().fungibleResources().totalQuantity(settlement, "minecraft:bread"));
        assertEquals(1, reserved.inventory().fungibleResources().claims().get(start.meal().claimId()).quantity());
        assertEquals(start.meal(), reserved.humanPopulation().meals().get(resident));
        assertFalse(ResidentMealProcess.selectSourceAtYield(reserved, resident, 24_000L).isPresent());
        assertTrue(FrontierWorldRuntimeDefinition.payloadCodecs().types().contains(start.type()));
        FrontierWorldState recovered = new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(reserved));
        assertEquals(reserved.humanPopulation().meals(), recovered.humanPopulation().meals());
        assertEquals(reserved.inventory().fungibleResources(), recovered.inventory().fungibleResources());
    }
}
