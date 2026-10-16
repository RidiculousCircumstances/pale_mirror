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
            var targets = SettlementReservePlanning.read(state, home.id());
            var participant = state.companies().goodsTrade().participants().participants().get(home.id());
            var view = GoodsParticipantView.read(state, participant);
            assertEquals(targets, view.supplyPlan().orElseThrow().reserves());
            assertEquals(targets, SettlementReservePlanning.read(restored, home.id()));
            assertEquals(0, targets.get("minecraft:cobblestone").quantity(), "no invented construction consumption");
            assertTrue(targets.get("minecraft:coal").quantity() > 0);
            assertEquals(targets.get("minecraft:bread").consumption().times(Rate.of(3, 1)), targets.get("minecraft:wheat").consumption());
            assertEquals(targets.get("minecraft:bread").consumption().times(Rate.of(1, 20)), targets.get("minecraft:coal").consumption());
            for (var commodity : state.bootstrap().ruleset().goodsTrade().policies().get(participant.policy())) {
                var target = targets.get(commodity.itemKind());
                assertEquals(target.protectedQuantity(), PublicStockProtection.minimum(state, home.id(), commodity.itemKind()));
                assertTrue(view.target(commodity) >= target.quantity());
                assertEquals(view.target(commodity), ProductionStockDemand.read(state, home.id(), commodity.itemKind()).target());
            }
            assertEquals(0, PublicStockProtection.minimum(state, home.id(), "minecraft:pickaxe"));
        }
    }
    @Test void missingAndDuplicateOwnersFailClosed() {
        assertThrows(IllegalArgumentException.class, () -> SettlementReservePlanning.register(List.of()));
        assertThrows(IllegalArgumentException.class, () -> SettlementReservePlanning.register(List.of(new NutritionReserveSupply(), new NutritionReserveSupply())));
    }
    @Test void destroyedMachineWithdrawsItsInputForecastButNotTheResidentsAppetite() {
        var state = initial(); var home = state.bootstrap().settlements().getFirst();
        var conditions = new HashMap<>(state.structureConditions());
        home.structures().stream().filter(s -> s.kind() == StructureKind.WORKSHOP)
                .forEach(s -> conditions.put(s.id(), StructureCondition.DESTROYED));
        var damaged = state.withChanges(FrontierWorldStateUpdate.begin().structureConditions(conditions));
        var before = SettlementReservePlanning.read(state, home.id()); var after = SettlementReservePlanning.read(damaged, home.id());
        assertEquals(before.get("minecraft:bread").consumption(), after.get("minecraft:bread").consumption());
        assertEquals(0, after.get("minecraft:coal").quantity()); assertEquals(0, after.get("minecraft:wheat").quantity());
        assertTrue(after.get("minecraft:bread").quantity() > 0);
        assertEquals(TimingBasis.INITIAL_ESTIMATE, after.get("minecraft:bread").timing().basis());
    }
}
