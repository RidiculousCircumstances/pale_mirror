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
import io.farfrontier.palemirror.frontier.v3.model.FrontierBootstrapper;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FrontierPersistenceCodecTest {
    @Test
    void preFootprintWorldsAreRejectedBeforeHeaderSelectionOrHydration() {
        var state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:footprint-version"), 91L));
        var codec = new FrontierWorldStateCodec();
        var encoded = codec.encode(state);
        assertEquals(244, Byte.toUnsignedInt(encoded[4]));
        assertArrayEquals(encoded, codec.encode(codec.decode(encoded)));
        for (int legacy = 177; legacy < 244; legacy++) {
            var old = encoded.clone(); old[4] = (byte) legacy;
            var before = old.clone();
            org.junit.jupiter.api.Assertions.assertTrue(assertThrows(IllegalArgumentException.class,
                    () -> FrontierWorldSnapshotHeader.read(old)).getMessage().contains("schema " + legacy));
            org.junit.jupiter.api.Assertions.assertTrue(assertThrows(IllegalArgumentException.class,
                    () -> codec.decode(old)).getMessage().contains("schema " + legacy));
            assertArrayEquals(before, old, "rejection must not rewrite an old world");
        }
    }

    @Test
    void snapshotRoundTripsWithCompleteKernelStateAndChecksum() {
        CheckpointImage image = new CheckpointImage(new WorldId("frontier:persistence"), new Revision(3L), new SimInstant(10L),
                new byte[] {1, 2, 3}, List.of(new ScheduledAction(new ScheduleId("schedule:one"), new SimInstant(12L), 0,
                new SubjectId("settlement:one"), "process.test", 1)), List.of(new CommandReceipt(new CommandId("command:one"),
                new SimInstant(10L), new TransactionId("transaction:three"), new Revision(3L))));
        SnapshotRecord record = new SnapshotRecord(image, 7L);
        byte[] encoded = FrontierPersistenceCodec.encodeSnapshot(record);
        SnapshotRecord decoded = FrontierPersistenceCodec.decodeSnapshot(encoded);
        assertEquals(7L, decoded.coveredWalSequence());
        assertEquals(image.worldId(), decoded.checkpoint().worldId());
        assertEquals(image.schedules(), decoded.checkpoint().schedules());
        assertEquals(image.receipts(), decoded.checkpoint().receipts());
        assertArrayEquals(image.canonicalState(), decoded.checkpoint().canonicalState());
        encoded[encoded.length - 1] ^= 1;
        assertThrows(IllegalArgumentException.class, () -> FrontierPersistenceCodec.decodeSnapshot(encoded));
    }

    @Test
    void preCurrentEnvelopeAndTruncatedSnapshotsFailClosed() {
        byte[] encoded = FrontierPersistenceCodec.encodeSnapshot(new SnapshotRecord(new CheckpointImage(new WorldId("frontier:empty"), Revision.ZERO,
                SimInstant.ZERO, new byte[0], List.of(), List.of()), 0L));
        assertEquals(97, Byte.toUnsignedInt(encoded[4]));
        for (int version : new int[]{74, 75, 76, 77, 78, 79, 80, 81, 82, 83, 84, 85, 86, 87, 88, 89, 91, 92}) {
            byte[] oldEnvelope = encoded.clone(); oldEnvelope[4] = (byte) version;
            assertThrows(IllegalArgumentException.class, () -> FrontierPersistenceCodec.decodeSnapshot(oldEnvelope));
        }
        encoded[4] = 44;
        assertThrows(IllegalArgumentException.class, () -> FrontierPersistenceCodec.decodeSnapshot(encoded));
        assertThrows(IllegalArgumentException.class, () -> FrontierPersistenceCodec.decodeSnapshot(new byte[] {0, 1, 2}));
    }

    @Test
    void preHydrationHeaderConsumesTheCurrentDescriptorInventoryBeforeReadingWorldIdentity() {
        WorldId world = new WorldId("frontier:header-recovery");
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(world, 91L));
        FrontierWorldSnapshotHeader header = FrontierWorldSnapshotHeader.read(new FrontierWorldStateCodec().encode(state));

        assertEquals(world, header.worldId());
        assertEquals(91L, header.seed());
        assertEquals(state.bootstrap().ruleset(), header.ruleset());
    }
}
