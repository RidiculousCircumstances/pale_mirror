package io.farfrontier.palemirror.internal.frontier.v3;

import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FrontierV3DepartureReturnReadFenceTest {
    @Test void overlappingReadsKeepTheChunkReservedUntilBothFinish() {
        var reads = new FrontierV3DepartureReturnReadFence.ReadReservations<Object>();
        var level = new Object(); var other = new Object();
        var chunk = new ChunkPos(2, -3); var elsewhere = new ChunkPos(3, -3);
        assertFalse(reads.pending(level, chunk));
        assertFalse(reads.anyPending(level));
        reads.reserve(level, chunk); reads.reserve(level, chunk);
        reads.reserve(other, elsewhere);
        assertTrue(reads.pending(level, chunk)); assertFalse(reads.pending(level, elsewhere));
        assertTrue(reads.anyPending(level));
        assertFalse(reads.pending(other, chunk)); assertTrue(reads.pending(other, elsewhere));
        reads.release(level, chunk);
        assertTrue(reads.pending(level, chunk));
        reads.release(level, chunk);
        assertFalse(reads.pending(level, chunk)); assertTrue(reads.pending(other, elsewhere));
        assertFalse(reads.anyPending(level)); assertTrue(reads.anyPending(other));
        reads.release(other, elsewhere);
        assertFalse(reads.pending(other, elsewhere));
        assertFalse(reads.anyPending(other));
        assertThrows(IllegalStateException.class, () -> reads.release(level, chunk));
    }
}
