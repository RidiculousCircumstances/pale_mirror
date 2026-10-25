package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EconomicRecoveryTest {
    @Test void exhaustedToolsRemoveExecutableOutputButNotTheValueOfRestoringAdmittedLand() {
        var state = prepared(); var home = new SubjectId("settlement:7");
        assertFalse(FieldWorkForecast.workers(state, home).isEmpty(), "fixture starts with productive tools");
        state = exhaust(state, home);
        var participant = state.companies().goodsTrade().participants().participants().get(home);
        var before = state;
        var assessment = SettlementEconomicEvaluation.domestic(state, participant, 5000);
        assertTrue(FieldWorkForecast.workers(state, home).isEmpty());
        assertTrue(assessment.facts().orElseThrow().capacities().stream().noneMatch(p -> p.product().equals("minecraft:wheat")));
        var references = new TreeMap<String, FixedScalar>();
        state.bootstrap().ruleset().goodsTrade().policies().get(participant.policy())
                .forEach(c -> references.put(c.itemKind(), c.minimumSellPrice()));
        var prices = GoodsProductionPricing.assess(state, assessment, references);
        assertTrue(prices.opportunities().get("minecraft:wheat").raw() > 0);
        assertFalse(prices.alternatives().containsKey("minecraft:wheat"), "replacement valuation cannot become an executable make alternative");
        assertSame(before.inventory(), state.inventory(), "valuation cannot create a replacement tool or resource");
        var sites = state.resourceSites();
        for (var field : state.resourceSiteDescriptors().values()) if (field.settlementId().equals(home))
            sites = sites.replace(sites.site(field.id()).destroyed());
        assertTrue(SupplyCapacityComposition.forValuation(state.withResourceSites(sites), participant).stream()
                .noneMatch(p -> p.product().equals("minecraft:wheat")), "destroyed land is lost capacity, not a transient tool shortage");
    }

    @Test void cashPoorRestartPlansOneRepairRatherThanAnUnobtainableIronFleet() {
        var state = exhaust(prepared(), new SubjectId("settlement:7"));
        var home = new SubjectId("settlement:7");
        state = state.withInventory(state.inventory().withEconomics(state.inventory().economics()
                .transfer(home, new SubjectId("settlement:6"), FixedScalar.whole(96))));
        var plans = EquipmentProductionPlanning.planned(state, home, 5000);
        assertFalse(plans.isEmpty());
        var first = plans.getFirst();
        assertTrue(first.request().maintenance().isPresent(), first.toString());
        assertEquals(1, first.operations());
        assertTrue(first.request().recipe().ingredient().accepts("minecraft:cobblestone"));
        assertTrue(EquipmentProductionPlanning.ready(state, home, 5000).isEmpty(), "an estimated import is not present material");
        assertEquals(1, new EquipmentReserveSupply().readAt(state, home, 5000).needs().get("minecraft:cobblestone").immediate());
        var requirement = new EquipmentReserveSupply().readAt(state, home, 5000).requiredInputs().get("minecraft:cobblestone");
        assertEquals(1, requirement.quantity());
        assertEquals(0, requirement.withinTicks());
        var participant = state.companies().goodsTrade().participants().participants().get(home);
        var assessment = SettlementEconomicEvaluation.domestic(state, participant, 5000);
        var material = assessment.view().supplyPlan().orElseThrow().require("minecraft:cobblestone");
        assertTrue(material.protectedInput() > 0);
        assertEquals(0, material.deadlineTicks(), "a failed productive pool is not an optional undated stock target");
        var reviewed = GoodsParticipantPricing.review(state, participant, 5000, assessment);
        assertTrue(reviewed.get("minecraft:cobblestone").bid().raw() > FixedScalar.ONE.raw(),
                "manufacturing substitution value must allow a real input offer above the historical one-coin reference");

        var depot = FrontierWorldState.depotId(home);
        var account = FungibleResourceCustodySupport.accountAtContainer(state, depot).orElseThrow().id();
        var lot = new SubjectId("lot:recovery-stone"); var staging = new SubjectId("custody:recovery-stone");
        var resources = state.inventory().fungibleResources().issue(new ResourceLot(lot, home, "minecraft:cobblestone", 1,
                "fixture:recovery", List.of()), new CustodyAccount(staging, new ResourceCustody.Container(depot), Map.of(lot, 1), Map.of()))
                .transfer(staging, account, Map.of(lot, 1), Map.of());
        state = state.withInventory(state.inventory().withFungibleResources(resources));
        assertTrue(EquipmentProductionPlanning.ready(state, home, 5000).orElseThrow().request().maintenance().isPresent());
    }
    @Test void depletedPicksDoNotEraseKnownMineralReplacementValueOrAuthorizeMining() {
        var state = prepared(); var home = new SubjectId("settlement:6");
        var participant = state.companies().goodsTrade().participants().participants().get(home);
        var before = SupplyCapacityComposition.forValuation(state, participant);
        assertTrue(before.stream().anyMatch(p -> p.product().equals("minecraft:coal")));
        var inventory = state.inventory();
        for (var item : List.copyOf(inventory.items().values())) {
            var tool = state.bootstrap().ruleset().tools().find(item.itemKind());
            if (item.economicOwnerId().equals(home) && tool.isPresent() && item.condition().durable()) inventory = inventory.changeCondition(item,
                    new ItemCondition(item.condition().maximumDamage(), item.condition().maximumDamage() - tool.orElseThrow().maintenanceReserve()));
        }
        state = state.withInventory(inventory);
        var assessment = SettlementEconomicEvaluation.domestic(state, participant, 5000);
        assertTrue(assessment.facts().orElseThrow().capacities().stream().noneMatch(p -> p.product().equals("minecraft:coal")));
        assertEquals(before.stream().filter(p -> p.product().equals("minecraft:coal")).toList(),
                SupplyCapacityComposition.forValuation(state, participant).stream().filter(p -> p.product().equals("minecraft:coal")).toList());
    }
    private static FrontierWorldState exhaust(FrontierWorldState state, SubjectId home) {
        var inventory = state.inventory(); var tools = state.bootstrap().ruleset().tools();
        for (var item : List.copyOf(inventory.items().values())) {
            var definition = tools.find(item.itemKind());
            if (item.economicOwnerId().equals(home) && definition.isPresent()
                    && definition.orElseThrow().supports(FieldHarvestExtraction.DEFINITION.toolRequirement()))
                inventory = inventory.changeCondition(item, new ItemCondition(definition.orElseThrow().durability(),
                        definition.orElseThrow().durability() - definition.orElseThrow().maintenanceReserve()));
        }
        return state.withInventory(inventory);
    }
    private static FrontierWorldState prepared() {
        var state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:economic-recovery"),
                126, FrontierRulesets.toolGraybox()));
        for (var id : state.resourceSites().sites().keySet().stream().sorted().toList()) {
            var events = io.farfrontier.palemirror.frontier.v3.process.ResourceSiteProcess.planPreparation(state,
                    io.farfrontier.palemirror.frontier.v3.process.ResourceSiteProcess.preparation(id, 4000));
            state = io.farfrontier.palemirror.frontier.v3.process.ResourceSiteProcess.reducePreparationStarted(state, id,
                    (ResourceSitePreparationStarted) events.getFirst().payload());
            state = io.farfrontier.palemirror.frontier.v3.process.ResourceSiteProcess.reducePrepared(state, id,
                    (ResourceSitePrepared) events.get(1).payload());
        }
        return state;
    }
}
