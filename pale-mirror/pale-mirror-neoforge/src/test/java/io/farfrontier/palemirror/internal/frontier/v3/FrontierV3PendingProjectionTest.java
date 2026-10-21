package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.extraction.BlockExtraction;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class FrontierV3PendingProjectionTest {
    private static WorksiteBlock cell(int x, long revision) {
        return new WorksiteBlock(new WorksiteBlock.Key(CellMutationKey.OwnerFamily.EXTRACTIVE_SITE,
                new SubjectId("extraction:queue-test"), WorksiteBlock.Role.NATURAL_TERRAIN, x + 1),
                new BlockPosition(x, 60, 0), revision, new BlockExtraction.Block("minecraft:stone", Map.of()));
    }
    @Test void settledChunksSpendNoFurtherProbesAndExtensionDoesNotResetUnfinishedPeers() {
        var queue = new FrontierV3PendingProjection();
        var cells = java.util.stream.IntStream.range(0, 64).mapToObj(i -> cell(i, 1)).toList();
        queue.update(Map.of(1L, cells.subList(0, 32), 2L, cells.subList(32, 64)));
        var token = new Object();
        var first = queue.batch(key -> token, key -> key == 1 ? 0 : Long.MAX_VALUE, 16);
        first.forEach(block -> queue.complete(1, block));
        assertEquals(16, queue.pending(1));
        queue.update(Map.of(1L, cells.subList(0, 32), 2L, cells.subList(32, 64), 3L, List.of(cell(100, 1))));
        assertEquals(16, queue.pending(1), "unrelated extension cannot restart completed reads");
        for (int turn = 0; turn < 10; turn++) for (var block : queue.batch(key -> token, key -> key == 3 ? 0 : Long.MAX_VALUE, 16))
            queue.complete(block.position().x() < 32 ? 1 : block.position().x() < 64 ? 2 : 3, block);
        assertTrue(queue.batch(key -> token, key -> Long.MAX_VALUE, 64).isEmpty());
        queue.invalidate(2, cell(40, 1).position());
        assertEquals(List.of(cell(40, 1)), queue.batch(key -> token, key -> Long.MAX_VALUE, 64));
        queue.complete(2, cell(40, 1));
        queue.update(Map.of(1L, cells.subList(0, 32), 2L, List.of(cell(40, 2))));
        queue.complete(2, cell(40, 1));
        assertEquals(1, queue.pending(2), "stale completion cannot clear a new revision");
    }
    @Test void unavailableAndReloadedChunksRetainWorkAndBlockedPreferredChunkCannotStarveOthers() {
        var queue = new FrontierV3PendingProjection();
        queue.update(Map.of(1L, List.of(cell(1, 1)), 2L, List.of(cell(2, 1))));
        var token = new Object();
        assertTrue(queue.batch(key -> null, key -> 0, 1).isEmpty());
        var seen = new HashSet<WorksiteBlock>();
        for (int i = 0; i < 4; i++) seen.addAll(queue.batch(key -> token, key -> key == 1 ? 0 : Long.MAX_VALUE, 1));
        assertEquals(Set.of(cell(1, 1), cell(2, 1)), seen);
        queue.complete(1, cell(1, 1)); queue.complete(2, cell(2, 1));
        assertTrue(queue.batch(key -> token, key -> Long.MAX_VALUE, 64).isEmpty());
        queue.batch(key -> null, key -> Long.MAX_VALUE, 64);
        assertEquals(2, queue.batch(key -> new Object(), key -> Long.MAX_VALUE, 64).size(), "reloaded image needs renewed owner assessment");
    }
    @Test void physicalWitnessChangesWakeOnlyTheirExactPointAndHydrationDoesNotInventCompletion() {
        var ledger = FrontierV3GrayboxLedger.inMemory(); var block = cell(1, 1);
        var witness = new FrontierV3WorksiteBlockWitness(block, block.block(), FrontierV3WorksiteBlockWitness.Phase.PREPARED);
        ledger.worksite(witness);
        assertEquals(Set.of(new BlockPos(1, 60, 0).asLong()), ledger.takeWorksiteChanges());
        ledger.worksite(witness); assertTrue(ledger.takeWorksiteChanges().isEmpty());
        var recovered = FrontierV3GrayboxLedger.load(ledger.save(new net.minecraft.nbt.CompoundTag(), null), null);
        assertEquals(witness, recovered.worksite(new BlockPos(1, 60, 0)));
        var queue = new FrontierV3PendingProjection(); queue.update(Map.of(1L, List.of(block)));
        assertEquals(List.of(block), queue.batch(key -> Boolean.TRUE, key -> 0, 64));
        recovered.worksite(new FrontierV3WorksiteBlockWitness(block, block.block(), FrontierV3WorksiteBlockWitness.Phase.EXTERNAL));
        assertEquals(1, recovered.takeWorksiteChanges().size());
    }
    @Test void currentIndexRetainsUnchangedChunkVersionsAndDropsRemovedSources() {
        var index = new FrontierV3WorksiteCurrentIndex();
        var a = cell(1, 1); var b = cell(33, 1);
        var facts = io.farfrontier.palemirror.frontier.v3.model.geometry.RegionalBlockMap.copyOf(Map.of(a.position(), a, b.position(), b));
        var original = index.update(List.of(facts));
        assertSame(original, index.update(List.of(facts)));
        var revised = index.update(List.of(facts.with(Map.of(b.position(), cell(33, 2)))));
        assertSame(original.get(0L), revised.get(0L));
        assertEquals(List.of(cell(33, 2)), revised.get(net.minecraft.world.level.ChunkPos.asLong(2, 0)));
        assertEquals(original, index.update(List.of(facts)), "branch rollback is an exact view, not monotonic version guessing");
        assertTrue(index.update(List.of()).isEmpty());
        assertThrows(IllegalStateException.class, () -> index.update(List.of(facts, facts)));
    }
    @Test void oneMissingCellDoesNotWaitForThousandsOfSettledCells() {
        var queue = new FrontierV3PendingProjection(); var old = new FrontierV3IndexedProbeCursor<WorksiteBlock>();
        var chunks = new LinkedHashMap<Long, List<WorksiteBlock>>();
        for (long chunk = 0; chunk < 20; chunk++) {
            int base = (int) chunk * 512;
            chunks.put(chunk, java.util.stream.IntStream.range(base, base + 512).mapToObj(i -> cell(i, 1)).toList());
        }
        queue.update(chunks);
        var token = new Object();
        queue.batch(key -> token, key -> Long.MAX_VALUE, 1); // establish loaded generations
        chunks.forEach((key, cells) -> cells.forEach(block -> queue.complete(key, block)));
        var target = chunks.get(19L).getLast(); queue.invalidate(19, target.position());
        int oldTurns = 0; boolean found = false;
        while (!found) {
            oldTurns++;
            for (var block : old.probes(chunks, key -> true, 64)) if (block.equals(target)) found = true;
        }
        assertEquals(List.of(target), queue.batch(key -> token, key -> 0, 64));
        assertEquals(160, oldTurns);
        System.out.println("worksite scheduling same 20x512 declarations: last dirty cell old=" + oldTurns + " turns; pending queue=1 turn");
        var initial = new FrontierV3PendingProjection(); initial.update(chunks);
        int firstReady = 0;
        do {
            firstReady++;
            for (var block : initial.batch(key -> token, key -> 0, 64))
                initial.complete(block.position().x() / 512, block);
        } while (java.util.stream.LongStream.range(0, 20).noneMatch(key -> initial.pending(key) == 0));
        assertTrue(firstReady <= 16, "finish a demanded chunk instead of leaving every chunk partially ready");
        System.out.println("first complete chunk, same 64-write ceiling: pending queue=" + firstReady + " turns; old round robin~160 turns");
    }
    @Test void viewerMovementReprioritizesPendingChunksWithoutRestartingProgress() {
        var queue = new FrontierV3PendingProjection(); var token = new Object();
        queue.update(Map.of(1L, List.of(cell(1, 1), cell(2, 1)), 2L, List.of(cell(33, 1))));
        assertEquals(List.of(cell(33, 1)), queue.batch(key -> token, key -> key == 2 ? 0 : 10, 1));
        queue.batch(key -> token, key -> key == 2 ? 0 : 10, 1); // fair turn, still unfinished
        var closer = queue.batch(key -> token, key -> key == 1 ? 0 : 10, 1);
        assertTrue(closer.getFirst().position().x() < 16, "new viewer position preempts the old focus");
        assertEquals(2, queue.pending(1)); assertEquals(1, queue.pending(2));
    }
}
