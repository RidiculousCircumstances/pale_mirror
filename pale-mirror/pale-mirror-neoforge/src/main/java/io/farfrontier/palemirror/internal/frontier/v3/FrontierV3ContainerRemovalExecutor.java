package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.CommandResult;
import io.farfrontier.palemirror.frontier.v3.model.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

/** Closes witnessed provider destruction, never guesses disposal from a missing block. */
final class FrontierV3ContainerRemovalExecutor {
    private FrontierV3ContainerRemovalExecutor() { }
    static void tick(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        var journal = FrontierV3ResourceHandoffLedger.get(level);
        for (var witness : journal.removals()) {
            var state = runtime.decodedState().orElseThrow(); var id = witness.container();
            var record = state.inventory().containers().get(id); var surface = state.inventory().surfaces().get(id);
            if (record == null || surface == null || !record.ownerId().equals(witness.owner())
                    || !surface.position().equals(witness.position()))
                throw new IllegalStateException("container removal lost its exact declaration");
            if (surface.status() == ContainerSurfaceStatus.RETIRED) {
                var tombstone = state.fencedRecovery().tombstones().get(FencedRecoveryContainerSupport.bindingId(record));
                if (tombstone == null || tombstone.retiredEpoch() != witness.recoveryEpoch())
                    throw new IllegalStateException("container removal lost its recovery tombstone");
                journal.retireRemoval(witness); journal.persist(level); return;
            }
            var p = witness.position(); var pos = new BlockPos(p.x(), p.y(), p.z());
            if (level.getChunkSource().getChunkNow(p.x() >> 4, p.z() >> 4) == null) continue;
            if (level.getBlockEntity(pos) instanceof ChestBlockEntity chest
                    && FrontierV3PhysicalContainer.observedChest(id, chest).declared()) continue;
            var recovery = state.fencedRecovery().current().get(FencedRecoveryContainerSupport.bindingId(record));
            if (recovery == null || recovery.authorityEpoch() != witness.recoveryEpoch()
                    || surface.status() != ContainerSurfaceStatus.ACTIVE)
                throw new IllegalStateException("container removal has stale surface authority");
            if (state.inventory().items().values().stream().anyMatch(item -> item.custody() instanceof InventoryCustody.ContainerSlot slot && slot.containerId().equals(id))
                    || state.inventory().fungibleResources().accounts().values().stream().anyMatch(account ->
                        account.custody().equals(new ResourceCustody.Container(id)) && !account.lotQuantities().isEmpty())
                    || journal.exactDrops().values().stream().anyMatch(drop -> ((InventoryCustody.ContainerSlot) drop.from()).containerId().equals(id))) continue;
            var lease = state.replicaCustody().custodyByScope().get(ReferenceContainerCustody.scopeId(id));
            if (lease == null || lease.authorityEpoch() != witness.custodyEpoch())
                throw new IllegalStateException("container removal has stale custody authority");
            if (ReferenceContainerCustody.hasLiveCustody(state, id)) {
                long revision = Math.max(runtime.canonicalState().orElseThrow().revision().value(), lease.expectedCanonicalRevision() + 1L);
                if (!(FrontierV3CommandSubmission.submit(runtime, "container-removal-close", id.value(),
                        ReferenceContainerCustody.confirmedMutationTransition(state, id, revision)) instanceof CommandResult.Accepted))
                    throw new IllegalStateException("witnessed container removal custody closure rejected");
            }
            if (!(FrontierV3CommandSubmission.submit(runtime, "container-removal-retire", id.value(),
                    new ContainerSurfaceTransition(id, ContainerSurfaceStatus.RETIRED)) instanceof CommandResult.Accepted))
                throw new IllegalStateException("witnessed container retirement rejected");
            io.farfrontier.palemirror.PaleMirrorMod.LOGGER.info("PMV3_CONTAINER_RETIRED container={} position={} custodyEpoch={} recoveryEpoch={}",
                    id.value(), p, witness.custodyEpoch(), witness.recoveryEpoch());
            journal.retireRemoval(witness); journal.persist(level); return;
        }
    }
}
