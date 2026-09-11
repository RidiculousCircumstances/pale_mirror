package io.farfrontier.palemirror.internal.frontier.v3.client;

import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;

/** Exact ordinary menu protocol used by the development-only visible pilot. */
final class FrontierV3PilotInventoryActions {
    private FrontierV3PilotInventoryActions() { }

    static Result splitMoveFromContainer(Minecraft minecraft, JsonObject action, boolean attempted) {
        if (minecraft.player.containerMenu == minecraft.player.inventoryMenu) return Result.waiting();
        ResourceLocation item = ResourceLocation.parse(action.get("item").getAsString());
        int sourceCount = action.get("sourceCount").getAsInt(); int movedCount = action.get("movedCount").getAsInt();
        int remaining = sourceCount - movedCount;
        if (attempted) {
            boolean sourceRetained = minecraft.player.containerMenu.slots.stream().filter(slot -> slot.container != minecraft.player.getInventory())
                    .anyMatch(slot -> sameStack(slot, item, remaining));
            boolean playerReceived = minecraft.player.containerMenu.slots.stream().filter(slot -> slot.container == minecraft.player.getInventory())
                    .anyMatch(slot -> sameStack(slot, item, movedCount));
            return sourceRetained && playerReceived ? Result.finished() : Result.waiting();
        }
        Slot source = minecraft.player.containerMenu.slots.stream().filter(slot -> slot.container != minecraft.player.getInventory())
                .filter(slot -> sameStack(slot, item, sourceCount)).findFirst().orElse(null);
        Slot destination = minecraft.player.containerMenu.slots.stream().filter(slot -> slot.container == minecraft.player.getInventory())
                .filter(slot -> slot.getItem().isEmpty()).findFirst().orElse(null);
        if (source == null || destination == null) return Result.waiting();
        int sourceSlot = minecraft.player.containerMenu.slots.indexOf(source);
        int destinationSlot = minecraft.player.containerMenu.slots.indexOf(destination);
        if (sourceSlot < 0 || destinationSlot < 0) throw new IllegalStateException("split move slot is absent from the open container menu");
        minecraft.gameMode.handleInventoryMouseClick(minecraft.player.containerMenu.containerId, sourceSlot, 1, ClickType.PICKUP, minecraft.player);
        minecraft.gameMode.handleInventoryMouseClick(minecraft.player.containerMenu.containerId, destinationSlot, 0, ClickType.PICKUP, minecraft.player);
        return Result.afterClick();
    }

    static boolean sameStack(Slot slot, ResourceLocation item, int count) {
        return !slot.getItem().isEmpty() && slot.getItem().getCount() == count && BuiltInRegistries.ITEM.getKey(slot.getItem().getItem()).equals(item);
    }

    record Result(boolean attempted, boolean complete) {
        static Result waiting() { return new Result(false, false); }
        static Result afterClick() { return new Result(true, false); }
        static Result finished() { return new Result(true, true); }
    }
}
