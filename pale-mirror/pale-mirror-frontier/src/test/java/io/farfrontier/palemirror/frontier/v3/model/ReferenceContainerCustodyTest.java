package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

class ReferenceContainerCustodyTest {
    @Test
    void onlyTheReferenceDepotAndNestStoreCanGrantOneCurrentExactLease() {
        FrontierWorldState baseline = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:reference-custody"), 91L));
        SubjectId depot = FrontierWorldState.depotId(new SubjectId("settlement:1"));
        SubjectId store = baseline.bootstrap().hive().organs().stream().flatMap(organ -> organ.containerId().stream()).findFirst().orElseThrow();
        assertTrue(ReferenceContainerCustody.isReferenceContainer(baseline, depot));
        assertTrue(ReferenceContainerCustody.isReferenceContainer(baseline, store));
        assertFalse(ReferenceContainerCustody.isReferenceContainer(baseline, FrontierRouteNetwork.MAINTENANCE_CONTAINER));
        assertFalse(ReferenceContainerCustody.hasLiveCustody(baseline, depot), "legacy surface state alone has no physical authority");

        PhysicalReplicaRecord record = PhysicalReplicaRecord.expected(depot, ReferenceContainerCustody.semanticKind(baseline, depot), 7L,
                ReferenceContainerCustody.canonicalFingerprint(baseline, depot), ReferenceContainerCustody.provenance(depot));
        PhysicalReplicaCustodyState custody = PhysicalReplicaCustodyState.empty().declare(record)
                .observe(depot, 7L, 1L, record.fingerprint(), record.provenance(), 7L)
                .acquire(new PhysicalCustodyLease(ReferenceContainerCustody.scopeId(depot), depot, ReferenceContainerCustody.PROVIDER_ID,
                        1L, 7L, 2L, PhysicalCustodyLeaseStatus.ACQUIRED, null));
        FrontierWorldState held = baseline.withChanges(FrontierWorldStateUpdate.begin().replicaCustody(custody));
        assertTrue(ReferenceContainerCustody.hasLiveCustody(held, depot));
        assertFalse(ReferenceContainerCustody.hasLiveCustody(held, store), "one depot lease cannot grant the hive store authority");
    }

    @Test
    void canonicalAndObservedSlotFingerprintsAreDeterministicAndFailClosedOnDrift() {
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:reference-fingerprint"), 91L));
        SubjectId depot = FrontierWorldState.depotId(new SubjectId("settlement:1"));
        String expected = ReferenceContainerCustody.canonicalFingerprint(state, depot);
        ArrayList<ReferenceContainerCustody.ObservedSlot> matching = new ArrayList<>();
        for (int slot = 0; slot < state.inventory().containers().get(depot).slotCount(); slot++) {
            ExactItemStack item = state.inventory().itemAt(depot, slot).orElse(null);
            matching.add(item == null ? ReferenceContainerCustody.ObservedSlot.empty(slot)
                    : new ReferenceContainerCustody.ObservedSlot(slot, item.id().value(), item.itemKind(), item.count()));
        }
        assertEquals(expected, ReferenceContainerCustody.observedFingerprint(state, depot, matching));
        ExactItemStack original = state.inventory().itemAt(depot, 0).orElseThrow();
        matching.set(0, new ReferenceContainerCustody.ObservedSlot(0, original.id().value(), original.itemKind(), original.count() - 1));
        assertNotEquals(expected, ReferenceContainerCustody.observedFingerprint(state, depot, matching));
        assertThrows(IllegalArgumentException.class, () -> ReferenceContainerCustody.observedFingerprint(state, depot, matching.subList(1, matching.size())));
    }

    @Test
    void unresolvedReferenceEpochBlocksItsObjectWithoutAuthorizingPhysicalMutation() {
        FrontierWorldState baseline = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:reference-unresolved"), 91L));
        SubjectId depot = FrontierWorldState.depotId(new SubjectId("settlement:1"));
        PhysicalReplicaRecord record = PhysicalReplicaRecord.expected(depot, ReferenceContainerCustody.semanticKind(baseline, depot), 7L,
                ReferenceContainerCustody.canonicalFingerprint(baseline, depot), ReferenceContainerCustody.provenance(depot));
        PhysicalReplicaCustodyState custody = PhysicalReplicaCustodyState.empty().declare(record)
                .observe(depot, 7L, 1L, record.fingerprint(), record.provenance(), 7L)
                .acquire(new PhysicalCustodyLease(ReferenceContainerCustody.scopeId(depot), depot, ReferenceContainerCustody.PROVIDER_ID,
                        1L, 7L, 2L, PhysicalCustodyLeaseStatus.ACQUIRED, null))
                .unresolved(ReferenceContainerCustody.scopeId(depot), 1L, 7L, 2L, PhysicalCustodyUnresolvedReason.RESTART_AMBIGUITY);
        FrontierWorldState unresolved = baseline.withChanges(FrontierWorldStateUpdate.begin().replicaCustody(custody));
        assertTrue(ReferenceContainerCustody.hasLiveCustody(unresolved, depot), "the retained epoch locally blocks cold reuse");
        assertFalse(ReferenceContainerCustody.hasOperationalCustody(unresolved, depot), "unresolved recovery cannot write the chest");
    }
}
