package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.CheckpointImage;
import io.farfrontier.palemirror.frontier.v3.api.Revision;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RecoveryImageTest {
    @Test
    void checkpointAndStoreReceiptsHaveTypedBoundaries() {
        WorldId world = new WorldId("frontier:recovery");
        CheckpointImage checkpoint = new CheckpointImage(world, Revision.ZERO, SimInstant.ZERO, new byte[] {1}, List.of(), List.of());

        RecoveryImage image = new RecoveryImage(world, Optional.of(new SnapshotRecord(checkpoint, 0L)), List.of());

        assertEquals(world, image.worldId());
        assertEquals(new Revision(2L), new AppendReceipt(
                new io.farfrontier.palemirror.frontier.v3.api.TransactionId("transaction:two"), new Revision(2L),
                Durability.DURABLE_BEFORE_EFFECT, 3L).revision());
        assertThrows(IllegalArgumentException.class, () -> new SnapshotReceipt(Revision.ZERO, -1L));
    }

    @Test
    void foreignCheckpointFailsRecoveryBeforeAnyReplay() {
        CheckpointImage checkpoint = new CheckpointImage(new WorldId("frontier:other"), Revision.ZERO, SimInstant.ZERO,
                new byte[] {1}, List.of(), List.of());
        assertThrows(IllegalArgumentException.class, () -> new RecoveryImage(
                new WorldId("frontier:expected"), Optional.of(new SnapshotRecord(checkpoint, 0L)), List.of()));
    }
}
