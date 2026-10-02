package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** Read-only physical inspection, followed by one exact durable domain verification receipt. */
final class FrontierV3ReferenceSurfaceRecovery {
    private FrontierV3ReferenceSurfaceRecovery() { }
    static boolean inspect(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                           FrontierWorldState state, ContainerSurface surface) {
        var id = surface.containerId();
        var replica = state.replicaCustody().replicas().get(id);
        var container = state.inventory().containers().get(id);
        if (surface.status() != ContainerSurfaceStatus.CONFLICT || replica == null || container == null
                || replica.state() == PhysicalReplicaState.CONFLICT || FrontierV3ContainerEffectFence.pending(level, state, id)) return false;
        var position = new BlockPos(surface.position().x(), surface.position().y(), surface.position().z());
        if (!level.hasChunkAt(position) || !level.shouldTickBlocksAt(position)
                || FrontierV3ContainerSurfaceExecutor.supportReadiness(level, FrontierV3GrayboxLedger.get(level), position,
                    FrontierContainerSocketPlan.support(state, surface).orElse(null))
                    != FrontierV3ContainerSurfaceExecutor.SocketReadiness.READY) return false;
        var chest = FrontierV3ContainerSurfaceExecutor.activeChest(level, position, id);
        if (chest == null) return false;
        var binding = state.fencedRecovery().current().get(FencedRecoveryContainerSupport.bindingId(container));
        if (binding == null || binding.phase() != FencedRecoveryPhase.AMBIGUOUS) return false;
        var lease = state.replicaCustody().custodyByScope().get(ReferenceContainerCustody.scopeId(id));
        var actual = lease != null && !lease.live()
                ? FrontierV3ReferenceContainerCustodyExecutor.observedRetained(state, id, chest)
                : FrontierV3ReferenceContainerCustodyExecutor.observed(state, id, chest);
        var receipt = new ReferenceSurfaceVerified(id, replica.emittedCanonicalRevision(), replica.replicaRevision(),
                binding.authorityEpoch(), actual.fingerprint(), actual.provenance());
        try { ReferenceSurfaceRecovery.verify(state, receipt); }
        catch (IllegalArgumentException changed) { return false; }
        var result = FrontierV3CommandSubmission.submit(runtime, "reference-surface-verified", id.value(), receipt);
        FrontierV3DiagnosticTrace.record(level.getServer(), "reference-surface:" + id.value(),
                "reference-surface-verified", id, result);
        return true;
    }
}
