package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.CheckpointImage;
import io.farfrontier.palemirror.frontier.v3.api.CommandId;
import io.farfrontier.palemirror.frontier.v3.api.CommandReceipt;
import io.farfrontier.palemirror.frontier.v3.api.Revision;
import io.farfrontier.palemirror.frontier.v3.api.ScheduleId;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.TransactionId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FrontierPersistenceCodecTest {
    @Test
    void snapshotRoundTripsWithCompleteKernelStateAndChecksum() {
        CheckpointImage image = new CheckpointImage(new WorldId("frontier:persistence"), new Revision(3L), new SimInstant(10L),
                new byte[] {1, 2, 3}, List.of(new ScheduledAction(new ScheduleId("schedule:one"), new SimInstant(12L), 0,
                new SubjectId("settlement:one"), "process.test", 1)), List.of(new CommandReceipt(new CommandId("command:one"),
                new SimInstant(10L), new TransactionId("transaction:three"), new Revision(3L))));
        byte[] encoded = FrontierPersistenceCodec.encodeSnapshot(image);
        CheckpointImage decoded = FrontierPersistenceCodec.decodeSnapshot(encoded);
        assertEquals(image.worldId(), decoded.worldId());
        assertEquals(image.schedules(), decoded.schedules());
        assertEquals(image.receipts(), decoded.receipts());
        assertArrayEquals(image.canonicalState(), decoded.canonicalState());
        encoded[encoded.length - 1] ^= 1;
        assertThrows(IllegalArgumentException.class, () -> FrontierPersistenceCodec.decodeSnapshot(encoded));
    }

    @Test
    void unknownVersionAndTruncatedSnapshotsFailClosed() {
        byte[] encoded = FrontierPersistenceCodec.encodeSnapshot(new CheckpointImage(new WorldId("frontier:empty"), Revision.ZERO,
                SimInstant.ZERO, new byte[0], List.of(), List.of()));
        encoded[4] = 99;
        assertThrows(IllegalArgumentException.class, () -> FrontierPersistenceCodec.decodeSnapshot(encoded));
        assertThrows(IllegalArgumentException.class, () -> FrontierPersistenceCodec.decodeSnapshot(new byte[] {0, 1, 2}));
    }
}
