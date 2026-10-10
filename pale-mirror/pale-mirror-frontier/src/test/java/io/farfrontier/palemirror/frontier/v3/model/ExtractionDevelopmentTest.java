package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.extraction.*;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Live contract: bounded adjacent opening changes admission, never physical history or resources. */
class ExtractionDevelopmentTest {
    private static FrontierWorldState initial() {
        return FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:quarry-development"),
                20260918065L, FrontierRulesets.installed("frontier-v3-quarry-graybox-r3")));
    }
    private static ExtractionDeposit deposit(FrontierWorldState state) {
        return state.extractionSites().deposits().values().stream().sorted(Comparator.comparing(value -> value.site().id())).findFirst().orElseThrow();
    }
    private static FrontierWorldState clearFirstFront(FrontierWorldState state) {
        var deposit = deposit(state);
        for (long cell : deposit.development().openedCells().stream().sorted().toList())
            deposit = deposit.extracted(cell, 1, "effect:first-front-" + cell);
        return state.withChanges(FrontierWorldStateUpdate.begin().extractionSites(state.extractionSites().replace(deposit)));
    }
    @Test void finiteBodyExistsBeforeOpeningAndRecoveryRetainsExactAdmissionWithoutStockOrRegeneration() {
        var initial = initial(); var original = deposit(initial); var site = original.site().id();
        assertEquals(512, original.cells().size()); assertEquals(8, original.development().openedCells().size());
        assertTrue(original.cells().values().stream().allMatch(cell -> cell.knownBlock().kind().equals("minecraft:stone")));
        assertTrue(ExtractionDevelopment.proposal(initial, site).isEmpty(), "a live front is not exhausted by reservation");
        var before = clearFirstFront(initial); var opening = ExtractionDevelopment.proposal(before, site).orElseThrow();
        assertEquals(8, opening.cells().size());
        var after = ExtractionDevelopment.apply(before, site, opening);
        assertEquals(before.inventory(), after.inventory()); assertEquals(before.actorLocations(), after.actorLocations());
        assertEquals(deposit(before).cells(), deposit(after).cells()); assertEquals(original.site(), deposit(after).site());
        assertEquals(16, deposit(after).development().openedCells().size());
        assertEquals(2, deposit(after).development().revision());
        var codec = new FrontierWorldStateCodec(); assertEquals(after, codec.decode(codec.encode(after)));
        var payloads = FrontierWorldRuntimeDefinition.payloadCodecs(); assertEquals(opening, payloads.decode(opening.type(), payloads.encode(opening)));
        assertThrows(IllegalArgumentException.class, () -> ExtractionDevelopment.apply(after, site, opening));
        assertThrows(IllegalArgumentException.class, () -> ExtractionDevelopment.apply(before, new SubjectId("extraction:foreign"), opening));
        var undeveloped = original.site().layout().cells().stream().filter(cell -> !original.development().openedCells().contains(cell.id())).findFirst().orElseThrow();
        assertThrows(IllegalArgumentException.class, () -> original.extracted(undeveloped.id(), 1, "effect:unadmitted"));
    }
    @Test void externalRemovalOpensRealClearanceButForeignBlocksDoNotYieldStockOrRestoreStone() {
        var state = initial(); var original = deposit(state); var changed = original;
        long blocked = original.development().openedCells().stream().sorted().findFirst().orElseThrow();
        for (long cell : original.development().openedCells()) changed = changed.observe(cell, 1,
                cell == blocked ? new BlockExtraction.Block("minecraft:bedrock", Map.of()) : original.site().layout().require(cell).definition().after());
        var before = state.withChanges(FrontierWorldStateUpdate.begin().extractionSites(state.extractionSites().replace(changed)));
        var opening = ExtractionDevelopment.proposal(before, original.site().id()).orElseThrow();
        assertEquals(7, opening.cells().size()); assertFalse(opening.cells().contains(blocked + 1));
        var after = ExtractionDevelopment.apply(before, original.site().id(), opening);
        assertEquals(before.inventory(), after.inventory()); assertEquals(changed.cells(), deposit(after).cells());
        assertTrue(deposit(after).cells().values().stream().noneMatch(cell -> cell.disposition() == ExtractionDeposit.Disposition.EXTRACTED));
    }
    @Test void noMandateOrNoKnownPathDoesNotOpenOrDestroyAFront() {
        var state = clearFirstFront(initial()); var original = deposit(state); var home = original.site().settlementId();
        var policy = SettlementWorkPolicy.permissions(state, home);
        for (var actor : policy.workers(ResidentWorkKind.EXTRACTION)) policy = policy.withoutResident(actor);
        var withdrawn = state.withStrategicPlans(state.strategicPlans().withWorkPermissions(home, policy));
        assertTrue(ExtractionDevelopment.proposal(withdrawn, original.site().id()).isEmpty());
        var broken = original;
        for (var cell : original.accessibleSources(Set.of())) {
            var support = cell.workstation().support();
            if (!broken.geometry().containsKey(support)) broken = broken.observeGeometry(support, 1, new BlockExtraction.Block("minecraft:air", Map.of()));
        }
        var blocked = state.withChanges(FrontierWorldStateUpdate.begin().extractionSites(state.extractionSites().replace(broken)));
        assertTrue(ExtractionDevelopment.proposal(blocked, original.site().id()).isEmpty());
        assertEquals(original.development(), deposit(blocked).development());
        assertEquals(state.inventory(), blocked.inventory());
    }
    @Test void genericAdjacencySkipsUnavailableCellsAndNeverJumpsToADisconnectedResourceIsland() {
        var cells = List.of(new AdjacentWorkArea.Cell(1, new BlockPosition(0, 0, 0)),
                new AdjacentWorkArea.Cell(2, new BlockPosition(1, 0, 0)),
                new AdjacentWorkArea.Cell(3, new BlockPosition(0, 0, 1)),
                new AdjacentWorkArea.Cell(4, new BlockPosition(20, 0, 0)));
        var opened = new WorkAreaDevelopment(1, Set.of(1L));
        assertEquals(Set.of(3L), AdjacentWorkArea.extend(cells, opened, id -> id != 2, 16));
        assertEquals(Set.of(2L), AdjacentWorkArea.extend(cells, opened, id -> true, 1));
        assertThrows(IllegalArgumentException.class, () -> opened.extend(1, Set.of(1L)));
        assertThrows(IllegalArgumentException.class, () -> opened.extend(2, Set.of(2L)));
    }
    @Test void exhaustedFiniteHistoryNeverCreatesAnotherFrontOrRegeneratesSources() {
        var state = initial(); var original = deposit(state);
        var opened = new WorkAreaDevelopment(2, original.cells().keySet());
        var exhausted = new ExtractionDeposit(original.site(), original.cells(), original.geometry(), opened);
        for (long cell : original.cells().keySet()) exhausted = exhausted.extracted(cell, 1, "effect:finite-" + cell);
        var end = state.withChanges(FrontierWorldStateUpdate.begin().extractionSites(state.extractionSites().replace(exhausted)));
        assertFalse(ExtractionWorkPolicy.remaining(exhausted));
        assertTrue(ExtractionDevelopment.proposal(end, original.site().id()).isEmpty());
        assertEquals(512, exhausted.cells().size());
        assertTrue(exhausted.cells().values().stream().allMatch(cell -> cell.knownBlock().kind().equals("minecraft:air")));
        assertEquals(state.inventory(), end.inventory(), "a history fixture is not a production receipt");
    }
}
