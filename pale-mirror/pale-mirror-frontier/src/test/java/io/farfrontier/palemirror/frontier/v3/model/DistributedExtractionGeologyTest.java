package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.extraction.*;
import io.farfrontier.palemirror.frontier.v3.model.geology.*;
import io.farfrontier.palemirror.frontier.v3.model.geometry.*;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DistributedExtractionGeologyTest {
    @Test void secondaryCoalDepositCanOpenFromExistingAccessWithoutArtificialStoneDemand() {
        var state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:secondary-coal-access"), 20260918065L,
                FrontierRulesets.marketQuarryGraybox()));
        var secondary = state.extractionSites().deposits().values().stream().filter(deposit -> !deposit.site().mineralDeposits().isEmpty()
                && !deposit.site().layout().outputKinds().contains("minecraft:coal")).toList();
        assertEquals(3, secondary.size());
        for (var deposit : secondary) {
            assertFalse(ProductionStockDemand.read(state, deposit.site().settlementId(), "minecraft:cobblestone").requested());
            assertTrue(ExtractionStockDemand.requested(state, deposit, Optional.empty()));
            var extension = ExtractionAreaPlanning.proposal(state, deposit.site().id()).orElseThrow();
            var extended = ExtractionAreaPlanning.preview(state, deposit.site().id(), extension);
            assertTrue(extended.extractionSites().deposits().get(deposit.site().id()).available(Set.of()).stream()
                    .anyMatch(cell -> cell.definition().coldOutput().stream().anyMatch(output -> output.itemKind().equals("minecraft:coal"))));
        }
    }
    @Test void compactIndexIsThreeDimensionalMaterialNeutralAndRejectsAmbiguity() {
        var lower = new MineralDeposit(new SubjectId("deposit:copper"), 1, List.of(new MineralDeposit.Section(
                new BlockVolume(new BlockPosition(-17, 4, -1), new BlockPosition(17, 6, 1)),
                new BlockExtraction.Block("minecraft:copper_ore", Map.of()))));
        var upper = new MineralDeposit(new SubjectId("deposit:coal"), 2, List.of(new MineralDeposit.Section(
                new BlockVolume(new BlockPosition(-17, 8, -1), new BlockPosition(17, 9, 1)),
                new BlockExtraction.Block("minecraft:coal_ore", Map.of()))));
        var geology = new MineralGeology(Map.of(lower.id(), lower, upper.id(), upper));
        assertEquals(8, geology.indexedChunks());
        assertEquals(lower.id(), geology.at(new BlockPosition(-17, 6, 0)).orElseThrow().depositId());
        assertEquals(upper.id(), geology.at(new BlockPosition(17, 8, 0)).orElseThrow().depositId());
        assertTrue(geology.at(new BlockPosition(0, 7, 0)).isEmpty());
        assertTrue(geology.at(new BlockPosition(18, 8, 0)).isEmpty());
        var overlap = new MineralDeposit(new SubjectId("deposit:overlap"), 1, lower.sections());
        assertThrows(IllegalArgumentException.class, () -> new MineralGeology(Map.of(lower.id(), lower, overlap.id(), overlap)));
        var home = new SubjectId("settlement:survey");
        var knowledge = new GeologicalKnowledge(Map.of(home, Map.of(upper.id(), 2L))); knowledge.validate(geology);
        assertTrue(knowledge.knows(home, upper)); assertFalse(knowledge.knows(home, lower));
        assertThrows(IllegalArgumentException.class, () -> new GeologicalKnowledge(Map.of(home, Map.of(upper.id(), 1L))).validate(geology));
    }

    @Test void grayboxHasPluralKnownFiniteDepositsAndRecoveryDoesNotRecreateMinedOrEditedBlocks() {
        var state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:distributed-geology"), 20260918065L,
                FrontierRulesets.marketQuarryGraybox()));
        var sites = state.extractionSites();
        assertEquals(12, sites.deposits().size());
        var counts = sites.deposits().values().stream().collect(java.util.stream.Collectors.groupingBy(
                deposit -> deposit.site().settlementId(), java.util.stream.Collectors.counting()));
        assertTrue(counts.values().stream().allMatch(count -> count == 2));
        assertEquals(6, sites.geology().deposits().size());
        assertEquals(2, sites.geology().deposits().values().stream().flatMap(deposit -> deposit.sections().stream())
                .map(section -> section.volume().blocks()).distinct().count(), "different finite sizes, not duplicate stock bags");
        var deposit = sites.deposits().values().stream().filter(value -> !value.site().mineralDeposits().isEmpty()
                && value.available(Set.of()).stream().anyMatch(cell -> cell.definition().before().kind().equals("minecraft:coal_ore")))
                .findFirst().orElseThrow();
        var coal = deposit.available(Set.of()).stream().filter(cell -> cell.definition().before().kind().equals("minecraft:coal_ore"))
                .findFirst().orElseThrow();
        var declared = sites.geology().at(coal.source()).orElseThrow();
        assertTrue(deposit.site().mineralDeposits().contains(declared.depositId()));
        assertTrue(sites.knowledge().knows(deposit.site().settlementId(), sites.geology().deposits().get(declared.depositId())));
        var changed = deposit.extracted(coal.id(), 1, "fixture:physical-extraction-receipt");
        state = state.withChanges(FrontierWorldStateUpdate.begin().extractionSites(sites.replace(changed)));
        assertSame(sites.geology(), state.extractionSites().geology());
        var codec = new FrontierWorldStateCodec(); var recovered = codec.decode(codec.encode(state));
        assertEquals(state, recovered);
        assertEquals("minecraft:air", new ExtractionWorkingSurfaces(recovered, recovered.extractionSites().deposits()
                .get(deposit.site().id())).at(coal.source()).orElseThrow().kind());
        assertEquals("minecraft:coal_ore", recovered.extractionSites().geology().at(coal.source()).orElseThrow().section().material().kind(),
                "initial distribution is not current depletion history");
        var unknown = sites.invalidateGeology(declared.section().volume().max());
        var fenced = recovered.withChanges(FrontierWorldStateUpdate.begin().extractionSites(unknown));
        var target = declared.section().volume().max();
        assertTrue(new ExtractionWorkingSurfaces(fenced, fenced.extractionSites().deposits().get(deposit.site().id())).at(target).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> new ExtractionSiteState(sites.deposits(), Map.of(), 1,
                sites.geologicalExclusions(), sites.geology(), GeologicalKnowledge.empty()));
    }
}
