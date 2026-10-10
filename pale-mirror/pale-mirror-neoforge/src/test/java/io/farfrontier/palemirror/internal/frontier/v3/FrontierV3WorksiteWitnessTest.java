package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.extraction.BlockExtraction;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class FrontierV3WorksiteWitnessTest {
    @Test void withdrawalAcceptsOnlyUnwrittenOrSettledOwnedProjectionNeverAmbiguousWritesOrEffects() {
        var key = new WorksiteBlock.Key(CellMutationKey.OwnerFamily.EXTRACTIVE_SITE, new SubjectId("extraction:test"), WorksiteBlock.Role.RESOURCE, 1);
        var stone = new BlockExtraction.Block("minecraft:stone", Map.of());
        var cell = new WorksiteBlock(key, new BlockPosition(1, 64, 1), 1, stone);
        assertTrue(FrontierV3WorksiteBlockWitness.permitsProjectionWithdrawal(null, cell));
        for (var phase : FrontierV3WorksiteBlockWitness.Phase.values()) {
            var operation = phase == FrontierV3WorksiteBlockWitness.Phase.EFFECT_BLOCK_APPLIED
                    || phase == FrontierV3WorksiteBlockWitness.Phase.EFFECT_COMMITTED ? Optional.of("effect:1") : Optional.<String>empty();
            var witness = new FrontierV3WorksiteBlockWitness(cell, stone, phase, operation);
            assertEquals(phase == FrontierV3WorksiteBlockWitness.Phase.SETTLED,
                    FrontierV3WorksiteBlockWitness.permitsProjectionWithdrawal(witness, cell), phase.name());
        }
        var future = new FrontierV3WorksiteBlockWitness(new WorksiteBlock(key, cell.position(), 2, stone), stone,
                FrontierV3WorksiteBlockWitness.Phase.SETTLED);
        assertFalse(FrontierV3WorksiteBlockWitness.permitsProjectionWithdrawal(future, cell));
    }
    @Test void appendingRealAdjacentSourcesRefreshesPointIndexWithoutRenumberingOldPhysicalDeclarations() {
        var state = FrontierWorldState.initial(FrontierBootstrapper.create(
                new io.farfrontier.palemirror.frontier.v3.api.WorldId("frontier:dynamic-worksite-index"), 20260918065L,
                FrontierRulesets.installed("frontier-v3-quarry-graybox-r4")));
        var old = state.extractionSites().deposits().values().stream().min(Comparator.comparing(value -> value.site().id())).orElseThrow();
        var initialIndex = FrontierV3WorksiteRegistry.chunks(state);
        var priorKeys = ExtractionWorksiteBlocks.declared(old).stream().collect(java.util.stream.Collectors.toMap(WorksiteBlock::position, WorksiteBlock::key));
        var exhausted = new io.farfrontier.palemirror.frontier.v3.model.extraction.ExtractionDeposit(old.site(), old.cells(), old.geometry(),
                new WorkAreaDevelopment(2, old.cells().keySet()));
        for (long id : exhausted.cells().keySet()) exhausted = exhausted.extracted(id, 1, "fixture:index-history-" + id);
        var cleared = state.withChanges(FrontierWorldStateUpdate.begin().extractionSites(state.extractionSites().replace(exhausted)));
        assertSame(initialIndex, FrontierV3WorksiteRegistry.chunks(cleared), "depletion is not a declaration change");
        var plan = ExtractionAreaPlanning.proposal(cleared, old.site().id()).orElseThrow();
        var next = ExtractionAreaPlanning.apply(cleared, old.site().id(), plan, 1000);
        var newPoint = plan.columns().getFirst().floor().support().offset(0, 2, 0);
        assertTrue(FrontierV3WorksiteRegistry.point(cleared, new net.minecraft.core.BlockPos(newPoint.x(), newPoint.y(), newPoint.z())).isEmpty());
        var nativePoint = FrontierV3WorksiteRegistry.point(next, new net.minecraft.core.BlockPos(newPoint.x(), newPoint.y(), newPoint.z())).orElseThrow();
        assertEquals(WorksiteBlock.Role.RESOURCE, nativePoint.key().role()); assertTrue(nativePoint.key().cell() > 512);
        assertNotSame(initialIndex, FrontierV3WorksiteRegistry.chunks(next));
        assertSame(FrontierV3WorksiteRegistry.chunks(next), FrontierV3WorksiteRegistry.chunks(next));
        var newKeys = ExtractionWorksiteBlocks.declared(next.extractionSites().deposits().get(old.site().id())).stream()
                .collect(java.util.stream.Collectors.toMap(WorksiteBlock::position, WorksiteBlock::key));
        assertTrue(newKeys.entrySet().containsAll(priorKeys.entrySet()));
    }
    @Test void sourceDeclarationIndexIsReusedAcrossTurnsAndDepletionButRefreshesForAnotherManifest() {
        var initial = FrontierWorldState.initial(FrontierBootstrapper.create(
                new io.farfrontier.palemirror.frontier.v3.api.WorldId("frontier:worksite-index"), 20260918065L,
                FrontierRulesets.installed("frontier-v3-quarry-graybox-r2")));
        var owner = new FrontierV3ExtractionWorksiteOwner();
        var declared = owner.declarations(initial);
        for (int turn = 0; turn < 100; turn++) assertSame(declared, owner.declarations(initial));
        var deposit = initial.extractionSites().deposits().values().iterator().next();
        var cell = deposit.available(Set.of()).getFirst();
        var next = initial.withChanges(FrontierWorldStateUpdate.begin().extractionSites(initial.extractionSites()
                .replace(deposit.extracted(cell.id(), 1, "effect:index-depletion"))));
        assertSame(declared, owner.declarations(next));
        var resource = declared.stream().filter(value -> value.key().owner().equals(deposit.site().id())
                && value.key().role() == WorksiteBlock.Role.RESOURCE && value.key().cell() == cell.id()).findFirst().orElseThrow();
        assertEquals("minecraft:air", owner.current(next, resource).block().kind());
        var other = FrontierWorldState.initial(FrontierBootstrapper.create(
                new io.farfrontier.palemirror.frontier.v3.api.WorldId("frontier:other-worksite-index"), 42L,
                FrontierRulesets.installed("frontier-v3-quarry-graybox-r2")));
        assertNotSame(declared, owner.declarations(other));
    }
    @Test void cachedPresentationIsCurrentOnlyAfterExactSettledRevisionAndPhysicalPostimage() {
        var key = new WorksiteBlock.Key(CellMutationKey.OwnerFamily.EXTRACTIVE_SITE, new SubjectId("extraction:test"), WorksiteBlock.Role.RESOURCE, 1);
        var stone = new BlockExtraction.Block("minecraft:stone", Map.of());
        var air = new BlockExtraction.Block("minecraft:air", Map.of());
        var before = new WorksiteBlock(key, new BlockPosition(1, 64, 1), 1, stone);
        var after = new WorksiteBlock(key, before.position(), 2, air);
        var settled = new FrontierV3WorksiteBlockWitness(before, stone, FrontierV3WorksiteBlockWitness.Phase.SETTLED);
        assertTrue(settled.settledCurrent(before, stone));
        assertFalse(settled.settledCurrent(after, stone), "COLD depletion must project even at a non-ticking view border");
        assertFalse(settled.settledCurrent(before, air), "player changes are not already-current presentation");
        assertFalse(new FrontierV3WorksiteBlockWitness(after, stone, FrontierV3WorksiteBlockWitness.Phase.PREPARED)
                .settledCurrent(after, air), "a matching block alone is not settled custody evidence");
    }
    @Test void splitBlockProofRequiresItsExactOperationAndCompleteProducerDeclaration() {
        var key = new WorksiteBlock.Key(CellMutationKey.OwnerFamily.EXTRACTIVE_SITE, new SubjectId("extraction:test"), WorksiteBlock.Role.RESOURCE, 1);
        var block = new BlockExtraction.Block("minecraft:stone", Map.of());
        var cell = new WorksiteBlock(key, new BlockPosition(1, 64, 1), 1, block);
        var receipt = new FrontierV3WorksiteBlockWitness(cell, block, FrontierV3WorksiteBlockWitness.Phase.EFFECT_BLOCK_APPLIED, Optional.of("effect:mine-1"));
        assertEquals(receipt, FrontierV3WorksiteBlockWitness.read(receipt.write()));
        assertThrows(IllegalArgumentException.class, () -> new FrontierV3WorksiteBlockWitness(cell, block, receipt.phase()));
        for (String missing : List.of("family", "owner", "role", "cell", "revision", "before", "effect")) {
            var tag = receipt.write(); tag.remove(missing);
            assertThrows(RuntimeException.class, () -> FrontierV3WorksiteBlockWitness.read(tag), missing);
        }
        assertThrows(IllegalStateException.class, () -> FrontierV3WorksiteWrites.apply(key, () ->
                FrontierV3WorksiteWrites.apply(key, () -> true)));
        assertFalse(FrontierV3WorksiteWrites.owns(key), "a rejected nested writer still releases ephemeral attribution");
        assertTrue(FrontierV3WorksiteWrites.apply(key, () -> FrontierV3WorksiteWrites.owns(key)));
        assertFalse(FrontierV3WorksiteWrites.owns(key));
    }
}
