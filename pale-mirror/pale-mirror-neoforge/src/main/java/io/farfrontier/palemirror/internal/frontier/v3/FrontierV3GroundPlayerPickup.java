package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import java.util.*;

/** Wraps the causal Vanilla inventory add. It never discovers ownership by matching player stacks. */
public final class FrontierV3GroundPlayerPickup {
    public static boolean add(ItemEntity entity, Inventory inventory, ItemStack stack) {
        if (!(entity.level() instanceof ServerLevel level) || !FrontierV3PhysicalWorld.isPhysical(level)) return inventory.add(stack);
        var runtime = FrontierV3ServerLifecycle.runtimeFor(level.getServer());
        if (runtime == null || runtime.status().kind() != FrontierV3RuntimeStatus.Kind.ACTIVE) return inventory.add(stack);
        var state = runtime.decodedState().orElseThrow(); var resources = state.inventory().fungibleResources();
        var account = resources.accounts().values().stream().filter(value -> value.custody() instanceof ResourceCustody.Ground ground
                && ground.entityId().equals(entity.getUUID())).findFirst().orElse(null);
        if (account == null) return inventory.add(stack);
        var journal = FrontierV3ResourceHandoffLedger.get(level);
        if (journal.blocks(entity.getUUID()) || journal.blocksAccount(account.id())) return false;
        var bindings = resources.bindings().values().stream().filter(value -> value.accountId().equals(account.id())).toList();
        if (bindings.size() != 1 || stack != entity.getItem() || stack.getComponentsPatch().size() != 0
                || stack.getCount() != bindings.getFirst().quantity()
                || !BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(bindings.getFirst().itemKind()))
            throw new IllegalStateException("ground player pickup lost its exact current native source: " + account.id());
        int before = stack.getCount(); int capacity = capacity(inventory, stack);
        int amount = inventory.player.hasInfiniteMaterials() ? before : Math.min(before, capacity);
        if (amount == 0) return inventory.add(stack);
        var receipt = GroundResourcePlayerWithdrawal.prepare(state, account.id(), inventory.player.getUUID(), UUID.randomUUID(), amount);
        journal.prepareLoss(receipt); journal.persist(level); // Before either the player inventory or source stack changes.
        boolean added = inventory.add(stack);
        if (stack.getCount() != before - amount)
            throw new IllegalStateException("ground player pickup diverged from its persisted Vanilla postimage: " + account.id());
        FrontierV3GroundItemDestruction.reconcile(level, runtime, receipt);
        return added;
    }
    /** Ordinary Vanilla add(-1): main slots, matching offhand, never an empty offhand or armour slot. */
    static int capacity(Inventory inventory, ItemStack source) {
        int result = 0;
        for (var destination : inventory.items) result = Math.addExact(result, capacity(inventory, source, destination, true));
        result = Math.addExact(result, capacity(inventory, source, inventory.getItem(40), false));
        return result;
    }
    private static int capacity(Inventory inventory, ItemStack source, ItemStack destination, boolean emptyAllowed) {
        if (destination.isEmpty()) return emptyAllowed ? inventory.getMaxStackSize(source) : 0;
        return destination.isStackable() && ItemStack.isSameItemSameComponents(source, destination)
                ? Math.max(0, inventory.getMaxStackSize(destination) - destination.getCount()) : 0;
    }
    private FrontierV3GroundPlayerPickup() { }
}
