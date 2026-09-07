package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngines;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalReplicaCustodyPayloads.*;
import org.junit.jupiter.api.Test;

import java.util.Map;

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
        state = state.observe(OBJECT_A, 10L, "sha256:a", "owned:genesis", 11L);
        state = state.acquire(lease(SCOPE_A, OBJECT_A, 1L, 11L));
        state = state.checkpoint(SCOPE_A, 1L, 12L, 11L).release(SCOPE_A, 1L, 13L, 11L);

        assertEquals(PhysicalReplicaState.OBSERVED_CURRENT, state.replicas().get(OBJECT_A).state());
        assertEquals(PhysicalCustodyLeaseStatus.RELEASED, state.custodyByScope().get(SCOPE_A).status());
        PhysicalReplicaCustodyState released = state;
        assertThrows(IllegalArgumentException.class, () -> released.acquire(lease(SCOPE_A, OBJECT_A, 1L, 11L)), "epochs are never reused after release");

        PhysicalReplicaCustodyState isolated = state.declare(replica(OBJECT_B)).observe(OBJECT_B, 10L, "sha256:a", "owned:genesis", 11L)
                .acquire(lease(SCOPE_B, OBJECT_B, 1L, 11L));
        assertEquals(PhysicalCustodyLeaseStatus.ACQUIRED, isolated.custodyByScope().get(SCOPE_B).status());
        assertEquals(PhysicalCustodyLeaseStatus.RELEASED, isolated.custodyByScope().get(SCOPE_A).status(), "one terminal scope does not block another");
        assertThrows(IllegalArgumentException.class, () -> isolated.acquire(lease(new SubjectId("scope:overlap"), OBJECT_B, 1L, 11L)));
    }

    @Test
    void changedEvidenceAndUnresolvedCustodyFailClosedWithoutChangingOtherRecords() {
        PhysicalReplicaCustodyState state = PhysicalReplicaCustodyState.empty().declare(replica(OBJECT_A)).declare(replica(OBJECT_B));
        state = state.observe(OBJECT_A, 10L, "changed", "owned:genesis", 11L);
        assertEquals(PhysicalReplicaState.CONFLICT, state.replicas().get(OBJECT_A).state());
        PhysicalReplicaCustodyState conflicted = state;
        assertThrows(IllegalArgumentException.class, () -> conflicted.acquire(lease(SCOPE_A, OBJECT_A, 1L, 11L)));

        PhysicalReplicaCustodyState other = state.observe(OBJECT_B, 10L, "sha256:a", "owned:genesis", 11L)
                .acquire(lease(SCOPE_B, OBJECT_B, 1L, 11L)).unresolved(SCOPE_B, 1L, PhysicalCustodyUnresolvedReason.RESTART_AMBIGUITY);
        assertThrows(IllegalArgumentException.class, () -> other.release(SCOPE_B, 1L, 12L, 11L));
        assertEquals(PhysicalReplicaState.CONFLICT, other.replicas().get(OBJECT_A).state());
        assertEquals(PhysicalCustodyLeaseStatus.UNRESOLVED, other.custodyByScope().get(SCOPE_B).status());
    }

    @Test
    void worldCodecRetainsTheCompleteRegistryDeterministicallyAndRejectsPreviousFreshBytes() {
        FrontierWorldState baseline = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:replica-custody"), 91L));
        PhysicalReplicaCustodyState custody = PhysicalReplicaCustodyState.empty().declare(replica(OBJECT_A))
                .observe(OBJECT_A, 10L, "sha256:a", "owned:genesis", 11L).acquire(lease(SCOPE_A, OBJECT_A, 1L, 11L));
        FrontierWorldState changed = baseline.withChanges(FrontierWorldStateUpdate.begin().replicaCustody(custody));
        FrontierWorldStateCodec codec = new FrontierWorldStateCodec();
        byte[] encoded = codec.encode(changed);

        assertArrayEquals(encoded, codec.encode(codec.decode(encoded)));
        assertEquals(changed.replicaCustody(), codec.decode(encoded).replicaCustody());
        encoded[4] = (byte) 128;
        assertThrows(IllegalArgumentException.class, () -> codec.decode(encoded));
        assertThrows(IllegalArgumentException.class, () -> new PhysicalReplicaCustodyState(Map.of(), Map.of(SCOPE_A, lease(SCOPE_A, OBJECT_A, 1L, 11L))));
    }

    @Test
    void registeredCommandsAndReducersRequireExactVersionsAndProduceNoContainerAuthority() {
        var engine = FrontierEngines.create(FrontierWorldRuntimeDefinition.configuration(new WorldId("frontier:replica-custody-command"), 91L));
        submit(engine, new ReplicaDeclared(replica(OBJECT_A)));
        submit(engine, new ReplicaObserved(OBJECT_A, 10L, "sha256:a", "owned:genesis", 11L));
        submit(engine, new CustodyAcquired(lease(SCOPE_A, OBJECT_A, 1L, 11L)));
        submit(engine, new CustodyCheckpointed(SCOPE_A, 1L, 12L, 11L));
        submit(engine, new CustodyReleased(SCOPE_A, 1L, 13L, 11L));

        FrontierWorldState restored = new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
        assertEquals(PhysicalCustodyLeaseStatus.RELEASED, restored.replicaCustody().custodyByScope().get(SCOPE_A).status());
        assertTrue(restored.inventory().surfaces().values().stream().allMatch(surface -> surface.status() == ContainerSurfaceStatus.UNMATERIALIZED),
                "the kernel does not initialize or reinterpret legacy container surfaces");
        assertInstanceOf(CommandResult.Rejected.class, submit(engine, new CustodyReleased(SCOPE_A, 1L, 14L, 11L)));
    }

    private static PhysicalReplicaRecord replica(SubjectId object) { return PhysicalReplicaRecord.expected(object, "container.depot", 10L, "sha256:a", "owned:genesis"); }
    private static PhysicalCustodyLease lease(SubjectId scope, SubjectId object, long epoch, long replicaRevision) {
        return new PhysicalCustodyLease(scope, object, PROVIDER, epoch, 11L, replicaRevision, PhysicalCustodyLeaseStatus.ACQUIRED, null);
    }
    private static CommandResult submit(FrontierEngine<FrontierWorldProjection> engine, FrontierPayload payload) {
        var canonical = engine.checkpoint(); CommandId id = new CommandId("command:replica-custody-" + canonical.revision().value());
        return engine.submit(new FrontierCommand(FrontierCommand.SCHEMA_VERSION, id, canonical.worldId(), canonical.revision(), canonical.instant(),
                FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR, CauseChain.root(id), payload));
    }
}
