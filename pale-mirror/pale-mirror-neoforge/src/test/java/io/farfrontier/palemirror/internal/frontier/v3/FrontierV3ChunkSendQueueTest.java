package io.farfrontier.palemirror.internal.frontier.v3;

import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class FrontierV3ChunkSendQueueTest {
    @Test void boundedDiscoveryPassesHeldNeighboursAndRetainsQuotaAndRetries() {
        var queue = new FrontierV3ChunkSendQueue();
        for (int x = 0; x < 1000; x++) queue.add(ChunkPos.asLong(x, 0));
        var probes = new AtomicInteger();
        var sent = new HashSet<Long>();
        for (int turn = 0; turn < 40; turn++) {
            probes.set(0);
            var batch = queue.collect(new ChunkPos(0, 0), 9, key -> { probes.incrementAndGet(); return key; },
                    key -> ChunkPos.getX(key) >= 990);
            assertTrue(probes.get() <= 64, "holding near chunks must not scan the whole pending set");
            assertTrue(batch.size() <= 9);
            for (var key : batch) assertTrue(sent.add(key), "a chunk may only count in one native batch");
        }
        assertEquals(10, sent.size(), "distant ready chunks cannot starve behind a blocked prefix");
        assertFalse(queue.collect(new ChunkPos(0, 0), 9, Long::valueOf, key -> true).isEmpty(),
                "previously deferred chunks are still retried when preparation finishes");
    }
    @Test void dropMoveAndReaddNeverRetainAnOldReadyVerdict() {
        var queue = new FrontierV3ChunkSendQueue();
        long a = ChunkPos.asLong(1, 0), b = ChunkPos.asLong(20, 0);
        queue.add(a); queue.add(b); queue.add(b);
        queue.remove(a);
        assertEquals(List.of(b), queue.collect(new ChunkPos(20, 0), 1, Long::valueOf, key -> true));
        queue.add(b);
        assertTrue(queue.collect(new ChunkPos(20, 0), 1, Long::valueOf, key -> false).isEmpty());
        queue.remove(b);
        assertTrue(queue.collect(new ChunkPos(20, 0), 9, Long::valueOf, key -> true).isEmpty());
        queue.add(a); queue.add(b);
        assertEquals(List.of(b), queue.collect(new ChunkPos(21, 0), 1, Long::valueOf, key -> true));
        assertTrue(queue.collect(new ChunkPos(0, 0), 0, key -> { fail("zero quota cannot probe"); return key; }, key -> true).isEmpty());
    }
    @Test void unavailableNativeChunkRemainsPendingWithoutFalseBatch() {
        var queue = new FrontierV3ChunkSendQueue(); queue.add(0);
        assertTrue(queue.collect(new ChunkPos(0, 0), 1, key -> null, key -> { fail(); return true; }).isEmpty());
        assertEquals(List.of(0L), queue.collect(new ChunkPos(0, 0), 1, Long::valueOf, key -> true));
    }
}
