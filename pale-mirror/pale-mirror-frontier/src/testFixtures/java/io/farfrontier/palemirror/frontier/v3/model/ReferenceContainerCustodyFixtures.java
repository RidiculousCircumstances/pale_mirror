package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

/** Explicit pure fixture precondition for a currently observed, naturally loaded reference chest. */
public final class ReferenceContainerCustodyFixtures {
    private ReferenceContainerCustodyFixtures() { }

    public static FrontierWorldState observedAndHeld(FrontierWorldState state, SubjectId containerId) {
        long revision = 1L;
        PhysicalReplicaRecord expected = PhysicalReplicaRecord.expected(containerId, ReferenceContainerCustody.semanticKind(state, containerId), revision,
                ReferenceContainerCustody.canonicalFingerprint(state, containerId), ReferenceContainerCustody.provenance(containerId));
        PhysicalReplicaCustodyState custody = state.replicaCustody().declare(expected)
                .observe(containerId, revision, 1L, expected.fingerprint(), expected.provenance(), revision)
                .acquire(new PhysicalCustodyLease(ReferenceContainerCustody.scopeId(containerId), containerId,
                        ReferenceContainerCustody.PROVIDER_ID, 1L, revision, 2L, PhysicalCustodyLeaseStatus.ACQUIRED, null));
        return state.withChanges(FrontierWorldStateUpdate.begin().replicaCustody(custody));
    }
}
