package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.process.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class EquipmentSettlementPlanningTest {
    @Test void missingEquipmentCreatesAnAdmissibleForgeOrderAndMaterialDemandWithoutFixedIngotReserve() {
        var state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:tool-planning"), 41, FrontierRulesets.toolGraybox()));
        var miningHomes = state.extractionSites().deposits().values().stream().map(d -> d.site().settlementId()).collect(java.util.stream.Collectors.toSet());
        var home = state.bootstrap().settlements().stream().filter(town -> miningHomes.contains(town.id())).findFirst().orElseThrow();
        var inventory = state.inventory();
        var player = new InventoryCustody.Player(UUID.fromString("00000000-0000-0000-0000-000000000229"));
        for (var item : List.copyOf(inventory.items().values()))
            if (item.economicOwnerId().equals(home.id()) && state.bootstrap().ruleset().tools().find(item.itemKind()).isPresent())
                inventory = inventory.moveObservedItem(item.id(), item.custody(), player);
        state = state.withInventory(inventory);
        var depot = FrontierWorldState.depotId(home.id());
        var source = FungibleResourceCustodySupport.accountAtContainer(state, depot).orElseThrow().id();
        var material = new SubjectId("lot:equipment-plan-stone"); var staging = new SubjectId("custody:equipment-plan-stone");
        var resources = state.inventory().fungibleResources().issue(new ResourceLot(material, home.id(), "minecraft:cobblestone", 12,
                "fixture:material", List.of()), new CustodyAccount(staging, new ResourceCustody.Container(depot), Map.of(material, 12), Map.of()))
                .transfer(staging, source, Map.of(material, 12), Map.of());
        state = state.withInventory(state.inventory().withFungibleResources(resources));
        assertTrue(state.bootstrap().ruleset().production().automaticPolicies().stream().allMatch(p -> p.outputReserve() == 0));
        var planned = EquipmentProductionPlanning.planned(state, home.id(), 300);
        assertFalse(planned.isEmpty());
        assertTrue(planned.stream().anyMatch(p -> p.request().recipe().ingredient().accepts("minecraft:cobblestone")));
        var demands = SettlementReservePlanning.basis(state, home.id(), 300);
        assertTrue(demands.needs().get("minecraft:cobblestone").immediate() > 0);
        var ready = EquipmentProductionPlanning.ready(state, home.id(), 300).orElseThrow();
        assertTrue(ready.request().recipe().ingredient().accepts("minecraft:cobblestone"));
        var objective = new StrategicObjective(new SubjectId("objective:equipment-plan"), home.id(),
                StrategicObjectiveKind.SETTLEMENT_MAINTAIN_EQUIPMENT, Optional.empty(), 1, StrategicObjectiveStatus.ACTIVE);
        var task = new StrategicTask(new SubjectId("task:equipment-plan"), objective.id(), home.id(), StrategicTaskKind.PRODUCE_AT_STATION,
                Optional.empty(), StrategicOperationSpecifications.requirements(objective.kind()), List.of(), StrategicTaskStatus.PENDING);
        state = state.withStrategicPlans(state.strategicPlans().addObjective(objective).addTask(task));
        var events = ProductionProcess.planStart(state, ProductionProcess.start(task, 300));
        var started = events.stream().map(ProposedEvent::payload).filter(ProductionStarted.class::isInstance)
                .map(ProductionStarted.class::cast).findFirst().orElseThrow(() -> new AssertionError(events));
        assertEquals(ProductionStationSpec.Capability.FABRICATION,
                state.bootstrap().ruleset().production().require(started.job().stationWork().orElseThrow().recipeId()).capability());
        assertNotEquals("minecraft:bread", started.job().outputItemKind());
        state = state.withStrategicPlans(state.strategicPlans().transitionTask(task.id(), StrategicTaskStatus.ACTIVE));
        state = ProductionProcess.reduceStarted(state, home.id(), started);
        assertTrue(EquipmentStockProjection.units(state, home.id()).stream().anyMatch(unit -> unit.id().equals(started.job().outputItemId())));
    }
}
