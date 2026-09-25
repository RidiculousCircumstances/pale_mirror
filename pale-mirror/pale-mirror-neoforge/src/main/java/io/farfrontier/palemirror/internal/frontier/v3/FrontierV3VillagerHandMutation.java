package io.farfrontier.palemirror.internal.frontier.v3;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

import java.lang.reflect.Method;

/** One observed farmer-cargo hand write, including the optional pack's manual-write permit. */
final class FrontierV3VillagerHandMutation {
    private static final String OVERHAUL = "villageroverhaul";
    private static final String HAND_BRIDGE = "org.z2six.villageroverhaul.server.ai.VillagerBrain";
    private static final String REASON = "pale_mirror_frontier_v3_field_custody";

    private FrontierV3VillagerHandMutation() { }

    static boolean setOffhand(Mob worker, ItemStack expected) {
        if (worker instanceof Villager villager && ModList.get().isLoaded(OVERHAUL)
                && !permitOverhaulWrite(villager, expected)) return false;
        worker.setItemInHand(InteractionHand.OFF_HAND, expected);
        ItemStack actual = worker.getOffhandItem();
        return expected.isEmpty() ? actual.isEmpty()
                : actual.getCount() == expected.getCount()
                && ItemStack.isSameItemSameComponents(actual, expected);
    }

    /** VillagerOverhaul's public hook grants a one-tick, item-specific manual equipment write. */
    private static boolean permitOverhaulWrite(Villager villager, ItemStack expected) {
        try {
            Class<?> bridge = Class.forName(HAND_BRIDGE, true, FrontierV3VillagerHandMutation.class.getClassLoader());
            Method notify = bridge.getMethod("notifyManualHandSet", net.minecraft.world.entity.Entity.class,
                    EquipmentSlot.class, ItemStack.class, String.class);
            notify.invoke(null, villager, EquipmentSlot.OFFHAND, expected, REASON);
            return true;
        } catch (ReflectiveOperationException | LinkageError unavailable) {
            // A loaded but incompatible pack may not silently turn a wheat receipt into an
            // empty-hand canonical success. The caller retains its exact physical witness.
            return false;
        }
    }
}
