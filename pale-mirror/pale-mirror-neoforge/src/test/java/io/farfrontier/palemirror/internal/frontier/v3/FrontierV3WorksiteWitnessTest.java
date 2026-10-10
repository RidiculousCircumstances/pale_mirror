package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.extraction.BlockExtraction;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class FrontierV3WorksiteWitnessTest {
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
