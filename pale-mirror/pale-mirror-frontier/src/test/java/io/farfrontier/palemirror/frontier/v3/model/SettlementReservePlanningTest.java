package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.model.supply.SupplyReservePlanner.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SettlementReservePlanningTest {
    private static FrontierWorldState initial() {
        return FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:reserve-test"), 20260918065L, FrontierRulesets.marketQuarryGraybox()));
    }
    @Test void allActiveConsumersShareTheForecastAndRecoveryPreservesIt() {
        var state = initial(); var codec = new FrontierWorldStateCodec(); var restored = codec.decode(codec.encode(state));
        for (var home : state.bootstrap().settlements()) {
            var targets = SettlementReservePlanning.read(state, home.id(), 0L);
            var participant = state.companies().goodsTrade().participants().participants().get(home.id());
            var view = GoodsParticipantAssessment.view(state, participant, 0L);
            var plan = view.supplyPlan().orElseThrow();
            assertEquals(plan, GoodsParticipantAssessment.view(restored,
                    restored.companies().goodsTrade().participants().participants().get(home.id()), 0L).supplyPlan().orElseThrow());
            assertEquals(targets, SettlementReservePlanning.read(restored, home.id(), 0L));
            assertEquals(0, targets.get("minecraft:cobblestone").quantity(), "no invented construction consumption");
            assertTrue(targets.get("minecraft:coal").quantity() > 0);
            assertEquals(targets.get("minecraft:bread").consumption().times(Rate.of(3, 1)), targets.get("minecraft:wheat").consumption());
            assertEquals(targets.get("minecraft:bread").consumption().times(Rate.of(1, 20)), targets.get("minecraft:coal").consumption());
            for (var commodity : state.bootstrap().ruleset().goodsTrade().policies().get(participant.policy())) {
                var target = plan.reserves().get(commodity.itemKind());
                assertEquals(targets.get(commodity.itemKind()).consumption(), target.consumption(),
                        "economic allocation may change targets, never the owner's appetite");
                assertEquals(view.stocks().get(commodity.itemKind()).protectedMinimum(), PublicStockProtection.minimum(state, home.id(), commodity.itemKind(), 0L));
                assertTrue(view.target(commodity) >= target.quantity());
                var work = ProductionStockDemand.read(state, home.id(), commodity.itemKind(), 0L);
                assertEquals(plan.economy().orElseThrow().additionalProduction().get(commodity.itemKind()).longValue(),
                        work.target() - work.covered(), "production consumes the net new work order, not the reserve target twice");
            }
            assertEquals(0, PublicStockProtection.minimum(state, home.id(), "minecraft:pickaxe", 0L));
        }
    }
    @Test void missingAndDuplicateOwnersFailClosed() {
        assertThrows(IllegalArgumentException.class, () -> SettlementReservePlanning.register(List.of()));
        assertThrows(IllegalArgumentException.class, () -> SettlementReservePlanning.register(List.of(new NutritionReserveSupply(), new NutritionReserveSupply())));
    }
    @Test void datedDemandAccruesNutritionWithoutChangingRetainedResidentState() {
        var state = initial();
        var home = state.bootstrap().settlements().getFirst().id();
        var retained = SettlementReservePlanning.retainedBasis(state, home);
        var life = state.bootstrap().ruleset().residentLife();
        long now = 48_000;
        long expected = state.humanPopulation().residents().values().stream()
                .filter(resident -> resident.settlementId().equals(home))
                .mapToLong(resident -> Math.ceilDiv(state.humanPopulation().nutrition(resident.id())
                        .accrueThrough(now, life, resident.characteristics().effectiveMetabolismPermille(now))
                        .nutritionWanted(life), life.breadNutritionUnits())).sum();
        var dated = SettlementReservePlanning.basis(state, home, now);
        assertEquals(expected, dated.needs().get(SettlementFoodPolicy.BREAD).immediate());
        assertTrue(expected > retained.needs().get(SettlementFoodPolicy.BREAD).immediate());
        assertSame(dated, SettlementReservePlanning.basis(state, home, now));
        assertSame(retained, SettlementReservePlanning.retainedBasis(state, home));
        var participant = state.companies().goodsTrade().participants().participants().get(home);
        assertEquals(dated, GoodsParticipantAssessment.domestic(state, participant, now)
                .facts().orElseThrow().basis());
    }
    @Test void destroyedMachineWithdrawsItsInputForecastButNotTheResidentsAppetite() {
        var state = initial(); var home = state.bootstrap().settlements().getFirst();
        var conditions = new HashMap<>(state.structureConditions());
        home.structures().stream().filter(s -> s.kind() == StructureKind.WORKSHOP)
                .forEach(s -> conditions.put(s.id(), StructureCondition.DESTROYED));
        var damaged = state.withChanges(FrontierWorldStateUpdate.begin().structureConditions(conditions));
        var before = SettlementReservePlanning.read(state, home.id(), 0L); var after = SettlementReservePlanning.read(damaged, home.id(), 0L);
        assertEquals(before.get("minecraft:bread").consumption(), after.get("minecraft:bread").consumption());
        assertEquals(0, after.get("minecraft:coal").quantity()); assertEquals(0, after.get("minecraft:wheat").quantity());
        assertTrue(after.get("minecraft:bread").quantity() > 0);
        assertEquals(TimingBasis.INITIAL_ESTIMATE, after.get("minecraft:bread").timing().basis());
    }
}
