package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import java.util.ArrayList;
import java.util.List;
import static io.farfrontier.palemirror.internal.frontier.v3.FrontierV3HotHandoff.Status.*;

/** Resource owner validates actual stock and provenance without transferring or creating resources. */
final class FrontierV3ReferenceContainerHandoff {
    private FrontierV3ReferenceContainerHandoff() { }
    static List<FrontierV3HotHandoff.Check> inspect(ServerLevel level, FrontierWorldState state, ChunkPos chunk) {
        var checks = new ArrayList<FrontierV3HotHandoff.Check>();
        for (var id : FrontierV3HandoffIndex.containers(level, state.inventory().surfaces(), chunk)) {
            var surface = state.inventory().surfaces().get(id);
            if (!ReferenceContainerCustody.isReferenceContainer(state, surface.containerId())) continue;
            var pos = surface.position();
            if (!chunk.equals(new ChunkPos(pos.x() >> 4, pos.z() >> 4))) continue;
            var status = WAITING;
            String reason = "container_projection_or_recovery";
            var replica = state.replicaCustody().replicas().get(surface.containerId());
            if (surface.status() == ContainerSurfaceStatus.CONFLICT
                    || replica != null && replica.state() == PhysicalReplicaState.CONFLICT) {
                checks.add(new FrontierV3HotHandoff.Check(surface.containerId(), CONFLICT, "container_custody_conflict"));
                continue;
            }
            if (FrontierV3ContainerEffectFence.pending(level, state, surface.containerId())) {
                checks.add(new FrontierV3HotHandoff.Check(surface.containerId(), WAITING, "container_effect_recovery"));
                continue;
            }
            var block = new BlockPos(pos.x(), pos.y(), pos.z());
            if (level.hasChunkAt(block) && level.getBlockEntity(block) instanceof ChestBlockEntity chest) {
                var observed = FrontierV3ReferenceContainerCustodyExecutor.observed(state, surface.containerId(), chest);
                if (observed.fingerprint().equals(ReferenceContainerCustody.canonicalFingerprint(state, surface.containerId()))
                        && observed.provenance().equals(ReferenceContainerCustody.provenance(surface.containerId()))) {
                    status = READY; reason = "current_container";
                }
            }
            checks.add(new FrontierV3HotHandoff.Check(surface.containerId(), status, reason));
        }
        return List.copyOf(checks);
    }
}
