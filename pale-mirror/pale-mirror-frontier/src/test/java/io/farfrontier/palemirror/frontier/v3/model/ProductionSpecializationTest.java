package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.extraction.*;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ProductionSpecializationTest {
    @Test void demandedFuelAndItsStoneAccessOutrankUnrelatedStoneThroughTheRealSelectionAfterRecovery() {
        var state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:source-priority"),
                41, FrontierRulesets.marketQuarryGraybox()));
        var deposit = state.extractionSites().deposits().values().stream()
                .filter(value -> value.site().settlementId().equals(new SubjectId("settlement:1"))).findFirst().orElseThrow();
        var home = deposit.site().settlementId();
        // Stock fixture creates real replenishment demand, not an assertion that
        // an empty fuel reserve is automatically an emergency with full food stores.
        var account = FungibleResourceCustodySupport.accountAtContainer(state, FrontierWorldState.depotId(home)).orElseThrow();
        var bread = account.lotQuantities().entrySet().stream().filter(entry -> state.inventory().fungibleResources().lots()
                .get(entry.getKey()).itemKind().equals("minecraft:bread")).findFirst().orElseThrow();
        var demandedState = state.withInventory(state.inventory().withFungibleResources(state.inventory().fungibleResources()
                .destroy(account.id(), Map.of(bread.getKey(), bread.getValue() - 64), Map.of())));
        var priorities = ExtractionPriorities.read(demandedState, deposit, Optional.empty());
        assertTrue(priorities.products().get("minecraft:coal").compareTo(priorities.products().get("minecraft:cobblestone")) < 0);
        var coal = deposit.site().layout().cells().stream()
                .filter(cell -> cell.definition().before().kind().equals("minecraft:coal_ore")).findFirst().orElseThrow();
        for (long prerequisite : coal.prerequisites())
            assertTrue(priorities.require(prerequisite).compareTo(priorities.require(coal.id())) <= 0);
        var resident = state.humanPopulation().residents().values().stream().filter(value -> value.settlementId().equals(home))
                .findFirst().orElseThrow();
        var available = deposit.available(Set.of());
        var selected = ExtractionWorkTargets.spatialSelection(demandedState, deposit, resident.id(), available,
                AreaWorkSelection.SpatialPolicy.LOCAL_CONTINUATION, Optional.empty()).orElseThrow();
        var best = available.stream().filter(cell -> ExtractionWorkSafety.available(demandedState, deposit, cell))
                .map(cell -> priorities.require(cell.id())).min(io.farfrontier.palemirror.frontier.v3.model.supply.SupplyPriority::compareTo).orElseThrow();
        assertEquals(0, priorities.require(selected.id()).compareTo(best));
        var codec = new FrontierWorldStateCodec(); var recovered = codec.decode(codec.encode(demandedState));
        assertEquals(selected, ExtractionWorkTargets.spatialSelection(recovered, recovered.extractionSites().deposits().get(deposit.site().id()),
                resident.id(), available, AreaWorkSelection.SpatialPolicy.LOCAL_CONTINUATION, Optional.empty()).orElseThrow());
        assertEquals(demandedState.inventory(), recovered.inventory());
    }
    private FrontierWorldState initial() {
        return FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:fuel-specialization"), 41,
                FrontierRulesets.fuelledQuarryGraybox()));
    }
    @Test void finiteMixedFacesAndDifferentFieldsUseOnePinnedRecoverableDeclaration() {
        var state = initial(); var profile = state.bootstrap().ruleset();
        var deposits = state.extractionSites().deposits().values();
        assertEquals(6, deposits.size());
        assertEquals(3, deposits.stream().filter(deposit -> deposit.site().layout().cells().stream()
                .anyMatch(cell -> cell.definition().before().kind().equals("minecraft:coal_ore"))).count());
        assertEquals(24, deposits.stream().flatMap(deposit -> deposit.site().layout().cells().stream())
                .filter(cell -> cell.definition().before().kind().equals("minecraft:coal_ore")).count());
        var producers = GrayboxQuarryPlan.producerSettlements(state.bootstrap()).stream().map(Settlement::id).collect(java.util.stream.Collectors.toSet());
        for (var site : FrontierResourceSitePlan.compile(state.bootstrap()).values())
            assertEquals(producers.contains(site.settlementId()) ? 64 : 252, site.layout().cells().size());
        var coal = profile.extraction().definitionFor(new BlockExtraction.Block("minecraft:coal_ore", Map.of())).orElseThrow();
        assertEquals(List.of(new BlockExtraction.Output("minecraft:coal", 1)), coal.coldOutput());
        var codec = new FrontierWorldStateCodec(); assertEquals(state, codec.decode(codec.encode(state)));
    }
    @Test void fullStoneReserveStillAllowsResourceBearingAccessToNeededCoalAndNeverExportsFuelBuffer() {
        var state = initial();
        var deposit = state.extractionSites().deposits().values().stream().filter(value -> value.site().layout().cells().stream()
                .anyMatch(cell -> cell.definition().before().kind().equals("minecraft:coal_ore"))).findFirst().orElseThrow();
        var home = deposit.site().settlementId(); var depot = FrontierWorldState.depotId(home);
        var lot = new SubjectId("lot:test-full-stone"); var staging = new SubjectId("custody:test-full-stone");
        var ledger = state.inventory().fungibleResources().issue(new ResourceLot(lot, home, "minecraft:cobblestone", 256, "fixture:reserve", List.of()),
                new CustodyAccount(staging, new ResourceCustody.Container(depot), Map.of(lot, 256), Map.of()));
        ledger = ledger.transfer(staging, ReferenceContainerCustody.scopeId(depot), Map.of(lot, 256), Map.of());
        state = state.withInventory(state.inventory().withFungibleResources(ledger));
        assertFalse(ProductionStockDemand.read(state, home, "minecraft:cobblestone").requested());
        assertTrue(ProductionStockDemand.read(state, home, "minecraft:coal").requested());
        var needed = ExtractionStockDemand.requiredCells(state, deposit, Optional.empty());
        assertTrue(needed.stream().anyMatch(id -> deposit.site().layout().require(id).definition().before().kind().equals("minecraft:stone")),
                "needed coal retains its stone access prerequisites even when stone reserve is full");
        assertTrue(needed.size() < deposit.site().layout().cells().size());
        assertTrue(ExtractionStockDemand.requested(state, deposit, Optional.empty()));
        assertSame(deposit.site().layout().outputKinds(), deposit.site().layout().outputKinds(),
                "immutable material declarations are not recompiled by each work-admission query");
        var participant = state.companies().goodsTrade().participants().participants().get(home);
        var view = GoodsParticipantView.read(state, participant);
        assertEquals(3, view.stocks().get("minecraft:coal").protectedMinimum());
        assertTrue(GoodsParticipantPolicies.require(participant).decide(view, state.bootstrap().ruleset().goodsTrade()).stream()
                .noneMatch(intent -> intent.itemKind().equals("minecraft:coal") && intent.side() == GoodsTradeOrder.Side.SELL));
        var coalLot = new SubjectId("lot:test-full-coal"); var coalStaging = new SubjectId("custody:test-full-coal");
        var stocked = state.inventory().fungibleResources().issue(new ResourceLot(coalLot, home, "minecraft:coal", 64,
                "fixture:reserve", List.of()), new CustodyAccount(coalStaging, new ResourceCustody.Container(depot),
                Map.of(coalLot, 64), Map.of()));
        stocked = stocked.transfer(coalStaging, ReferenceContainerCustody.scopeId(depot), Map.of(coalLot, 64), Map.of());
        var fulfilled = state.withInventory(state.inventory().withFungibleResources(stocked));
        assertTrue(ExtractionStockDemand.requiredCells(fulfilled, deposit, Optional.empty()).isEmpty());
        assertFalse(ExtractionStockDemand.requested(fulfilled, deposit, Optional.empty()),
                "the cheap admission query agrees with the source-selection closure when both products are stocked");
    }
    @Test void safeFoodAndFuelFavourGrainButCriticalShortageStillAllowsBread() {
        var state = initial(); var home = state.bootstrap().settlements().getFirst().id();
        var participant = state.companies().goodsTrade().participants().participants().get(home);
        var view = GoodsParticipantView.read(state, participant);
        assertEquals(0, view.purchaseLimits().get("minecraft:bread"));
        var permissions = SettlementWorkPolicy.permissions(state, home);
        for (var resident : state.humanPopulation().residents().values())
            if (resident.settlementId().equals(home)) permissions = permissions.withoutResident(resident.id());
        var withoutOperators = state.withStrategicPlans(state.strategicPlans().withWorkPermissions(home, permissions));
        assertFalse(GoodsParticipantView.read(withoutOperators, participant).purchaseLimits().containsKey("minecraft:bread"),
                "a physical station and fuel do not make grain usable without any permitted operator");
        // Policy receives current stock facts; removing real owned food changes its actual urgency.
        var account = FungibleResourceCustodySupport.accountAtContainer(state, FrontierWorldState.depotId(participant.settlementId())).orElseThrow();
        var bread = account.lotQuantities().entrySet().stream().filter(entry -> state.inventory().fungibleResources().lots()
                .get(entry.getKey()).itemKind().equals("minecraft:bread")).collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        var emptyFood = state.withInventory(state.inventory().withFungibleResources(state.inventory().fungibleResources().destroy(account.id(), bread, Map.of())));
        var hungry = GoodsParticipantView.read(emptyFood, participant);
        assertFalse(hungry.purchaseLimits().containsKey("minecraft:bread"));
        assertTrue(GoodsParticipantPolicies.require(participant).decide(hungry, state.bootstrap().ruleset().goodsTrade()).stream()
                .anyMatch(intent -> intent.itemKind().equals("minecraft:bread") && intent.side() == GoodsTradeOrder.Side.BUY));
    }
}
