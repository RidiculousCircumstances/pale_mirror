package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalCustodyLease;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalCustodyLeaseStatus;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalCustodyUnresolvedReason;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalReplicaCustodyPayloads.*;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalReplicaRecord;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PhysicalReplicaCustodyPayloadCodecTest {
    private static final SubjectId OBJECT = new SubjectId("container:codec-a");
    private static final SubjectId SCOPE = new SubjectId("scope:codec-a");
    private static final SubjectId PROVIDER = new SubjectId("provider:codec-a");

    @Test
    void completeTransitionVocabularyRoundTripsAndMalformedPayloadsFailClosed() {
        PhysicalReplicaRecord replica = PhysicalReplicaRecord.expected(OBJECT, "container.depot", 10L, "sha256:a", "owned:genesis");
        PhysicalCustodyLease lease = new PhysicalCustodyLease(SCOPE, OBJECT, PROVIDER, 1L, 10L, 2L,
                PhysicalCustodyLeaseStatus.ACQUIRED, null);
        List<FrontierPayload> payloads = List.of(new ReplicaDeclared(replica), new ReplicaEmitted(OBJECT, 10L, 2L, 11L,
                "sha256:b", "owned:cycle-two"), new ReplicaObserved(OBJECT, 10L, 1L,
                "sha256:a", "owned:genesis", 10L), new ReplicaConflictObserved(OBJECT, 10L, 2L, "sha256:foreign", "foreign:player"),
                new CustodyAcquired(lease), new CustodyCheckpointed(SCOPE, 1L, 10L, 2L),
                new CustodyUnresolved(SCOPE, 1L, 10L, 2L, PhysicalCustodyUnresolvedReason.RESTART_AMBIGUITY),
                new CustodyReleased(SCOPE, 1L, 10L, 2L));
        var codecs = FrontierWorldRuntimeDefinition.payloadCodecs();
        for (FrontierPayload payload : payloads) {
            byte[] encoded = codecs.encode(payload);
            assertEquals(payload, codecs.decode(payload.type(), encoded));
            assertThrows(IllegalArgumentException.class, () -> codecs.decode(payload.type(), Arrays.copyOf(encoded, encoded.length + 1)));
        }
        byte[] unknownLifecycle = codecs.encode(payloads.getFirst());
        unknownLifecycle[unknownLifecycle.length - 1] = 127;
        assertThrows(IllegalArgumentException.class, () -> codecs.decode(payloads.getFirst().type(), unknownLifecycle));
        assertThrows(IllegalArgumentException.class, () -> codecs.decode("frontier.physical_custody_unknown", new byte[0]));
        assertThrows(IllegalArgumentException.class, () -> new ReplicaObserved(OBJECT, 10L, 1L, "", "owned:genesis", 10L));
        assertThrows(IllegalArgumentException.class, () -> new CustodyCheckpointed(SCOPE, 1L, 10L, 0L));
        assertThrows(IllegalArgumentException.class, () -> new CustodyReleased(SCOPE, 1L, 10L, 0L));
    }
}
