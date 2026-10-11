package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;

/** Causal native merge adapter. Untracked stock cannot acquire title by proximity; live pickups retain their UUID. */
public final class FrontierV3GroundItemMerge {
    private FrontierV3GroundItemMerge() { }
    public static boolean before(ItemEntity destination, ItemStack destinationStack, ItemEntity source, ItemStack sourceStack) {
        if (!(source.level() instanceof ServerLevel level) || !FrontierV3PhysicalWorld.isPhysical(level)) return true;
        var runtime = FrontierV3ServerLifecycle.runtimeFor(level.getServer());
        if (runtime == null || runtime.status().kind() != FrontierV3RuntimeStatus.Kind.ACTIVE) return true;
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
        var journal = FrontierV3ResourceHandoffLedger.get(level);
        if (journal.blocks(destination.getUUID()) || journal.blocks(source.getUUID())
                || journal.blocksAccount(from.accountId()) || journal.blocksAccount(to.accountId())) return false;
        var receipt = FungiblePhysicalHandoff.returnToExisting(resources, from.accountId(), from.authorityEpoch(), from,
                to.accountId(), to.authorityEpoch(), to);
        journal.prepare(destination.getUUID(), receipt); journal.persist(level); return true;
    }
    public static void after(ItemEntity destination) {
        if (!(destination.level() instanceof ServerLevel level) || !FrontierV3PhysicalWorld.isPhysical(level)) return;
        var runtime = FrontierV3ServerLifecycle.runtimeFor(level.getServer());
        if (runtime == null || runtime.status().kind() != FrontierV3RuntimeStatus.Kind.ACTIVE) return;
        var receipt = FrontierV3ResourceHandoffLedger.get(level).pending().get(destination.getUUID());
        if (receipt != null) FrontierV3ContainerDrops.reconcile(level, runtime, destination.getUUID(), receipt);
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
