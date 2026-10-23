package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.model.extraction.*;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class IronDepositContentTest {
    @Test void separateIronDepositsPreserveCoalAndDoNotCreateConsumptionOrProductionDemand() {
        var rules = FrontierRulesets.mineralDepositsGraybox();
        var world = new WorldId("frontier:iron-content");
        var state = FrontierWorldState.initial(FrontierBootstrapper.create(world, 125, rules));
        var old = GrayboxQuarryPlan.initial(FrontierBootstrapper.create(world, 125, FrontierRulesets.dynamicFarmingGraybox()));
        old.deposits().forEach((id, deposit) -> assertEquals(deposit, state.extractionSites().deposits().get(id), "coal stays intact"));
        var ironSites = state.extractionSites().deposits().values().stream()
                .filter(d -> d.site().layout().outputKinds().contains("minecraft:raw_iron")).toList();
        assertEquals(3, ironSites.size());
        assertEquals(old.deposits().size() + ironSites.size(), state.extractionSites().deposits().size(), "no empty extra installations");
        for (var home : state.bootstrap().settlements()) {
            assertEquals(0, SettlementReservePlanning.read(state, home.id(), 0).get("minecraft:raw_iron").quantity());
            assertFalse(ProductionStockDemand.read(state, home.id(), "minecraft:raw_iron", 0).requested());
        }
        for (var deposit : ironSites) {
            assertFalse(ExtractionStockDemand.requested(state, deposit, Optional.empty(), 0));
            assertTrue(ExtractionStockDemand.requiredCells(state, deposit, Optional.empty(), 0).isEmpty());
            assertFalse(deposit.site().layout().outputKinds().contains("minecraft:coal"));
        }
        assertTrue(rules.initialSettlementStocks().stream().noneMatch(s -> s.itemKind().equals("minecraft:raw_iron")));
        var iron = rules.extraction().definitionFor(new BlockExtraction.Block("minecraft:iron_ore", Map.of())).orElseThrow();
        assertEquals(rules.extraction().source().toolRequirement().ability(), iron.toolRequirement().ability());
        assertTrue(iron.toolRequirement().minimumGrade() >= rules.extraction().source().toolRequirement().minimumGrade());
        assertTrue(iron.toolRequirement().wear() > rules.extraction().source().toolRequirement().wear());
        assertEquals(List.of(new BlockExtraction.Output("minecraft:raw_iron", 1)), iron.coldOutput());
        assertEquals(3 * rules.workCatalog().require("minecraft:coal_ore", WorkOperation.EXTRACT).workUnits(),
                2 * rules.workCatalog().require("minecraft:iron_ore", WorkOperation.EXTRACT).workUnits());
        var codec = new FrontierWorldStateCodec();
        assertEquals(state, codec.decode(codec.encode(state)), "new rules and ore geometry survive recovery");
    }

    @Test void ironMayBeAnAccessPrerequisiteWithoutBecomingAnIndependentProductionTarget() {
        var state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:iron-overburden"), 125,
                FrontierRulesets.mineralDepositsGraybox()));
        state = CoalDepositReserveTest.withoutFinishedFood(state);
        var deposit = state.extractionSites().deposits().values().stream()
                .filter(d -> d.site().layout().outputKinds().contains("minecraft:coal")).findFirst().orElseThrow();
        var iron = state.bootstrap().ruleset().extraction().definitionFor(new BlockExtraction.Block("minecraft:iron_ore", Map.of())).orElseThrow();
        var layout = deposit.site().layout();
        var coal = layout.cells().stream().filter(c -> c.definition().before().kind().equals("minecraft:coal_ore")
                && !c.prerequisites().isEmpty()).findFirst().orElseThrow();
        var upper = layout.require(coal.prerequisites().stream().max(Long::compareTo).orElseThrow());
        var cells = new ArrayList<>(layout.cells());
        cells.set(cells.indexOf(upper), new ExtractionLayout.Cell(upper.id(), upper.source(), upper.workstation(), iron, upper.prerequisites()));
        var mixed = new ExtractionLayout(cells, layout.fixedBlocks(), layout.accessSurfaces(), layout.entrance(), layout.container(),
                layout.storagePort(), layout.infrastructureIds(), layout.terrainSupports());
        var changed = ExtractionDeposit.initial(new ExtractionSite(deposit.site().id(), deposit.site().settlementId(), mixed,
                deposit.site().mineralDeposits()), deposit.development().openedCells());
        var sites = state.extractionSites();
        var deposits = new HashMap<>(sites.deposits()); deposits.put(changed.site().id(), changed);
        state = state.withChanges(FrontierWorldStateUpdate.begin().extractionSites(new ExtractionSiteState(deposits,
                sites.work(), sites.nextWorkOrdinal(), sites.geologicalExclusions(), sites.geology(), sites.knowledge())));
        assertFalse(ProductionStockDemand.read(state, changed.site().settlementId(), "minecraft:raw_iron", 0).requested());
        assertTrue(ExtractionStockDemand.requiredCells(state, changed, Optional.empty(), 0).contains(upper.id()));
        assertTrue(ExtractionPriorities.read(state, changed, Optional.empty(), 0).require(upper.id()).missing() > 0);
    }
}
