package io.farfrontier.palemirror.frontier.v3.model;

/** Atomic owner boundary: matching fresh evidence resumes only the same isolated surface epoch. */
public final class ReferenceSurfaceRecovery {
    private ReferenceSurfaceRecovery() { }
    public static FrontierWorldState verify(FrontierWorldState state, ReferenceSurfaceVerified observed) {
        var id = observed.containerId();
        var surface = state.inventory().surfaces().get(id);
        var container = state.inventory().containers().get(id);
        var replica = state.replicaCustody().replicas().get(id);
        if (!ReferenceContainerCustody.isReferenceContainer(state, id) || surface == null || container == null
                || surface.status() != ContainerSurfaceStatus.CONFLICT || replica == null
                || replica.state() == PhysicalReplicaState.CONFLICT
                || replica.emittedCanonicalRevision() != observed.expectedCanonicalRevision()
                || replica.replicaRevision() != observed.expectedReplicaRevision()
                || !replica.fingerprint().equals(observed.fingerprint())
                || !replica.provenance().equals(observed.provenance())
                || !ReferenceContainerCustody.provenance(id).equals(observed.provenance())
                || ContainerPhysicalAuthorityComposition.pending(state, id))
            throw new IllegalArgumentException("reference surface lacks matching current stock/provenance or has a pending effect");
        var lease = state.replicaCustody().custodyByScope().get(ReferenceContainerCustody.scopeId(id));
        if (lease != null && (lease.status() == PhysicalCustodyLeaseStatus.PREPARING
                || lease.status() == PhysicalCustodyLeaseStatus.UNRESOLVED))
            throw new IllegalArgumentException("surface verification cannot bypass unfinished custody recovery");
        // Released custody permits COLD successors. Verify the retained physical predecessor,
        // then leave its normal fenced projection protocol to publish today's canonical stock.
        if ((lease == null || lease.live())
                && !ReferenceContainerCustody.canonicalFingerprint(state, id).equals(observed.fingerprint()))
            throw new IllegalArgumentException("live surface inspection disagrees with its canonical stock");
        var bindingId = FencedRecoveryContainerSupport.bindingId(container);
        var binding = state.fencedRecovery().current().get(bindingId);
        if (binding == null || binding.asset() != FencedRecoveryAsset.CONTAINER
                || !binding.ownerId().equals(container.ownerId()) || binding.phase() != FencedRecoveryPhase.AMBIGUOUS
                || binding.authorityEpoch() != observed.expectedSurfaceEpoch()
                || !binding.reason().equals("container-surface-conflict"))
            throw new IllegalArgumentException("surface verification has a stale or foreign recovery epoch");
        var inventory = state.inventory();
        var surfaces = new java.util.HashMap<>(inventory.surfaces());
        surfaces.put(id, new ContainerSurface(id, surface.location(), ContainerSurfaceStatus.ACTIVE));
        var verified = new ExactInventory(inventory.containers(), inventory.items(), inventory.cargo(),
                inventory.playerItems(), inventory.worldCarrierItems(), inventory.conflicts(), surfaces,
                inventory.economics(), inventory.fungibleResources());
        return state.withChanges(FrontierWorldStateUpdate.begin()
                .inventory(verified)
                .fencedRecovery(state.fencedRecovery().inspectedRunning(bindingId, binding.authorityEpoch())));
    }
}
