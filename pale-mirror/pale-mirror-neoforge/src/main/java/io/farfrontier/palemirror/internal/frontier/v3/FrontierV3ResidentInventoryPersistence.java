package io.farfrontier.palemirror.internal.frontier.v3;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.item.ItemStack;

/** Physical pocket addresses survive vanilla entity storage; no stock or ownership is inferred. */
public final class FrontierV3ResidentInventoryPersistence {
    private static final String KEY = "pmv3_indexed_pockets";
    private static final int FORMAT = 1;
    private FrontierV3ResidentInventoryPersistence() { }

    private static boolean managed(AbstractVillager body) {
        if (!FrontierV3ActorCarrierComposition.hasDeclarationMetadata(body.getPersistentData())) return false;
        if (FrontierV3ActorCarrierComposition.declaredBy(body)
                .or(() -> FrontierV3ActorCarrierComposition.declaredByUnloading(body)).isEmpty())
            throw new IllegalArgumentException("indexed pockets require a complete physical carrier declaration: " + body.getUUID());
        return true;
    }

    public static void write(AbstractVillager body, CompoundTag entity) {
        if (!managed(body)) return;
        var slots = new ListTag();
        for (int index = 0; index < body.getInventory().getContainerSize(); index++)
            slots.add(body.getInventory().getItem(index).saveOptional(body.registryAccess()));
        var inventory = new CompoundTag();
        inventory.putInt("format", FORMAT);
        inventory.put("slots", slots);
        entity.put(KEY, inventory);
        // Vanilla's unaddressed Inventory list merges identical stacks on load.
        // Only this indexed physical representation is authoritative for managed pockets.
        entity.remove("Inventory");
    }

    public static void read(AbstractVillager body, CompoundTag entity) {
        if (!managed(body)) return;
        if (!entity.contains(KEY, Tag.TAG_COMPOUND) || entity.contains("Inventory"))
            throw new IllegalArgumentException("unsupported managed pocket schema: " + body.getUUID());
        var inventory = entity.getCompound(KEY);
        if (!inventory.contains("format", Tag.TAG_INT) || inventory.getInt("format") != FORMAT
                || !inventory.contains("slots", Tag.TAG_LIST))
            throw new IllegalArgumentException("invalid indexed pocket format: " + body.getUUID());
        var slots = inventory.getList("slots", Tag.TAG_COMPOUND);
        if (!slots.equals(inventory.get("slots")) || slots.size() != body.getInventory().getContainerSize())
            throw new IllegalArgumentException("invalid indexed pocket capacity: " + body.getUUID());
        var decoded = new java.util.ArrayList<ItemStack>(slots.size());
        for (int index = 0; index < slots.size(); index++) {
            var value = slots.getCompound(index);
            var stack = value.isEmpty() ? ItemStack.EMPTY : ItemStack.parse(body.registryAccess(), value).orElseThrow(
                    () -> new IllegalArgumentException("invalid indexed pocket stack: " + body.getUUID()));
            if (!stack.isEmpty() && (stack.getCount() < 1 || stack.getCount() > stack.getMaxStackSize()))
                throw new IllegalArgumentException("invalid indexed pocket quantity: " + body.getUUID());
            decoded.add(stack);
        }
        // Validate the entire saved physical image before touching the live inventory.
        body.getInventory().clearContent();
        for (int index = 0; index < decoded.size(); index++) body.getInventory().setItem(index, decoded.get(index));
    }
}
