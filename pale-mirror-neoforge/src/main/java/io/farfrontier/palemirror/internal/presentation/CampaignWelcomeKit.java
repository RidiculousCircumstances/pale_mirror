package io.farfrontier.palemirror.internal.presentation;

import java.util.List;

import io.farfrontier.palemirror.PaleMirrorItems;
import io.farfrontier.palemirror.internal.world.WorldObjectRegistryEntry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.component.ItemLore;

/** One-time diegetic handoff when an audience reaches a commissioned living region. */
public final class CampaignWelcomeKit {
    private static final String GRANTED_KEY = "pale_mirror_ironhill_welcome_v1";

    private CampaignWelcomeKit() { }

    public static boolean granted(ServerPlayer player) {
        return player.getPersistentData().getBoolean(GRANTED_KEY);
    }

    public static boolean grant(ServerPlayer player, WorldObjectRegistryEntry settlement) {
        if (granted(player)) return false;
        ItemStack letter = new ItemStack(Items.PAPER);
        letter.set(DataComponents.CUSTOM_NAME, Component.literal("Letter from Ironhill"));
        letter.set(DataComponents.LORE, new ItemLore(List.of(
                Component.literal("Our mine train is the town's iron lifeline."),
                Component.literal("Visit the bell or open the Regional Ledger (P)."))));
        ItemStack map = MapItem.create(player.serverLevel(), settlement.anchor().getX(), settlement.anchor().getZ(),
                (byte) 2, true, true);
        map.set(DataComponents.CUSTOM_NAME, Component.literal("Ironhill Survey Map"));
        ItemStack ledger = new ItemStack(PaleMirrorItems.REGIONAL_LEDGER.get());
        player.getInventory().placeItemBackInInventory(letter);
        player.getInventory().placeItemBackInInventory(map);
        player.getInventory().placeItemBackInInventory(ledger);
        player.getPersistentData().putBoolean(GRANTED_KEY, true);
        return true;
    }
}
