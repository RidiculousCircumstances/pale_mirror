package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.CommandResult;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.ContainerSurface;
import io.farfrontier.palemirror.frontier.v3.model.ContainerSurfaceStatus;
import io.farfrontier.palemirror.frontier.v3.model.CustodyAccount;
import io.farfrontier.palemirror.frontier.v3.model.FungiblePhysicalObservation;
import io.farfrontier.palemirror.frontier.v3.model.FungibleResourceLedger;
import io.farfrontier.palemirror.frontier.v3.model.FungibleStackBindingsReleased;
import io.farfrontier.palemirror.frontier.v3.model.FungibleStackLayoutObserved;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalCustodyLease;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalCustodyLeaseStatus;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalStackBinding;
import io.farfrontier.palemirror.frontier.v3.model.ReferenceContainerCustody;
import io.farfrontier.palemirror.frontier.v3.model.ResourceCustody;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

import java.util.Comparator;
import java.util.List;

/**
 * Converts a naturally ticking owned chest layout into the one temporary fungible binding
 * epoch. It never writes stacks, adopts foreign stock, or creates a fallback COLD balance.
 */
final class FrontierV3FungibleResourceObservationExecutor {
    private FrontierV3FungibleResourceObservationExecutor() { }

    static void tick(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        FrontierWorldState state = runtime.decodedState().orElse(null);
        if (state == null) return;
        for (CustodyAccount account : state.inventory().fungibleResources().accounts().values().stream()
                .filter(value -> value.custody() instanceof ResourceCustody.Container)
                .sorted(Comparator.comparing(CustodyAccount::id)).toList()) {
            ResourceCustody.Container custody = (ResourceCustody.Container) account.custody();
            ContainerSurface surface = state.inventory().surfaces().get(custody.containerId());
            if (surface == null || surface.status() != ContainerSurfaceStatus.ACTIVE || !ReferenceContainerCustody.isReferenceContainer(state, custody.containerId())) continue;
            BlockPos position = new BlockPos(surface.position().x(), surface.position().y(), surface.position().z());
            if (!level.hasChunkAt(position) || !level.shouldTickBlocksAt(position)
                    || !(level.getBlockEntity(position) instanceof ChestBlockEntity chest)) continue;
            PhysicalCustodyLease lease = state.replicaCustody().custodyByScope().get(ReferenceContainerCustody.scopeId(custody.containerId()));
            if (lease != null && lease.status() == PhysicalCustodyLeaseStatus.CHECKPOINTED) {
                release(runtime, account, lease);
                return;
            }
            if (lease != null && (!lease.live() || lease.status() != PhysicalCustodyLeaseStatus.ACQUIRED)) continue;
            long epoch = lease == null ? nextEpoch(state, custody.containerId()) : lease.authorityEpoch();
            if (!observe(level, runtime, state, account, chest, epoch)) return;
        }
    }

    private static boolean observe(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, FrontierWorldState state,
                                   CustodyAccount account, ChestBlockEntity chest, long epoch) {
        List<FungiblePhysicalObservation.Stack> stacks = FrontierV3ContainerSurfaceExecutor.observedFungibleSlots(chest, state,
                ((ResourceCustody.Container) account.custody()).containerId());
        List<PhysicalStackBinding> expected;
        try {
            expected = FungiblePhysicalObservation.bind(state.inventory().fungibleResources(), account.id(), epoch, stacks);
        } catch (IllegalArgumentException invalid) {
            FrontierV3ContainerSurfaceExecutor.reportConflict(runtime, ((ResourceCustody.Container) account.custody()).containerId());
            return false;
        }
        List<PhysicalStackBinding> current = state.inventory().fungibleResources().bindings().values().stream()
                .filter(binding -> binding.accountId().equals(account.id())).sorted(Comparator.comparing(PhysicalStackBinding::id)).toList();
        if (current.equals(expected)) return true;
        if (!current.isEmpty() && current.stream().anyMatch(binding -> binding.authorityEpoch() != epoch)) {
            FrontierV3ContainerSurfaceExecutor.reportConflict(runtime, ((ResourceCustody.Container) account.custody()).containerId());
            return false;
        }
        CommandResult result = FrontierV3CommandSubmission.submit(runtime, "fungible-layout", account.id().value(),
                new FungibleStackLayoutObserved(account.id(), epoch, stacks));
        return result instanceof CommandResult.Accepted;
    }

    private static void release(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, CustodyAccount account, PhysicalCustodyLease lease) {
        FungibleResourceLedger resources = runtime.decodedState().orElseThrow().inventory().fungibleResources();
        List<PhysicalStackBinding> current = resources.bindings().values().stream().filter(binding -> binding.accountId().equals(account.id())).toList();
        if (current.isEmpty()) return;
        if (current.stream().anyMatch(binding -> binding.authorityEpoch() != lease.authorityEpoch())) {
            throw new IllegalStateException("fungible resource binding has a stale reference custody epoch");
        }
        FrontierV3CommandSubmission.submit(runtime, "fungible-release", account.id().value(),
                new FungibleStackBindingsReleased(account.id(), lease.authorityEpoch()));
    }

    private static long nextEpoch(FrontierWorldState state, SubjectId containerId) {
        return state.replicaCustody().custodyByScope().values().stream().filter(lease -> lease.objectId().equals(containerId))
                .mapToLong(PhysicalCustodyLease::authorityEpoch).max().orElse(0L) + 1L;
    }
}
