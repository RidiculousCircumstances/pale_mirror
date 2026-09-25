package io.farfrontier.palemirror.internal.frontier.v3;

import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FrontierV3EntityWriteEpochsTest {
    @Test void newerWritesInvalidateOnlyTheirChunkAndOverflowNeverWrapsIntoAValidStamp() {
        var epochs = new FrontierV3EntityWriteEpochs.Epochs<Object>(2);
        var level = new Object(); var otherLevel = new Object();
        var first = new ChunkPos(0, 0); var second = new ChunkPos(1, 0); var third = new ChunkPos(2, 0);
        assertEquals(0, epochs.stamp(level, first).orElseThrow());
        assertEquals(0, epochs.globalStamp(level).orElseThrow());
        epochs.began(level, first);
        assertEquals(1, epochs.stamp(level, first).orElseThrow());
        assertEquals(1, epochs.globalStamp(level).orElseThrow());
        assertEquals(0, epochs.stamp(level, second).orElseThrow());
        assertEquals(0, epochs.stamp(otherLevel, first).orElseThrow());
        epochs.began(level, first); epochs.began(level, second);
        assertEquals(2, epochs.stamp(level, first).orElseThrow());
        assertEquals(3, epochs.globalStamp(level).orElseThrow());
        epochs.began(level, third);
        assertTrue(epochs.stamp(level, first).isEmpty());
        assertTrue(epochs.stamp(level, third).isEmpty());
        assertTrue(epochs.globalStamp(level).isEmpty());
        assertEquals(0, epochs.stamp(otherLevel, first).orElseThrow());
    }
}
