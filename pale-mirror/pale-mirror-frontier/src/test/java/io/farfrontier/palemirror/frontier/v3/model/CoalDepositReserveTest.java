package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.extraction.*;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CoalDepositReserveTest {
    private static FrontierWorldState initial() {
        return FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:finite-coal"),
                20260918065L, FrontierRulesets.marketQuarryGraybox()));
    }
    @Test void longReserveIsFinitePhysicalOreNotFreeInventoryOrAnExpandedInitialJobPool() {
        var state = initial(); var rules = state.bootstrap().ruleset();
        assertTrue(rules.goodsTrade().supplyPlanning().economics().isPresent());
        assertTrue(rules.initialSettlementStocks().stream().noneMatch(stock -> stock.itemKind().equals("minecraft:coal")));
        assertTrue(state.inventory().fungibleResources().lots().values().stream().noneMatch(lot -> lot.itemKind().equals("minecraft:coal")));
        assertTrue(rules.goodsTrade().supplyPlanning().reserves().isPresent());
        long coal = 0; int producers = 0;
        for (var deposit : state.extractionSites().deposits().values()) {
            assertEquals(64, deposit.site().layout().cells().size(), "geology is not a pre-admitted giant work pool");
            var ore = ExtractionWorksiteBlocks.declared(deposit).stream()
                    .filter(block -> block.block().kind().equals("minecraft:coal_ore")).toList();
            if (ore.isEmpty()) continue;
            producers++;
            assertTrue(Set.of(1336, 384).contains(ore.size()), "primary and secondary finite ore bodies"); coal += ore.size();
            var outside = ore.stream().filter(block -> block.key().role() == WorksiteBlock.Role.NATURAL_TERRAIN).findFirst().orElseThrow();
            var sample = ExtractionGroundKnowledge.forSite(state, deposit).at(outside.position()).orElseThrow();
            assertEquals("minecraft:coal_ore", sample.block().kind());
            assertEquals(io.farfrontier.palemirror.frontier.v3.model.geometry.KnownBlockGeometry.Sample.Permission.PUBLIC, sample.permission());
            assertTrue(state.bootstrap().bounds().contains(outside.position()));
        }
        assertEquals(12, producers); assertEquals(10320, coal);
        long required = ProductionFuelReserve.requiredItems(rules.production(), rules.workCatalog(), BakeryRecipes.BREAD,
                "minecraft:coal", 64, state.bootstrap().settlements().size(), 100_000);
        assertTrue(coal >= required, "finite veins cover every existing bakery at the maximum normal rate");
        assertEquals(state, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(state)));
    }

    @Test void depletedInitialFaceStillRequestsAndAdmitsOreFromDeclaredNeighbouringTerrain() {
        var state = withoutFinishedFood(initial());
        // Isolated stock fixture removes stone demand; only coal can justify resuming development.
        for (var home : state.bootstrap().settlements()) {
            var lot = new SubjectId("lot:full-stone-" + home.id().value().replace(':', '-'));
            var temporary = new SubjectId("custody:full-stone-" + home.id().value().replace(':', '-'));
            int reserve = state.bootstrap().ruleset().extraction().reserveItems();
            var ledger = state.inventory().fungibleResources().issue(new ResourceLot(lot, home.id(), "minecraft:cobblestone", reserve,
                    "test", List.of()), new CustodyAccount(temporary, new ResourceCustody.WorldCarrier(
                    UUID.nameUUIDFromBytes(home.id().value().getBytes(java.nio.charset.StandardCharsets.UTF_8))), Map.of(lot, reserve), Map.of()));
            ledger = ledger.transfer(temporary, ReferenceContainerCustody.scopeId(FrontierWorldState.depotId(home.id())), Map.of(lot, reserve), Map.of());
            state = state.withInventory(state.inventory().withFungibleResources(ledger));
        }
        var prior = state.extractionSites().deposits().values().stream()
                .filter(deposit -> deposit.site().layout().cells().stream().anyMatch(cell -> cell.definition().before().kind().equals("minecraft:coal_ore")))
                .findFirst().orElseThrow();
        var next = new ExtractionDeposit(prior.site(), prior.cells(), prior.geometry(), new WorkAreaDevelopment(2, prior.cells().keySet()));
        for (long cell : next.cells().keySet()) next = next.extracted(cell, 1, "fixture:prior-extraction-" + cell);
        state = state.withChanges(FrontierWorldStateUpdate.begin().extractionSites(state.extractionSites().replace(next)));
        state = new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(state));
        var exhausted = state.extractionSites().deposits().get(prior.site().id());
        assertFalse(ProductionStockDemand.read(state, prior.site().settlementId(), "minecraft:cobblestone", 0L).requested());
        assertTrue(ProductionStockDemand.read(state, prior.site().settlementId(), "minecraft:coal", 0L).requested());
        assertTrue(ExtractionStockDemand.requiredCells(state, exhausted, Optional.empty(), 0L).isEmpty());
        assertTrue(ExtractionStockDemand.requested(state, exhausted, Optional.empty(), 0L), "unadmitted geology is not depletion");
        var extension = ExtractionAreaPlanning.proposal(state, prior.site().id(), 0L).orElseThrow();
        assertTrue(extension.columns().stream().flatMap(column -> column.clearance().stream())
                .anyMatch(sample -> sample.block().kind().equals("minecraft:coal_ore")));
        var territory = SettlementStorageTerritory.areas(state);
        var after = ExtractionAreaPlanning.apply(state, prior.site().id(), extension, 1000);
        var expandedTerritory = SettlementStorageTerritory.areas(after);
        assertNotSame(territory, expandedTerritory, "a changed site declaration invalidates its derived land bounds");
        assertEquals(expandedTerritory, SettlementStorageTerritory.areas(
                new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(after))),
                "cached expanded land must agree with independent recovered declarations");
        assertTrue(after.extractionSites().deposits().get(prior.site().id()).site().layout().cells().stream()
                .anyMatch(cell -> cell.id() > 64 && cell.definition().before().kind().equals("minecraft:coal_ore")));
        assertEquals(state.inventory(), after.inventory(), "admitting a front never credits coal");
    }
    static FrontierWorldState withoutFinishedFood(FrontierWorldState state) {
        var ledger = state.inventory().fungibleResources();
        for (var account : List.copyOf(ledger.accounts().values())) {
            var bread = new TreeMap<SubjectId, Integer>();
            for (var lot : account.lotQuantities().entrySet())
                if (ledger.lots().get(lot.getKey()).itemKind().equals("minecraft:bread") && lot.getValue() > 1)
                    bread.put(lot.getKey(), lot.getValue() - 1);
            if (!bread.isEmpty()) ledger = ledger.destroy(account.id(), bread, Map.of());
        }
        return state.withInventory(state.inventory().withFungibleResources(ledger));
    }
}
