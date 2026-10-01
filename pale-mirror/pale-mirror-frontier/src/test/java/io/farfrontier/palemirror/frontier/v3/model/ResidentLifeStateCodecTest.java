package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.process.ResidentMealProcess;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ResidentLifeStateCodecTest {
    @Test void residentLifeEnumsHaveStableClosedWireTags() {
        assertEquals(0, FrontierWireTags.tag(SettlementDailySchedule.Window.WORK));
        assertEquals(1, FrontierWireTags.tag(SettlementDailySchedule.Window.FREE));
        assertEquals(2, FrontierWireTags.tag(ResidentMeal.Phase.CONSUME));
        assertEquals(3, FrontierWireTags.tag(ResidentActivityChoice.Wait.HAND_OCCUPIED));
        assertThrows(IllegalArgumentException.class,
                () -> FrontierWireTags.require(ResidentMeal.Phase.class, 4));
    }

    @Test void exactPolicyAndRetainedMealSurviveSnapshotAndUnrelatedPopulationMutation() {
        FrontierWorldState base = FrontierWorldState.initial(FrontierBootstrapper.create(
                new WorldId("frontier:resident-life-codec"), 417L));
        Settlement settlement = base.bootstrap().settlements().getFirst();
        SubjectId resident = settlement.residents().getFirst().id();
        SubjectId depot = FrontierWorldState.depotId(settlement.id());
        SettlementDailySchedule policy = new SettlementDailySchedule(24_000,
                java.util.List.of(new SettlementDailySchedule.Segment(0, 8_000, SettlementDailySchedule.Window.WORK),
                        new SettlementDailySchedule.Segment(8_000, 24_000, SettlementDailySchedule.Window.FREE)));
        SubjectId account = ReferenceContainerCustody.scopeId(depot);
        SubjectId bread = new SubjectId("lot:resident-life-codec-bread");
        FungibleResourceLedger resources = base.inventory().fungibleResources().transformCold(account,
                java.util.Map.of(new SubjectId("lot:bootstrap-1-wheat"), 64), java.util.Map.of(),
                new ResourceLot(bread, settlement.id(), "minecraft:bread", 64, "test", java.util.List.of()));
        FrontierWorldState stocked = base.withInventory(base.inventory().withFungibleResources(resources));
        stocked = stocked.withHumanPopulation(stocked.humanPopulation().accrueHunger(resident, 24_000L));
        var started = ResidentMealProcess.selectSourceAtYield(stocked, resident, 24_000L).orElseThrow();
        FrontierWorldState eating = ResidentMealProcess.reduceStarted(stocked, resident, started);
        ResidentMeal meal = started.meal();
        HumanPopulation population = eating.humanPopulation().withSchedule(settlement.id(), policy);
        population = population.transitionHealth(resident, ResidentHealthStatus.EXPOSED, 24_001L);
        assertEquals(meal, population.meals().get(resident));
        FrontierWorldState state = eating.withHumanPopulation(population);
        FrontierWorldState recovered = new FrontierWorldStateCodec().decode(
                new FrontierWorldStateCodec().encode(state));
        assertEquals(policy, recovered.humanPopulation().schedule(settlement.id()));
        assertEquals(meal, recovered.humanPopulation().meals().get(resident));
        assertEquals(1, recovered.inventory().fungibleResources().claims().get(meal.claimId()).quantity());
    }

    @Test void activeMealCannotBeSilentlyRelocated() {
        FrontierWorldState base = FrontierWorldState.initial(FrontierBootstrapper.create(
                new WorldId("frontier:resident-meal-migration"), 418L));
        Settlement settlement = base.bootstrap().settlements().getFirst();
        SubjectId resident = settlement.residents().getFirst().id();
        SubjectId depot = FrontierWorldState.depotId(settlement.id());
        ResidentMeal meal = new ResidentMeal(resident, settlement.id(), depot,
                base.actorLocations().get(resident).supportingSurface(),
                ReferenceContainerCustody.scopeId(depot), new SubjectId("custody:resident-meal-test"),
                new FoodPortion(FoodCatalog.BREAD, 1_000, java.util.Map.of(new SubjectId("lot:bread-test"), 1)), new SubjectId("claim:meal-test"),
                Optional.empty(), ResidentMeal.Phase.MOVE, 24_000L, Optional.empty());
        HumanPopulation population = base.humanPopulation().withMeal(meal);
        ResidentProfile profile = population.resident(resident);
        assertThrows(IllegalArgumentException.class, () -> population.migrate(resident,
                profile.householdId(), settlement.id()));
    }
}
