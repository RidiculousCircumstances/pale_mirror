package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.process.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class EquipmentSettlementPlanningTest {
    @Test void recoveryStockExposureSpansParcelsButDoesNotMultiplyAcrossBuyers() {
        var state = CultivationPlanningTest.prepared(); var home = new SubjectId("settlement:7");
        var participant = state.companies().goodsTrade().participants().participants().get(home);
        String item = "minecraft:wheat";
        var recovery = new io.farfrontier.palemirror.frontier.v3.model.market.MarketPricePolicy.Quote(FixedScalar.whole(2),
                FixedScalar.whole(3), FixedScalar.whole(2), 0, 200, Optional.empty(),
                new io.farfrontier.palemirror.frontier.v3.model.market.StockSalePlan(
                        io.farfrontier.palemirror.frontier.v3.model.market.StockSalePlan.Kind.OPERATING_RECOVERY, 83, FixedScalar.whole(100).raw()));
        participant = participant.withPrices(Map.of(item, recovery));
        var zero = Map.of(item, 0L);
        var economic = new io.farfrontier.palemirror.frontier.v3.model.economy.EconomicPlan(1000, zero, zero, zero, 0, 0, 0,
                List.of(), List.of(), Set.of(), Map.of(item, 1L), Set.of(), Map.of(item, 200L), Map.of(item, FixedScalar.whole(100).raw()));
        var plan = new io.farfrontier.palemirror.frontier.v3.model.supply.SupplyPlanner.Plan(Map.of(), Map.of(), Map.of(), Optional.of(economic));
        var stock = Map.of(item, new GoodsParticipantView.Stock(200, 200, 0));
        var commodity = state.bootstrap().ruleset().goodsTrade().policies().get(participant.policy()).stream().filter(c -> c.itemKind().equals(item)).findFirst().orElseThrow();
        var view = new GoodsParticipantView(participant, 1, FixedScalar.ZERO, stock, Map.of(), Optional.of(plan), Set.of(), FixedScalar.ZERO);
        assertEquals(83, view.saleable(commodity));
        assertEquals(200, view.releasableStock(commodity), "new pricing sees stock, independent of the previous sales cap");
        var bound = new GoodsParticipantView(participant, 1, FixedScalar.ZERO, stock, Map.of(), Optional.of(plan), Set.of(), FixedScalar.whole(128));
        assertEquals(19, bound.saleable(commodity), "64+19 fills one economical lot even if the first parcel funds the minimum");
        var complete = new GoodsParticipantView(participant, 1, FixedScalar.ZERO, stock, Map.of(), Optional.of(plan), Set.of(), FixedScalar.whole(166));
        assertEquals(0, complete.saleable(commodity));
        var paid = new GoodsParticipantView(participant, 1, FixedScalar.whole(100), stock, Map.of(), Optional.of(plan), Set.of(), FixedScalar.ZERO);
        assertEquals(0, paid.saleable(commodity), "paid funds close recovery authorization immediately");
        var finalParticipant = participant;
        assertThrows(IllegalArgumentException.class, () -> finalParticipant.withPrices(Map.of(item, recovery, "minecraft:coal", recovery)));
    }
    @Test void healthyToolsDeclareFutureRenewalWithoutManufacturingStockOrRepairOrders() {
        var state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:healthy-renewal"), 126L,
                FrontierRulesets.installed("frontier-v3-quarry-graybox-r27")));
        for (var id : state.resourceSites().sites().keySet().stream().sorted().toList()) {
            var events = ResourceSiteProcess.planPreparation(state, ResourceSiteProcess.preparation(id, 4000));
            state = ResourceSiteProcess.reducePreparationStarted(state, id, (ResourceSitePreparationStarted) events.getFirst().payload());
            state = ResourceSiteProcess.reducePrepared(state, id, (ResourceSitePrepared) events.get(1).payload());
        }
        var home = new SubjectId("settlement:7");
        var before = state.inventory();
        var facts = new EquipmentReserveSupply().readAt(state, home, 5000);
        assertFalse(facts.renewalInputs().isEmpty(), "ongoing work spends healthy tools' remaining service life");
        var basis = SettlementReservePlanning.basis(state, home, 5000);
        facts.renewalInputs().forEach((item, q) -> assertTrue(basis.renewalInputs().get(item) >= q));
        assertSame(before, state.inventory());
    }
    @Test void admittedRenewableFieldsPlanToolReplacementBeforeTheLastCurrentCrop() {
        var state = CultivationPlanningTest.prepared();
        var home = new SubjectId("settlement:7");
        var demand = new FieldEquipmentDemand().read(state, home).getFirst();
        long cells = state.resourceSiteDescriptors().values().stream().filter(site -> site.settlementId().equals(home))
                .mapToLong(site -> site.layout().cells().size()).sum();
        assertTrue(demand.plannedWear() > cells, "continuing cultivation needs tools for repeated harvests, not one pass");
        var sites = state.resourceSites();
        for (var field : state.resourceSiteDescriptors().values()) if (field.settlementId().equals(home))
            sites = sites.planSowing(field.id(), Set.of());
        var fallow = new FieldEquipmentDemand().read(state.withResourceSites(sites), home);
        assertTrue(fallow.isEmpty() || fallow.getFirst().plannedWear() <= cells,
                "stopping renewal must not reserve future cycles or invent work");
        var codec = new io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec();
        assertEquals(List.of(demand), new FieldEquipmentDemand().read(codec.decode(codec.encode(state)), home));
    }

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
