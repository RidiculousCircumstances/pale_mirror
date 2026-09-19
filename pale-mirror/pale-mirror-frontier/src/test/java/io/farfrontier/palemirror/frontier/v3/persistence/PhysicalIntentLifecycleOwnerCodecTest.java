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
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentRoleBinding;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentSubjectRole;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalPostcondition;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.Revision;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalIntentRecoveryDiagnosticProducer;
import io.farfrontier.palemirror.frontier.v3.model.DiagnosticCategory;
import io.farfrontier.palemirror.frontier.v3.model.DiagnosticDisposition;
import io.farfrontier.palemirror.frontier.v3.model.DiagnosticOwner;
import io.farfrontier.palemirror.frontier.v3.model.DiagnosticOwnerKind;
import io.farfrontier.palemirror.frontier.v3.model.DiagnosticReason;
import io.farfrontier.palemirror.frontier.v3.model.DiagnosticSubject;
import io.farfrontier.palemirror.frontier.v3.model.DiagnosticSubjectKind;
import io.farfrontier.palemirror.frontier.v3.model.DiagnosticTuple;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.List;
import java.util.Map;
import java.util.Arrays;
import java.nio.ByteBuffer;
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
    void recoveryUnknownCannotExistWithoutAnExactTupleAndItsWalAndSnapshotBytesFailClosedWhenForged() throws Exception {
        PhysicalIntent prepared = productionIntent();
        assertThrows(IllegalArgumentException.class, () -> prepared.withStatus(PhysicalIntentStatus.UNKNOWN_AFTER_RESTART, java.util.Optional.empty()));
        PhysicalIntent unknown = prepared.withRecoveryUnknown(PhysicalIntentRecoveryDiagnosticProducer.PRODUCTION_WORK.stamp(prepared));
        assertEquals(unknown, roundTripPayload(unknown));
        ByteArrayOutputStream snapshot = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(snapshot)) { PhysicalIntentStateCodec.write(output, Map.of(unknown.id(), unknown)); }
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(snapshot.toByteArray()))) {
            assertEquals(Map.of(unknown.id(), unknown), PhysicalIntentStateCodec.read(input, true));
        }
        DiagnosticTuple forged = new DiagnosticTuple(DiagnosticReason.PHYSICAL_CUSTODY_UNRESOLVED, DiagnosticCategory.RECOVERY_UNKNOWN,
                new DiagnosticOwner(DiagnosticOwnerKind.PHYSICAL_INTENT, new SubjectId("intent:foreign")),
                new DiagnosticSubject(DiagnosticSubjectKind.PHYSICAL_EFFECT, new SubjectId("intent:foreign")), DiagnosticDisposition.INSPECT);
        assertThrows(IllegalArgumentException.class, () -> new PhysicalIntent(prepared.id(), prepared.kind(), PhysicalIntentStatus.UNKNOWN_AFTER_RESTART,
                prepared.causeSubjectId(), prepared.roles(), prepared.origin(), prepared.radiusBlocks(), prepared.postcondition(), java.util.Optional.empty(),
                prepared.targetSlot(), prepared.semanticTarget(), prepared.lifecycleOwner(), java.util.Optional.of(forged)));
    }

    @Test
    void everyComposedOwnerHasItsOwnStableCodecIdentity() {
        for (PhysicalIntentLifecycleOwner owner : PhysicalIntentLifecycleOwner.values()) {
            assertEquals(owner, PhysicalIntentLifecycleOwner.fromWire(PhysicalIntentLifecycleOwner.CODEC_VERSION, owner.stableId()));
        }
    }

    @Test
    void rejectsMissingUnknownStaleAndKindMismatchedOwnerIdentity() {
        assertTrue(Arrays.stream(PhysicalIntent.class.getConstructors()).allMatch(constructor ->
                Arrays.asList(constructor.getParameterTypes()).contains(PhysicalIntentLifecycleOwner.class)),
                "every public physical-intent construction path must require an explicit lifecycle owner");
        assertThrows(NullPointerException.class, () -> new PhysicalIntent(new PhysicalIntentId("intent:missing-owner"),
                PhysicalIntentKind.PRODUCTION_TRANSFORMATION, PhysicalIntentStatus.PREPARED, new SubjectId("job:one"),
                io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentRoleBinding.production(new SubjectId("job:one"), new SubjectId("item:input"), new SubjectId("item:output")), origin(), 0,
                PhysicalPostcondition.PRODUCTION_TRANSFORMED_OBSERVED, java.util.Optional.empty(), java.util.Optional.empty(), null));
        assertMessage(() -> PhysicalIntentLifecycleOwner.fromWire(1, PhysicalIntentLifecycleOwner.PRODUCTION_WORK.stableId()), "codec version");
        assertMessage(() -> PhysicalIntentLifecycleOwner.fromWire(PhysicalIntentLifecycleOwner.CODEC_VERSION + 1,
                PhysicalIntentLifecycleOwner.PRODUCTION_WORK.stableId()), "codec version");
        assertMessage(() -> PhysicalIntentLifecycleOwner.fromWire(PhysicalIntentLifecycleOwner.CODEC_VERSION, "frontier:unknown-owner"), "unknown");
        assertThrows(IllegalArgumentException.class, () -> new PhysicalIntent(new PhysicalIntentId("intent:mismatched-owner"),
                PhysicalIntentKind.PRODUCTION_TRANSFORMATION, PhysicalIntentStatus.PREPARED, new SubjectId("job:one"),
                io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentRoleBinding.production(new SubjectId("job:one"), new SubjectId("item:input"), new SubjectId("item:output")), origin(), 0,
                PhysicalPostcondition.PRODUCTION_TRANSFORMED_OBSERVED, PhysicalIntentLifecycleOwner.ROUTE_OPERATION));
    }

    @Test
    void typedRolesRejectMissingUnknownAndSwappedBindingsBeforeReplay() {
        assertMessage(() -> PhysicalIntentRoleBinding.decode(io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentRoleSchema.PRODUCTION,
                Map.of(PhysicalIntentSubjectRole.PRODUCTION_JOB, new SubjectId("job:one"), PhysicalIntentSubjectRole.INPUT_ITEM, new SubjectId("item:input"))), "does not match");
        assertMessage(() -> PhysicalIntentSubjectRole.fromWire(255), "unknown");
        assertMessage(() -> io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentRoleSchema.fromWire(255), "unknown");
        PhysicalIntent swapped = new PhysicalIntent(new PhysicalIntentId("intent:swapped-production"), PhysicalIntentKind.PRODUCTION_TRANSFORMATION,
                PhysicalIntentStatus.PREPARED, new SubjectId("job:one"),
                PhysicalIntentRoleBinding.production(new SubjectId("job:one"), new SubjectId("item:output"), new SubjectId("item:input")), origin(), 0,
                PhysicalPostcondition.PRODUCTION_TRANSFORMED_OBSERVED, PhysicalIntentLifecycleOwner.PRODUCTION_WORK);
        assertEquals(swapped, roundTripPayload(swapped), "codec must retain the producer-stamped names without normalizing a forged role swap");
        assertThrows(IllegalArgumentException.class, () -> new PhysicalIntent(new PhysicalIntentId("intent:cross-schema"), PhysicalIntentKind.ROUTE_CONSTRUCTION,
                PhysicalIntentStatus.PREPARED, new SubjectId("maintenance:one"), PhysicalIntentRoleBinding.routeMaintenance(new SubjectId("route:one"), new SubjectId("maintenance:one"), new SubjectId("cargo:one"), new SubjectId("item:one")),
                origin(), 0, PhysicalPostcondition.ROUTE_CONSTRUCTION_OBSERVED, PhysicalIntentLifecycleOwner.ENGINEERING_WORKSITE));
        assertThrows(IllegalArgumentException.class, () -> new PhysicalIntent(new PhysicalIntentId("intent:wrong-owner-schema"), PhysicalIntentKind.PRODUCTION_TRANSFORMATION,
                PhysicalIntentStatus.PREPARED, new SubjectId("job:one"), PhysicalIntentRoleBinding.production(new SubjectId("job:one"), new SubjectId("item:input"), new SubjectId("item:output")),
                origin(), 0, PhysicalPostcondition.PRODUCTION_TRANSFORMED_OBSERVED, PhysicalIntentLifecycleOwner.ROUTE_OPERATION));
        assertMessage(() -> decodeWithSchemaTag(productionIntent(), 255), "unknown");
    }

    @Test
    void oldPersistenceEnvelopeFailsBeforeRecoveryCanReplayAnUntypedIntent() {
        byte[] snapshot = FrontierPersistenceCodec.encodeSnapshot(new SnapshotRecord(new CheckpointImage(new WorldId("frontier:owner-boundary"),
                Revision.ZERO, SimInstant.ZERO, new byte[0], List.of(), List.of()), 0L));
        snapshot[4] = 66; // The immediately preceding envelope version had typed roles but no closed role-schema bytes.
        assertMessage(() -> FrontierPersistenceCodec.decodeSnapshot(snapshot), "typed-role codec");
    }

    @Test
    void forgedSnapshotLifecycleCompositionFingerprintFailsBeforeRecoveredStateCanDispatch() {
        FrontierWorldStateCodec codec = new FrontierWorldStateCodec();
        byte[] encoded = codec.encode(FrontierWorldRuntimeDefinition.configuration(new WorldId("frontier:composition-fingerprint"), 91L).initialState());
        int durationLength = ByteBuffer.wrap(encoded, 5, Short.BYTES).getShort() & 0xffff;
        int compositionLengthOffset = 5 + Short.BYTES + durationLength;
        int compositionLength = ByteBuffer.wrap(encoded, compositionLengthOffset, Short.BYTES).getShort() & 0xffff;
        int compositionStart = compositionLengthOffset + Short.BYTES;
        assertTrue(compositionLength > 0, "the snapshot must retain one closed lifecycle composition fingerprint");
        encoded[compositionStart] ^= 1;
        assertMessage(() -> codec.decode(encoded), "physical lifecycle capability composition");
    }

    private static PhysicalIntent productionIntent() {
        return new PhysicalIntent(new PhysicalIntentId("intent:owner-round-trip"), PhysicalIntentKind.PRODUCTION_TRANSFORMATION,
                PhysicalIntentStatus.PREPARED, new SubjectId("job:one"),
                io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentRoleBinding.production(new SubjectId("job:one"), new SubjectId("item:input"), new SubjectId("item:output")), origin(), 0,
                PhysicalPostcondition.PRODUCTION_TRANSFORMED_OBSERVED, PhysicalIntentLifecycleOwner.PRODUCTION_WORK);
    }

    private static FixedPosition origin() { return new FixedPosition(FixedScalar.ZERO, FixedScalar.ZERO, FixedScalar.ZERO); }

    private static PhysicalIntent roundTripPayload(PhysicalIntent intent) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(bytes)) { PhysicalIntentPayloadCodec.write(output, intent); }
            try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))) { return PhysicalIntentPayloadCodec.read(input); }
        } catch (java.io.IOException impossible) { throw new AssertionError(impossible); }
    }

    private static void decodeWithSchemaTag(PhysicalIntent intent, int tag) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(bytes)) { PhysicalIntentPayloadCodec.write(output, intent); }
            byte[] forged = bytes.toByteArray();
            int schemaOffset = 2 + intent.id().value().length() + 2 + 2 + intent.causeSubjectId().value().length();
            forged[schemaOffset] = (byte) tag;
            try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(forged))) { PhysicalIntentPayloadCodec.read(input); }
        } catch (java.io.IOException impossible) { throw new AssertionError(impossible); }
    }

    private static void assertMessage(Runnable action, String fragment) {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, action::run);
        assertTrue(error.getMessage().contains(fragment));
    }
}
