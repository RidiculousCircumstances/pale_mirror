package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;

/** Resource-lot I/O for the shared production executor and its existing effect protocol. */
final class FrontierV3FungibleProductionEffect {
    private FrontierV3FungibleProductionEffect() { }

    static void execute(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, FrontierWorldState state, PhysicalIntent intent) {
        FungibleProductionLayout layout;
        ProductionJob job = state.productionJobs().get(intent.causeSubjectId());
        try { FungibleProductionStateSupport.validateIntent(state, intent); layout = FungibleProductionLayout.plan(state, job); }
        catch (IllegalArgumentException unavailable) {
            if (intent.status() != PhysicalIntentStatus.UNKNOWN_AFTER_RESTART) {
                FrontierV3ProductionTransformationExecutor.transition(runtime, intent.id(), PhysicalIntentStatus.UNKNOWN_AFTER_RESTART,
                        Optional.empty(), "lot-precondition-conflict");
            }
            return;
        }
        ContainerSurface surface = state.inventory().surfaces().get(layout.containerId());
        if (surface == null || surface.status() != ContainerSurfaceStatus.ACTIVE) return;
        BlockPosition p = surface.position(); BlockPos position = new BlockPos(p.x(), p.y(), p.z());
        if (!level.hasChunkAt(position)) return;
        ChestBlockEntity chest = FrontierV3ContainerSurfaceExecutor.activeChest(level, position, layout.containerId());
        if (chest == null) {
            if (intent.status() != PhysicalIntentStatus.UNKNOWN_AFTER_RESTART) {
                FrontierV3ProductionTransformationExecutor.transition(runtime, intent.id(), PhysicalIntentStatus.UNKNOWN_AFTER_RESTART,
                        Optional.empty(), "lot-chest-conflict");
            }
            return;
        }
        FrontierV3ProductionTransformationExecutor.executeEffect(intent.status(), new FrontierV3ProductionTransformationExecutor.EffectTurn() {
            @Override public boolean inputPresent() { return matches(chest, state, layout, layout.before()); }
            @Override public boolean outputPresent() { return matches(chest, state, layout, layout.after()); }
            @Override public boolean begin() { return transition(PhysicalIntentStatus.RUNNING, Optional.empty(), "running"); }
            @Override public boolean replaceInput() { return replace(chest, state, layout); }
            @Override public void confirm() {
                if (!outputPresent()) throw new IllegalStateException("production lot result changed before its observation");
                var observed = new ArrayList<FungiblePhysicalObservation.Stack>();
                for (int slot = 0; slot < chest.getContainerSize(); slot++) {
                    ItemStack stack = chest.getItem(slot);
                    if (stack.isEmpty() || state.inventory().itemAt(layout.containerId(), slot).isPresent()) continue;
                    observed.add(new FungiblePhysicalObservation.Stack(new PhysicalStackAddress.ContainerSlot(
                            new InventoryCustody.ContainerSlot(layout.containerId(), slot)), BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), stack.getCount()));
                }
                var held = (ProductionInputHold.FungibleBound) job.inputHold();
                var receipt = new FungibleProductionObservation(new PhysicalObservationId("observation:" + intent.id().value().replace(':', '-')),
                        intent.id(), held.accountId(), layout.containerId(), held.itemId(), held.claimId(), job.outputItemId(), job.outputCount(),
                        held.authorityEpoch(), observed);
                if (!transition(PhysicalIntentStatus.CONFIRMED, Optional.of(receipt), "confirmed")
                        || !FrontierV3ReferenceContainerCustodyExecutor.checkpointConfirmedMutation(runtime, layout.containerId(), chest)) {
                    throw new IllegalStateException("confirmed production lot output lacks its next replica boundary");
                }
            }
            @Override public void unknown(String reason) { transition(PhysicalIntentStatus.UNKNOWN_AFTER_RESTART, Optional.empty(), reason); }
            private boolean transition(PhysicalIntentStatus status, Optional<PhysicalEffectObservation> observation, String phase) {
                return FrontierV3ProductionTransformationExecutor.transition(runtime, intent.id(), status, observation, "lot-" + phase);
            }
        });
    }

    static boolean matches(ChestBlockEntity chest, FrontierWorldState state, FungibleProductionLayout layout,
                           Map<Integer, ReferenceContainerCustody.ProjectedFungibleSlot> expected) {
        if (chest.getContainerSize() != state.inventory().containers().get(layout.containerId()).slotCount()) return false;
        for (int slot = 0; slot < chest.getContainerSize(); slot++) {
            ItemStack actual = chest.getItem(slot);
            ExactItemStack exact = state.inventory().itemAt(layout.containerId(), slot).orElse(null);
            if (exact != null) { if (!FrontierV3CargoHandoffExecutor.exactMatch(actual, exact)) return false; continue; }
            var resource = expected.get(slot);
            if (resource == null) { if (!actual.isEmpty()) return false; continue; }
            ItemStack requested = stack(resource);
            if (actual.isEmpty() || actual.getCount() != resource.quantity() || !ItemStack.isSameItemSameComponents(actual, requested)) return false;
        }
        return true;
    }

    static boolean replace(ChestBlockEntity chest, FrontierWorldState state, FungibleProductionLayout layout) {
        if (!matches(chest, state, layout, layout.before())) return false;
        for (int slot = 0; slot < chest.getContainerSize(); slot++) {
            if (java.util.Objects.equals(layout.before().get(slot), layout.after().get(slot))) continue;
            var resource = layout.after().get(slot);
            chest.setItem(slot, resource == null ? ItemStack.EMPTY : stack(resource));
        }
        chest.setChanged(); return matches(chest, state, layout, layout.after());
    }

    private static ItemStack stack(ReferenceContainerCustody.ProjectedFungibleSlot resource) {
        return new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(resource.itemKind())), resource.quantity());
    }
}
