package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.extraction.BlockExtraction;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class FrontierV3WorksiteWitnessTest {
    @Test void externalFactsDispatchFromTheirRetainedOwnerAfterGeometryWithdrawalAndRecovery() {
        var ledger = FrontierV3GrayboxLedger.inMemory();
        var block = new BlockExtraction.Block("minecraft:oak_log", Map.of());
        var forest = new WorksiteBlock(new WorksiteBlock.Key(CellMutationKey.OwnerFamily.FOREST_TREE,
                new SubjectId("forest:test"), WorksiteBlock.Role.RESOURCE, 1), new BlockPosition(1, 64, 1), 1, block);
        var mining = new WorksiteBlock(new WorksiteBlock.Key(CellMutationKey.OwnerFamily.EXTRACTIVE_SITE,
                new SubjectId("extraction:test"), WorksiteBlock.Role.RESOURCE, 1), new BlockPosition(2, 64, 1), 1, block);
        var forestWitness = new FrontierV3WorksiteBlockWitness(forest, block, FrontierV3WorksiteBlockWitness.Phase.EXTERNAL);
        ledger.worksite(forestWitness);
        ledger.worksite(new FrontierV3WorksiteBlockWitness(mining, block, FrontierV3WorksiteBlockWitness.Phase.EXTERNAL_PENDING));
        var recovered = FrontierV3GrayboxLedger.load(ledger.save(new net.minecraft.nbt.CompoundTag(), null), null);
        assertEquals(List.of(new net.minecraft.core.BlockPos(1, 64, 1)),
                recovered.externalWorksites(CellMutationKey.OwnerFamily.FOREST_TREE));
        assertEquals(List.of(new net.minecraft.core.BlockPos(2, 64, 1)),
                recovered.externalWorksites(CellMutationKey.OwnerFamily.EXTRACTIVE_SITE));
        // No current point index is needed to retire an acknowledged lost tree source.
        recovered.retireExternalWorksite(forestWitness);
        assertTrue(recovered.externalWorksites(CellMutationKey.OwnerFamily.FOREST_TREE).isEmpty());
        assertEquals(1, recovered.externalWorksites(CellMutationKey.OwnerFamily.EXTRACTIVE_SITE).size());
    }
    @Test void acquisitionRetainsItsPositiveGenerationObservationForUnloadedDepartureAndRecovery() {
        var ledger = FrontierV3GrayboxLedger.inMemory();
        var log = new BlockExtraction.Block("minecraft:oak_log", Map.of());
        var cell = new WorksiteBlock(new WorksiteBlock.Key(CellMutationKey.OwnerFamily.FOREST_TREE,
                new SubjectId("forest:test"), WorksiteBlock.Role.RESOURCE, 1), new BlockPosition(1, 64, 1), 1, log);
        var virtual = new FrontierV3WorksiteBlockWitness(cell, log, FrontierV3WorksiteBlockWitness.Phase.SETTLED);
        assertNull(ledger.worksite(new net.minecraft.core.BlockPos(1, 64, 1)));
        assertFalse(FrontierV3WorksiteEvidence.retainCurrent(ledger, cell, new BlockExtraction.Block("minecraft:air", Map.of()), virtual));
        assertNull(ledger.worksite(new net.minecraft.core.BlockPos(1, 64, 1)), "generation is not a current native observation");
        assertTrue(FrontierV3WorksiteEvidence.retainCurrent(ledger, cell, log, virtual));
        var recovered = FrontierV3GrayboxLedger.load(ledger.save(new net.minecraft.nbt.CompoundTag(), null), null);
        assertTrue(recovered.worksite(new net.minecraft.core.BlockPos(1, 64, 1)).settledCurrent(cell, log),
                "release no longer needs a loaded chunk to recreate its generation receipt");
    }
    @Test void ownerCertifiedReclassificationRetainsPhysicalPreimageAndSurvivesLedgerHydration() {
        var owner = new SubjectId("extraction:test"); var position = new BlockPosition(1, 64, 1);
        var stone = new BlockExtraction.Block("minecraft:stone", Map.of());
        var original = new BlockExtraction.Block("minecraft:dirt", Map.of());
        var terrain = new WorksiteBlock(new WorksiteBlock.Key(CellMutationKey.OwnerFamily.EXTRACTIVE_SITE,
                owner, WorksiteBlock.Role.NATURAL_TERRAIN, 7), position, 1, stone);
        var resource = new WorksiteBlock(new WorksiteBlock.Key(CellMutationKey.OwnerFamily.EXTRACTIVE_SITE,
                owner, WorksiteBlock.Role.RESOURCE, 100), position, 1, new BlockExtraction.Block("minecraft:air", Map.of()));
        var ledger = FrontierV3GrayboxLedger.inMemory();
        var prior = new FrontierV3WorksiteBlockWitness(terrain, original, FrontierV3WorksiteBlockWitness.Phase.SETTLED);
        ledger.worksite(prior);
        var proposed = new FrontierV3WorksiteBlockWitness(resource, original, FrontierV3WorksiteBlockWitness.Phase.SETTLED);
        assertThrows(IllegalStateException.class, () -> ledger.worksite(proposed), "ordinary writes may not rekey provenance");
        var rebound = ledger.reclassify(prior, new WorksiteBlockTransition(terrain, resource));
        assertEquals(resource.key(), rebound.declaration().key());
        assertEquals(stone, rebound.declaration().block(), "reclassification must not claim the successor's unwritten AIR postimage");
        assertEquals(original, rebound.before());
        assertEquals(FrontierV3WorksiteBlockWitness.Phase.SETTLED, rebound.phase());
        var nativePosition = new net.minecraft.core.BlockPos(position.x(), position.y(), position.z());
        var restored = FrontierV3GrayboxLedger.load(ledger.save(new net.minecraft.nbt.CompoundTag(), null), null);
        assertEquals(rebound, restored.worksite(nativePosition));
        assertThrows(IllegalStateException.class, () -> restored.reclassify(prior, new WorksiteBlockTransition(terrain, resource)));
        restored.worksite(new FrontierV3WorksiteBlockWitness(resource, stone, FrontierV3WorksiteBlockWitness.Phase.PREPARED));
        assertEquals(FrontierV3WorksiteBlockWitness.Phase.PREPARED, restored.worksite(nativePosition).phase());
    }
    @Test void unresolvedOrStalePhysicalWitnessCannotCrossReclassification() {
        var owner = new SubjectId("extraction:test"); var stone = new BlockExtraction.Block("minecraft:stone", Map.of());
        var terrain = new WorksiteBlock(new WorksiteBlock.Key(CellMutationKey.OwnerFamily.EXTRACTIVE_SITE,
                owner, WorksiteBlock.Role.NATURAL_TERRAIN, 7), new BlockPosition(1, 64, 1), 1, stone);
        var resource = new WorksiteBlock(new WorksiteBlock.Key(CellMutationKey.OwnerFamily.EXTRACTIVE_SITE,
                owner, WorksiteBlock.Role.RESOURCE, 100), terrain.position(), 1, stone);
        var transition = new WorksiteBlockTransition(terrain, resource);
        for (var phase : FrontierV3WorksiteBlockWitness.Phase.values()) {
            if (phase == FrontierV3WorksiteBlockWitness.Phase.SETTLED) continue;
            var effect = phase == FrontierV3WorksiteBlockWitness.Phase.EFFECT_BLOCK_APPLIED
                    || phase == FrontierV3WorksiteBlockWitness.Phase.EFFECT_COMMITTED ? Optional.of("effect:1") : Optional.<String>empty();
            var prior = new FrontierV3WorksiteBlockWitness(terrain, stone, phase, effect);
            var ledger = FrontierV3GrayboxLedger.inMemory(); ledger.worksite(prior);
            assertThrows(IllegalStateException.class, () -> ledger.reclassify(prior, transition), phase.name());
            assertEquals(prior, ledger.worksite(new net.minecraft.core.BlockPos(1, 64, 1)));
        }
        var ledger = FrontierV3GrayboxLedger.inMemory();
        var prior = new FrontierV3WorksiteBlockWitness(terrain, stone, FrontierV3WorksiteBlockWitness.Phase.SETTLED);
        ledger.worksite(prior);
        var stale = new WorksiteBlock(terrain.key(), terrain.position(), 2, stone);
        assertThrows(IllegalStateException.class, () -> ledger.reclassify(prior, new WorksiteBlockTransition(stale, resource)));
        assertThrows(IllegalArgumentException.class, () -> new WorksiteBlockTransition(terrain,
                new WorksiteBlock(resource.key(), terrain.position().offset(1, 0, 0), 1, stone)));
        assertThrows(IllegalArgumentException.class, () -> new WorksiteBlockTransition(terrain,
                new WorksiteBlock(new WorksiteBlock.Key(resource.key().family(), new SubjectId("extraction:foreign"),
                        resource.key().role(), resource.key().cell()), terrain.position(), 1, stone)));
    }
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
        var plan = ExtractionAreaPlanning.proposal(cleared, old.site().id(), 0L).orElseThrow();
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
        assertEquals(Optional.of(settled), settled.departure(before, stone));
        assertTrue(settled.departure(after, air).isEmpty(), "old observation cannot certify another source revision");
        var changed = settled.departure(before, air).orElseThrow();
        var recovered = FrontierV3WorksiteBlockWitness.read(changed.write());
        assertEquals(FrontierV3WorksiteBlockWitness.Phase.EXTERNAL, recovered.phase());
        assertEquals(air, recovered.before());
        assertFalse(recovered.settledCurrent(before, stone), "unload cannot reuse old authority after a physical change");
        assertFalse(FrontierV3WorksiteBlockWitness.permitsProjectionWithdrawal(recovered, before));
        var pending = new FrontierV3WorksiteBlockWitness(before, stone,
                FrontierV3WorksiteBlockWitness.Phase.EFFECT_BLOCK_APPLIED, Optional.of("effect:pending-departure"));
        assertTrue(pending.departure(before, air).isEmpty(), "departure cannot overwrite an unresolved effect receipt");
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
