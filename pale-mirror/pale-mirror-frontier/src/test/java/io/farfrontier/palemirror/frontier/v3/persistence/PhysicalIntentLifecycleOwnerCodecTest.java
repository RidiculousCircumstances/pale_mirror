package io.farfrontier.palemirror.frontier.v3.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.farfrontier.palemirror.frontier.v3.api.FixedPosition;
import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.CheckpointImage;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentLifecycleOwner;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalPostcondition;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.Revision;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PhysicalIntentLifecycleOwnerCodecTest {
    @Test
    void ownerIsRetainedAcrossWalPayloadAndSnapshotState() throws Exception {
        PhysicalIntent intent = productionIntent();
        ByteArrayOutputStream payloadBytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(payloadBytes)) { PhysicalIntentPayloadCodec.write(output, intent); }
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(payloadBytes.toByteArray()))) {
            assertEquals(intent, PhysicalIntentPayloadCodec.read(input));
        }

        ByteArrayOutputStream snapshotBytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(snapshotBytes)) { PhysicalIntentStateCodec.write(output, Map.of(intent.id(), intent)); }
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(snapshotBytes.toByteArray()))) {
            assertEquals(Map.of(intent.id(), intent), PhysicalIntentStateCodec.read(input, true));
        }
    }

    @Test
    void rejectsMissingUnknownStaleAndKindMismatchedOwnerIdentity() {
        assertThrows(NullPointerException.class, () -> new PhysicalIntent(new PhysicalIntentId("intent:missing-owner"),
                PhysicalIntentKind.PRODUCTION_TRANSFORMATION, PhysicalIntentStatus.PREPARED, new SubjectId("job:one"),
                List.of(new SubjectId("job:one"), new SubjectId("item:input"), new SubjectId("item:output")), origin(), 0,
                PhysicalPostcondition.PRODUCTION_TRANSFORMED_OBSERVED, java.util.Optional.empty(), java.util.Optional.empty(), null));
        assertMessage(() -> PhysicalIntentLifecycleOwner.fromWire(2, PhysicalIntentLifecycleOwner.PRODUCTION_WORK.stableId()), "codec version");
        assertMessage(() -> PhysicalIntentLifecycleOwner.fromWire(PhysicalIntentLifecycleOwner.CODEC_VERSION, "frontier:unknown-owner"), "unknown");
        assertThrows(IllegalArgumentException.class, () -> new PhysicalIntent(new PhysicalIntentId("intent:mismatched-owner"),
                PhysicalIntentKind.PRODUCTION_TRANSFORMATION, PhysicalIntentStatus.PREPARED, new SubjectId("job:one"),
                List.of(new SubjectId("job:one"), new SubjectId("item:input"), new SubjectId("item:output")), origin(), 0,
                PhysicalPostcondition.PRODUCTION_TRANSFORMED_OBSERVED, PhysicalIntentLifecycleOwner.ROUTE_OPERATION));
    }

    @Test
    void oldPersistenceEnvelopeFailsBeforeRecoveryCanReplayAnOwnerlessIntent() {
        byte[] snapshot = FrontierPersistenceCodec.encodeSnapshot(new SnapshotRecord(new CheckpointImage(new WorldId("frontier:owner-boundary"),
                Revision.ZERO, SimInstant.ZERO, new byte[0], List.of(), List.of()), 0L));
        snapshot[4] = 64; // The immediately preceding envelope version had no lifecycle-owner bytes.
        assertMessage(() -> FrontierPersistenceCodec.decodeSnapshot(snapshot), "lifecycle-owner codec");
    }

    private static PhysicalIntent productionIntent() {
        return new PhysicalIntent(new PhysicalIntentId("intent:owner-round-trip"), PhysicalIntentKind.PRODUCTION_TRANSFORMATION,
                PhysicalIntentStatus.PREPARED, new SubjectId("job:one"),
                List.of(new SubjectId("job:one"), new SubjectId("item:input"), new SubjectId("item:output")), origin(), 0,
                PhysicalPostcondition.PRODUCTION_TRANSFORMED_OBSERVED, PhysicalIntentLifecycleOwner.PRODUCTION_WORK);
    }

    private static FixedPosition origin() { return new FixedPosition(FixedScalar.ZERO, FixedScalar.ZERO, FixedScalar.ZERO); }

    private static void assertMessage(Runnable action, String fragment) {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, action::run);
        assertTrue(error.getMessage().contains(fragment));
    }
}
