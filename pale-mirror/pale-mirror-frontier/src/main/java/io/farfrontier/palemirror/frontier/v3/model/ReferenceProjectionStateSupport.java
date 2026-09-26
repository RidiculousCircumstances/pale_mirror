package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalReplicaCustodyPayloads.ReferenceProjectionPrepared;

import java.util.LinkedHashMap;
import java.util.Map;

/** One atomic input-custody and before-write transition for a reference container. */
public final class ReferenceProjectionStateSupport {
    private ReferenceProjectionStateSupport() { }

    public static FrontierWorldState prepare(FrontierWorldState state, ReferenceProjectionPrepared request, long revision) {
        SubjectId containerId = request.containerId();
        if (!ReferenceContainerCustody.isReferenceContainer(state, containerId)
                || ReferenceContainerCustody.hasLiveCustody(state, containerId)) {
            throw new IllegalArgumentException("reference projection requires a known container without live custody");
        }
        ContainerSurface surface = state.inventory().surfaces().get(containerId);
        ContainerRecord container = state.inventory().containers().get(containerId);
        if (surface == null || container == null || surface.status() == ContainerSurfaceStatus.CONFLICT) {
            throw new IllegalArgumentException("reference projection has no available canonical surface");
        }
        PhysicalReplicaRecord prior = state.replicaCustody().replicas().get(containerId);
        if (prior == null) {
            if (request.expectedReplicaRevision() != 0L || !request.priorFingerprint().isEmpty() || !request.priorProvenance().isEmpty()
                    || surface.status() != ContainerSurfaceStatus.UNMATERIALIZED) {
                throw new IllegalArgumentException("initial projection requires a fresh unmaterialized surface");
            }
        } else if (prior.state() != PhysicalReplicaState.OBSERVED_CURRENT
                || prior.replicaRevision() != request.expectedReplicaRevision()
                || !prior.fingerprint().equals(request.priorFingerprint()) || !prior.provenance().equals(request.priorProvenance())
                || revision <= prior.emittedCanonicalRevision()) {
            throw new IllegalArgumentException("reference catch-up requires matching released replica evidence");
        }

        ExactInventory inventory = state.inventory();
        Map<SubjectId, ProductionJob> jobs = new LinkedHashMap<>(state.productionJobs());
        for (ProductionJob job : state.productionJobs().values()) {
            if (!(job.inputHold() instanceof ProductionInputHold.Cold held)
                    || !(held.item().custody() instanceof InventoryCustody.ContainerSlot slot)
                    || !slot.containerId().equals(containerId)) continue;
            // The job and its input move together; no duplicate inventory/held representation
            // or reset of route, worker, labor, claim or engine schedule is published.
            if (inventory.items().containsKey(held.itemId()) || inventory.itemAt(containerId, slot.slot()).isPresent()) {
                throw new IllegalArgumentException("production projection input duplicates an occupied exact slot");
            }
            inventory = inventory.store(held.item());
            jobs.put(job.id(), job.withInputHold(new ProductionInputHold.Materialized(held.itemId())));
        }
        FencedRecoveryState recovery = state.fencedRecovery();
        if (prior == null) {
            inventory = inventory.withSurfaceStatus(containerId, ContainerSurfaceStatus.PREPARED);
            recovery = FencedRecoveryContainerSupport.transition(recovery, container, ContainerSurfaceStatus.PREPARED);
        }
        FrontierWorldState transferred = state.withChanges(FrontierWorldStateUpdate.begin().inventory(inventory).productionJobs(jobs));
        String fingerprint = ReferenceContainerCustody.canonicalFingerprint(transferred, containerId);
        String provenance = ReferenceContainerCustody.provenance(containerId);
        PhysicalReplicaCustodyState custody = prior == null
                ? state.replicaCustody().declare(PhysicalReplicaRecord.expected(containerId,
                        ReferenceContainerCustody.semanticKind(state, containerId), revision, fingerprint, provenance))
                : state.replicaCustody().emit(containerId, prior.emittedCanonicalRevision(), prior.replicaRevision(), revision, fingerprint, provenance);
        PhysicalReplicaRecord expected = custody.replicas().get(containerId);
        custody = custody.prepareProjection(new PhysicalCustodyLease(ReferenceContainerCustody.scopeId(containerId), containerId,
                ReferenceContainerCustody.PROVIDER_ID, request.authorityEpoch(), revision, expected.replicaRevision(),
                PhysicalCustodyLeaseStatus.PREPARING, null));
        return transferred.withChanges(FrontierWorldStateUpdate.begin().replicaCustody(custody).fencedRecovery(recovery));
    }
}
