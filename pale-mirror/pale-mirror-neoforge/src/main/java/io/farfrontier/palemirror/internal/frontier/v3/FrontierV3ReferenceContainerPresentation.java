package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

/** Presentation-only selection for the existing custody owner, never another stock writer. */
final class FrontierV3ReferenceContainerPresentation {
    private FrontierV3ReferenceContainerPresentation() { }
    static boolean needsReconciliation(ServerLevel level, FrontierWorldState state, ContainerSurface surface) {
        if (!ReferenceContainerCustody.isReferenceContainer(state, surface.containerId())) return false;
        var position = surface.position();
        var block = new BlockPos(position.x(), position.y(), position.z());
        if (!FrontierV3PhysicalDemand.presentationRequested(level, block)) return false;
        var replica = state.replicaCustody().replicas().get(surface.containerId());
        var lease = state.replicaCustody().custodyByScope().get(ReferenceContainerCustody.scopeId(surface.containerId()));
        if (replica == null || lease != null && (lease.status() == PhysicalCustodyLeaseStatus.PREPARING
                || lease.status() == PhysicalCustodyLeaseStatus.UNRESOLVED)) return true;
        if (replica.state() == PhysicalReplicaState.CONFLICT) return false;
        if (!(level.getBlockEntity(block) instanceof ChestBlockEntity chest)) return true;
        var actual = FrontierV3ReferenceContainerCustodyExecutor.observed(state, surface.containerId(), chest);
        String canonical = ReferenceContainerCustody.canonicalFingerprint(state, surface.containerId());
        return !canonical.equals(actual.fingerprint()) || !canonical.equals(replica.fingerprint())
                || !ReferenceContainerCustody.provenance(surface.containerId()).equals(actual.provenance());
    }
}
