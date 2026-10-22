package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;

/** Causal native merge adapter. Untracked stock cannot acquire title by proximity; live pickups retain their UUID. */
public final class FrontierV3GroundItemMerge {
    private static final ThreadLocal<java.util.UUID> NATIVE_MERGE = new ThreadLocal<>();
    private FrontierV3GroundItemMerge() { }
    public static boolean before(ItemEntity destination, ItemStack destinationStack, ItemEntity source, ItemStack sourceStack, Runnable nativeMerge) {
        if (source.getUUID().equals(NATIVE_MERGE.get())) return true;
        if (!(source.level() instanceof ServerLevel level) || !FrontierV3PhysicalWorld.isPhysical(level)) return true;
        var runtime = FrontierV3ServerLifecycle.runtimeForNativeMutation(level.getServer());
        if (runtime == null || runtime.status().kind() != FrontierV3RuntimeStatus.Kind.ACTIVE) return true;
        return before(level, runtime, destination, destinationStack, source, sourceStack, nativeMerge);
    }
    static boolean before(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
            ItemEntity destination, ItemStack destinationStack, ItemEntity source, ItemStack sourceStack, Runnable nativeMerge) {
        var journal = FrontierV3ResourceHandoffLedger.get(level);
        if (journal.blocks(destination.getUUID()) || journal.blocks(source.getUUID())) return false;
        var resources = runtime.decodedState().orElseThrow().inventory().fungibleResources();
        var from = binding(resources, source); var to = binding(resources, destination);
        if (from == null && to == null) return true;
        if (from == null || to == null || !from.claimQuantities().isEmpty() || !to.claimQuantities().isEmpty()
                || !matches(sourceStack, from) || !matches(destinationStack, to)
                || sourceStack.getCount() + destinationStack.getCount() > Math.min(64, destinationStack.getMaxStackSize())) return false;
        var sourceOwners = from.lotQuantities().keySet().stream().map(id -> resources.lots().get(id).economicOwnerId())
                .collect(java.util.stream.Collectors.toSet());
        var targetOwners = to.lotQuantities().keySet().stream().map(id -> resources.lots().get(id).economicOwnerId())
                .collect(java.util.stream.Collectors.toSet());
        if (sourceOwners.size() != 1 || !sourceOwners.equals(targetOwners)) return false;
        if (journal.blocksAccount(from.accountId()) || journal.blocksAccount(to.accountId())) return false;
        var receipt = FungiblePhysicalHandoff.returnToExisting(resources, from.accountId(), from.authorityEpoch(), from,
                to.accountId(), to.authorityEpoch(), to);
        var column = FrontierV3OperationEntityColumns.current(level, source.chunkPosition()).orElse(null);
        if (column == null) return false; // A later native merge attempt may retry admission, never the effect.
        var ports = java.util.List.of(column, FrontierV3OperationEntityParticipant.nativeObject(destination));
        FrontierV3NativeHandoffOperation.apply(level, runtime, destination.getUUID(), receipt,
                FrontierV3OperationReceipt.Protocol.GROUND_MERGE, ports,
                () -> !source.isRemoved() && !destination.isRemoved() && matches(source.getItem(), from) && matches(destination.getItem(), to),
                () -> {
                    var parent = NATIVE_MERGE.get(); NATIVE_MERGE.set(source.getUUID());
                    try { nativeMerge.run(); } finally {
                        if (parent == null) NATIVE_MERGE.remove(); else NATIVE_MERGE.set(parent);
                    }
                    return source.isRemoved();
                }, () -> source.isRemoved() && !destination.isRemoved()
                        && matches(destination.getItem(), receipt.destinationBindings().getFirst()));
        return false;
    }
    private static PhysicalStackBinding binding(FungibleResourceLedger resources, ItemEntity entity) {
        var address = new PhysicalStackAddress.WorldEntity(entity.getUUID());
        return resources.bindings().values().stream().filter(value -> value.address().equals(address))
                .filter(value -> resources.accounts().get(value.accountId()).custody() instanceof ResourceCustody.Ground).findFirst().orElse(null);
    }
    private static boolean matches(ItemStack stack, PhysicalStackBinding binding) {
        return !stack.isEmpty() && stack.getComponentsPatch().isEmpty() && stack.getCount() == binding.quantity()
                && BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(binding.itemKind());
    }
}
