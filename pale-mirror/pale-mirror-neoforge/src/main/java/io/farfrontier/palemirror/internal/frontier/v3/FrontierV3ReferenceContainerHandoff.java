package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
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
            // A witnessed destroyed incarnation is historical evidence, not a
            // provider that must reappear before exposing its former chunk.
            if (surface.status() == ContainerSurfaceStatus.RETIRED) continue;
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
            var physical = FrontierV3PhysicalContainer.inspect(level, state, surface.containerId()).orElse(null);
            if (FrontierV3ContainerEffectFence.pending(level, state, surface.containerId())) {
                // A pending stock transaction does not make an already declared
                // native chest invisible. Keep its execution fence, while allowing
                // this chunk and adjacent renderer sections to reach the player.
                boolean visible = physical != null && physical.declared()
                        && physical.provenance().equals(ReferenceContainerCustody.provenance(surface.containerId()));
                checks.add(new FrontierV3HotHandoff.Check(surface.containerId(),
                        visible ? PRESENTABLE_PENDING : WAITING, "container_effect_recovery"));
                continue;
            }
            if (physical != null) {
                var observed = FrontierV3ReferenceContainerCustodyExecutor.observedContainer(state, surface.containerId(), physical);
                if (observed.fingerprint().equals(ReferenceContainerCustody.canonicalFingerprint(state, surface.containerId()))
                        && observed.provenance().equals(ReferenceContainerCustody.provenance(surface.containerId()))) {
                    status = READY; reason = "current_container";
                } else if (surface.status() == ContainerSurfaceStatus.ACTIVE && physical.declared()
                        && physical.provenance().equals(ReferenceContainerCustody.provenance(surface.containerId()))) {
                    // Stock reconciliation fences inventory operations, not the existing
                    // terrain or the renderer sections in neighbouring chunks. This does
                    // not acquire custody, adopt the observed contents or permit a write.
                    status = PRESENTABLE_PENDING; reason = "container_stock_reconciliation";
                }
            }
            checks.add(new FrontierV3HotHandoff.Check(surface.containerId(), status, reason));
        }
        return List.copyOf(checks);
    }
}
