package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngines;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalReplicaCustodyPayloads.*;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class PhysicalReplicaCustodyStateTest {
    private static final SubjectId OBJECT_A = new SubjectId("container:replica-a");
    private static final SubjectId OBJECT_B = new SubjectId("container:replica-b");
    private static final SubjectId SCOPE_A = new SubjectId("scope:replica-a");
    private static final SubjectId SCOPE_B = new SubjectId("scope:replica-b");
    private static final SubjectId PROVIDER = new SubjectId("provider:physical-a");

    @Test
    void evidenceAndCurrentCustodyRemainSeparateEpochFencedAndLocallyIsolated() {
        PhysicalReplicaCustodyState state = PhysicalReplicaCustodyState.empty().declare(replica(OBJECT_A));
        state = state.observe(OBJECT_A, 10L, 1L, "sha256:a", "owned:genesis", 10L);
        state = state.acquire(lease(SCOPE_A, OBJECT_A, 1L, 10L, 2L));
        state = state.checkpoint(SCOPE_A, 1L, 10L, 2L).release(SCOPE_A, 1L, 10L, 2L);

        assertEquals(PhysicalReplicaState.OBSERVED_CURRENT, state.replicas().get(OBJECT_A).state());
        assertEquals(PhysicalCustodyLeaseStatus.RELEASED, state.custodyByScope().get(SCOPE_A).status());
        PhysicalReplicaCustodyState released = state;
        assertThrows(IllegalArgumentException.class, () -> released.acquire(lease(SCOPE_A, OBJECT_A, 1L, 10L, 2L)), "epochs are never reused after release");

        PhysicalReplicaCustodyState isolated = state.declare(replica(OBJECT_B)).observe(OBJECT_B, 10L, 1L, "sha256:a", "owned:genesis", 10L)
                .acquire(lease(SCOPE_B, OBJECT_B, 1L, 10L, 2L));
        assertEquals(PhysicalCustodyLeaseStatus.ACQUIRED, isolated.custodyByScope().get(SCOPE_B).status());
        assertEquals(PhysicalCustodyLeaseStatus.RELEASED, isolated.custodyByScope().get(SCOPE_A).status(), "one terminal scope does not block another");
        assertThrows(IllegalArgumentException.class, () -> isolated.acquire(lease(new SubjectId("scope:overlap"), OBJECT_B, 2L, 10L, 2L)));
    }

    @Test
    void changedEvidenceAndUnresolvedCustodyFailClosedWithoutChangingOtherRecords() {
        PhysicalReplicaCustodyState state = PhysicalReplicaCustodyState.empty().declare(replica(OBJECT_A)).declare(replica(OBJECT_B));
        state = state.observe(OBJECT_A, 10L, 1L, "changed", "owned:genesis", 10L);
        assertEquals(PhysicalReplicaState.CONFLICT, state.replicas().get(OBJECT_A).state());
        PhysicalReplicaCustodyState conflicted = state;
        assertThrows(IllegalArgumentException.class, () -> conflicted.acquire(lease(SCOPE_A, OBJECT_A, 1L, 10L, 2L)));

        PhysicalReplicaCustodyState other = state.observe(OBJECT_B, 10L, 1L, "sha256:a", "owned:genesis", 10L)
                .acquire(lease(SCOPE_B, OBJECT_B, 1L, 10L, 2L)).unresolved(SCOPE_B, 1L, 10L, 2L, PhysicalCustodyUnresolvedReason.RESTART_AMBIGUITY);
        assertThrows(IllegalArgumentException.class, () -> other.release(SCOPE_B, 1L, 10L, 2L));
        assertEquals(PhysicalReplicaState.CONFLICT, other.replicas().get(OBJECT_A).state());
        assertEquals(PhysicalCustodyLeaseStatus.UNRESOLVED, other.custodyByScope().get(SCOPE_B).status());
    }

    @Test
    void sameObjectRetainsTwoExactlyFencedEmissionCyclesAndConflictEvidence() {
        PhysicalReplicaCustodyState first = PhysicalReplicaCustodyState.empty().declare(replica(OBJECT_A))
                .observe(OBJECT_A, 10L, 1L, "sha256:a", "owned:genesis", 10L)
                .acquire(lease(SCOPE_A, OBJECT_A, 1L, 10L, 2L)).release(SCOPE_A, 1L, 10L, 2L);
        PhysicalReplicaCustodyState second = first.emit(OBJECT_A, 10L, 2L, 11L, "sha256:b", "owned:cycle-two")
                .observe(OBJECT_A, 11L, 3L, "sha256:b", "owned:cycle-two", 11L)
                .acquire(lease(SCOPE_B, OBJECT_A, 2L, 11L, 4L)).release(SCOPE_B, 2L, 11L, 4L);
        assertEquals(11L, second.replicas().get(OBJECT_A).emittedCanonicalRevision());
        assertEquals(4L, second.replicas().get(OBJECT_A).replicaRevision());
        assertThrows(IllegalArgumentException.class, () -> second.emit(OBJECT_A, 10L, 4L, 12L, "sha256:c", "owned:cycle-three"));

        PhysicalReplicaCustodyState conflicted = second.emit(OBJECT_A, 11L, 4L, 12L, "sha256:c", "owned:cycle-three")
                .observe(OBJECT_A, 12L, 5L, "sha256:foreign", "foreign:player", 12L);
        PhysicalReplicaRecord record = conflicted.replicas().get(OBJECT_A);
        assertEquals(PhysicalReplicaState.CONFLICT, record.state());
        assertEquals("sha256:foreign", record.observedFingerprint().orElseThrow());
        assertEquals("foreign:player", record.observedProvenance().orElseThrow());
        assertEquals(PhysicalReplicaConflictReason.FINGERPRINT_AND_PROVENANCE_MISMATCH, record.conflictReason().orElseThrow());
        assertThrows(IllegalArgumentException.class, () -> conflicted.acquire(lease(new SubjectId("scope:conflict"), OBJECT_A, 3L, 12L, 6L)));

        FrontierWorldState baseline = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:replica-conflict"), 91L));
        FrontierWorldState restored = new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(
                baseline.withChanges(FrontierWorldStateUpdate.begin().replicaCustody(conflicted))));
        PhysicalReplicaCustodyProjection.Entry entry = FrontierWorldProjectionCompiler.compile(restored, restored.bootstrap().worldId(), Revision.ZERO,
                SimInstant.ZERO, ProjectionQuery.summary()).replicaCustody().entries().getFirst();
        assertEquals("sha256:foreign", entry.observedFingerprint().orElseThrow());
        assertEquals(PhysicalReplicaConflictReason.FINGERPRINT_AND_PROVENANCE_MISMATCH, entry.conflictReason().orElseThrow());
    }

    @Test
    void forgedInitialVersionAndRepeatedUnresolvedOrTerminalTransitionsFailClosed() {
        PhysicalReplicaRecord forgedInitial = new PhysicalReplicaRecord(OBJECT_A, "container.depot", 10L, 10L, 2L,
                "sha256:a", "owned:genesis", PhysicalReplicaState.EXPECTED, Optional.empty(), Optional.empty(), Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> PhysicalReplicaCustodyState.empty().declare(forgedInitial));
        PhysicalReplicaCustodyState held = PhysicalReplicaCustodyState.empty().declare(replica(OBJECT_A))
                .observe(OBJECT_A, 10L, 1L, "sha256:a", "owned:genesis", 10L).acquire(lease(SCOPE_A, OBJECT_A, 1L, 10L, 2L))
                .unresolved(SCOPE_A, 1L, 10L, 2L, PhysicalCustodyUnresolvedReason.PROVIDER_LOST);
        assertThrows(IllegalArgumentException.class, () -> held.unresolved(SCOPE_A, 1L, 10L, 2L, PhysicalCustodyUnresolvedReason.PROVIDER_LOST));
        assertThrows(IllegalArgumentException.class, () -> held.release(SCOPE_A, 1L, 10L, 2L));
        assertThrows(IllegalArgumentException.class, () -> held.checkpoint(SCOPE_A, 1L, 10L, 2L));
    }

    @Test
    void exactCanonicalAndReplicaFencesRejectForgedFutureValuesWhileOtherObjectsRemainUsable() {
        PhysicalReplicaCustodyState ready = PhysicalReplicaCustodyState.empty().declare(replica(OBJECT_A)).declare(replica(OBJECT_B))
                .observe(OBJECT_A, 10L, 1L, "sha256:a", "owned:genesis", 10L);
        assertThrows(IllegalArgumentException.class, () -> ready.observe(OBJECT_A, 10L, 2L, "sha256:a", "owned:genesis", 10L));
        assertThrows(IllegalArgumentException.class, () -> ready.acquire(lease(SCOPE_A, OBJECT_A, 1L, 11L, 2L)));
        assertThrows(IllegalArgumentException.class, () -> ready.acquire(lease(SCOPE_A, OBJECT_A, 1L, 10L, 3L)));

        PhysicalReplicaCustodyState held = ready.acquire(lease(SCOPE_A, OBJECT_A, 1L, 10L, 2L));
        assertThrows(IllegalArgumentException.class, () -> held.checkpoint(SCOPE_A, 1L, 11L, 2L));
        assertThrows(IllegalArgumentException.class, () -> held.release(SCOPE_A, 1L, 10L, 3L));
        assertThrows(IllegalArgumentException.class, () -> held.unresolved(SCOPE_A, 1L, 11L, 2L, PhysicalCustodyUnresolvedReason.PROVIDER_LOST));

        PhysicalReplicaCustodyState unrelated = held.observe(OBJECT_B, 10L, 1L, "sha256:a", "owned:genesis", 10L)
                .acquire(lease(SCOPE_B, OBJECT_B, 1L, 10L, 2L));
        assertEquals(PhysicalCustodyLeaseStatus.ACQUIRED, unrelated.custodyByScope().get(SCOPE_B).status());
        assertEquals(PhysicalCustodyLeaseStatus.ACQUIRED, unrelated.custodyByScope().get(SCOPE_A).status());
    }

    @Test
    void projectionSelectsLiveThenHighestEpochCustodyDeterministically() {
        PhysicalReplicaCustodyState state = PhysicalReplicaCustodyState.empty().declare(replica(OBJECT_A))
                .observe(OBJECT_A, 10L, 1L, "sha256:a", "owned:genesis", 10L)
                .acquire(lease(SCOPE_A, OBJECT_A, 1L, 10L, 2L))
                .release(SCOPE_A, 1L, 10L, 2L)
                .acquire(lease(SCOPE_B, OBJECT_A, 2L, 10L, 2L));
        FrontierWorldState baseline = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:replica-projection"), 91L));
        FrontierWorldProjection projection = FrontierWorldProjectionCompiler.compile(
                baseline.withChanges(FrontierWorldStateUpdate.begin().replicaCustody(state)), baseline.bootstrap().worldId(), Revision.ZERO,
                SimInstant.ZERO, ProjectionQuery.summary());
        PhysicalReplicaCustodyProjection.Entry entry = projection.replicaCustody().entries().getFirst();
        assertEquals(SCOPE_B, entry.scopeId().orElseThrow());
        assertEquals(2L, entry.authorityEpoch().orElseThrow());
        assertEquals(PhysicalCustodyLeaseStatus.ACQUIRED, entry.custodyStatus().orElseThrow());
        assertEquals(2L, entry.replicaRevision());
    }

    @Test
    void worldCodecRetainsTheCompleteRegistryDeterministicallyAndRejectsPreviousFreshBytes() {
        FrontierWorldState baseline = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:replica-custody"), 91L));
        PhysicalReplicaCustodyState custody = PhysicalReplicaCustodyState.empty().declare(replica(OBJECT_A))
                .observe(OBJECT_A, 10L, 1L, "sha256:a", "owned:genesis", 10L).acquire(lease(SCOPE_A, OBJECT_A, 1L, 10L, 2L));
        FrontierWorldState changed = baseline.withChanges(FrontierWorldStateUpdate.begin().replicaCustody(custody));
        FrontierWorldStateCodec codec = new FrontierWorldStateCodec();
        byte[] encoded = codec.encode(changed);

        assertArrayEquals(encoded, codec.encode(codec.decode(encoded)));
        assertEquals(changed.replicaCustody(), codec.decode(encoded).replicaCustody());
        encoded[4] = (byte) 130;
        assertThrows(IllegalArgumentException.class, () -> codec.decode(encoded));
        assertThrows(IllegalArgumentException.class, () -> new PhysicalReplicaCustodyState(Map.of(), Map.of(SCOPE_A, lease(SCOPE_A, OBJECT_A, 1L, 10L, 2L))));
    }

    @Test
    void stateCodecRejectsDuplicateReplicaKeysAndCustodyReferencesToUnknownObjects() {
        FrontierWorldState baseline = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:replica-custody-corrupt"), 91L));
        FrontierWorldStateCodec codec = new FrontierWorldStateCodec();
        PhysicalReplicaCustodyState twoReplicas = PhysicalReplicaCustodyState.empty().declare(replica(OBJECT_A)).declare(replica(OBJECT_B));
        byte[] duplicate = codec.encode(baseline.withChanges(FrontierWorldStateUpdate.begin().replicaCustody(twoReplicas)));
        replaceOccurrence(duplicate, OBJECT_B.value(), OBJECT_A.value(), 1);
        assertThrows(IllegalArgumentException.class, () -> codec.decode(duplicate));

        PhysicalReplicaCustodyState leased = PhysicalReplicaCustodyState.empty().declare(replica(OBJECT_A))
                .observe(OBJECT_A, 10L, 1L, "sha256:a", "owned:genesis", 10L).acquire(lease(SCOPE_A, OBJECT_A, 1L, 10L, 2L));
        byte[] invalidReference = codec.encode(baseline.withChanges(FrontierWorldStateUpdate.begin().replicaCustody(leased)));
        replaceOccurrence(invalidReference, OBJECT_A.value(), "container:replica-z", 2);
        assertThrows(IllegalArgumentException.class, () -> codec.decode(invalidReference));
    }

    @Test
    void registeredCommandsAndReducersRequireExactVersionsAndProduceNoContainerAuthority() {
        var engine = FrontierEngines.create(FrontierWorldRuntimeDefinition.configuration(new WorldId("frontier:replica-custody-command"), 91L));
        submit(engine, new ReplicaDeclared(replica(OBJECT_A)));
        submit(engine, new ReplicaObserved(OBJECT_A, 10L, 1L, "sha256:a", "owned:genesis", 10L));
        submit(engine, new CustodyAcquired(lease(SCOPE_A, OBJECT_A, 1L, 10L, 2L)));
        submit(engine, new CustodyCheckpointed(SCOPE_A, 1L, 10L, 2L));
        submit(engine, new CustodyReleased(SCOPE_A, 1L, 10L, 2L));

        FrontierWorldState restored = new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
        assertEquals(PhysicalCustodyLeaseStatus.RELEASED, restored.replicaCustody().custodyByScope().get(SCOPE_A).status());
        assertTrue(restored.inventory().surfaces().values().stream().allMatch(surface -> surface.status() == ContainerSurfaceStatus.UNMATERIALIZED),
                "the kernel does not initialize or reinterpret legacy container surfaces");
        assertInstanceOf(CommandResult.Rejected.class, submit(engine, new CustodyReleased(SCOPE_A, 1L, 10L, 2L)));
    }

    private static PhysicalReplicaRecord replica(SubjectId object) { return PhysicalReplicaRecord.expected(object, "container.depot", 10L, "sha256:a", "owned:genesis"); }
    private static PhysicalCustodyLease lease(SubjectId scope, SubjectId object, long epoch, long canonicalRevision, long replicaRevision) {
        return new PhysicalCustodyLease(scope, object, PROVIDER, epoch, canonicalRevision, replicaRevision, PhysicalCustodyLeaseStatus.ACQUIRED, null);
    }
    private static CommandResult submit(FrontierEngine<FrontierWorldProjection> engine, FrontierPayload payload) {
        var canonical = engine.checkpoint(); CommandId id = new CommandId("command:replica-custody-" + canonical.revision().value());
        return engine.submit(new FrontierCommand(FrontierCommand.SCHEMA_VERSION, id, canonical.worldId(), canonical.revision(), canonical.instant(),
                FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR, CauseChain.root(id), payload));
    }
    private static void replaceOccurrence(byte[] bytes, String from, String to, int occurrence) {
        byte[] source = from.getBytes(StandardCharsets.UTF_8), replacement = to.getBytes(StandardCharsets.UTF_8);
        assertEquals(source.length, replacement.length);
        int found = 0;
        for (int index = 0; index <= bytes.length - source.length; index++) {
            boolean matches = true;
            for (int offset = 0; offset < source.length; offset++) if (bytes[index + offset] != source[offset]) { matches = false; break; }
            if (!matches) continue;
            if (++found == occurrence) { System.arraycopy(replacement, 0, bytes, index, replacement.length); return; }
        }
        throw new AssertionError("missing serialized occurrence " + occurrence + " of " + from);
    }
}
