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

    record QuickMoveAttempt(int menuId,int stateId,int sourceSlot,net.minecraft.world.item.ItemStack source,int beforeQuantity){}
    record QuickMoveResult(boolean complete,QuickMoveAttempt attempt,String detail){}
    /** A new click is permitted only after a server state update restored the exact unchanged preimage. */
    static QuickMoveResult quickMove(Minecraft minecraft,ResourceLocation item,int count,boolean intoContainer,QuickMoveAttempt previous){
        var menu=minecraft.player.containerMenu;
        int quantity=intoContainer?containerQuantity(minecraft,item):playerQuantity(minecraft,item);
        if(previous!=null){
            if(previous.menuId()!=menu.containerId)throw new IllegalStateException("quick move lost its exact open menu");
            var actual=menu.getSlot(previous.sourceSlot()).getItem();
            // Vanilla suppresses echoes of a correct prediction. The carrier's terminal domain assertion confirms causality.
            if(quantity==previous.beforeQuantity()+count && actual.isEmpty())return new QuickMoveResult(true,null,"destination reached");
            if(menu.getStateId()==previous.stateId())return new QuickMoveResult(false,previous,"await server menu acknowledgement");
            if(quantity!=previous.beforeQuantity() || actual.getCount()!=previous.source().getCount()
                    || !net.minecraft.world.item.ItemStack.isSameItemSameComponents(actual,previous.source()))
                return new QuickMoveResult(false,previous,"changed postimage is not permission to retry");
        }
        var source=menu.slots.stream().filter(slot->(slot.container==minecraft.player.getInventory())==intoContainer).filter(slot->sameStack(slot,item,count)).findFirst().orElse(null);
        if(source==null)return new QuickMoveResult(false,null,"declared source stack is unavailable");
        int index=menu.slots.indexOf(source);
        var next=new QuickMoveAttempt(menu.containerId,menu.getStateId(),index,source.getItem().copy(),quantity);
        minecraft.gameMode.handleInventoryMouseClick(menu.containerId,index,0,ClickType.QUICK_MOVE,minecraft.player);
        return new QuickMoveResult(false,next,"click submitted");
    }

    static void clickMenuButton(Minecraft minecraft, JsonObject action) {
        if (minecraft.player.containerMenu == minecraft.player.inventoryMenu)
            throw new IllegalStateException("ordinary menu button requires an open container");
        minecraft.gameMode.handleInventoryButtonClick(minecraft.player.containerMenu.containerId, action.get("button").getAsInt());
    }

    static Result openContainer(Minecraft minecraft, net.minecraft.core.BlockPos target, boolean attempted) {
        if (minecraft.player.containerMenu != minecraft.player.inventoryMenu) return Result.finished();
        if (attempted) return Result.waiting();
        minecraft.gameMode.useItemOn(minecraft.player, net.minecraft.world.InteractionHand.MAIN_HAND,
                new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(target),
                        net.minecraft.core.Direction.UP, target, false));
        return Result.afterClick();
    }

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

    /** Two ordinary left-clicks move an unchanged owned stack to a declared empty chest slot. */
    static Result moveWithinContainer(Minecraft minecraft, JsonObject action, boolean attempted) {
        if (minecraft.player.containerMenu == minecraft.player.inventoryMenu) return Result.waiting();
        ResourceLocation item = ResourceLocation.parse(action.get("item").getAsString());
        int count = action.get("count").getAsInt();
        int destinationIndex = action.get("destinationSlot").getAsInt();
        Slot destination = minecraft.player.containerMenu.slots.stream()
                .filter(slot -> slot.container != minecraft.player.getInventory() && slot.getSlotIndex() == destinationIndex)
                .findFirst().orElseThrow(() -> new IllegalStateException("declared chest destination slot is absent"));
        if (attempted) return sameStack(destination, item, count) && minecraft.player.containerMenu.getCarried().isEmpty()
                ? Result.finished() : Result.waiting();
        if (!destination.getItem().isEmpty() || !minecraft.player.containerMenu.getCarried().isEmpty())
            throw new IllegalStateException("ordinary chest reorder needs an empty destination and cursor");
        Slot source = minecraft.player.containerMenu.slots.stream()
                .filter(slot -> slot.container != minecraft.player.getInventory() && slot.getSlotIndex() != destinationIndex)
                .filter(slot -> sameStack(slot, item, count)).findFirst().orElse(null);
        if (source == null) return Result.waiting();
        int sourceSlot = minecraft.player.containerMenu.slots.indexOf(source);
        int destinationSlot = minecraft.player.containerMenu.slots.indexOf(destination);
        minecraft.gameMode.handleInventoryMouseClick(minecraft.player.containerMenu.containerId, sourceSlot, 0, ClickType.PICKUP, minecraft.player);
        minecraft.gameMode.handleInventoryMouseClick(minecraft.player.containerMenu.containerId, destinationSlot, 0, ClickType.PICKUP, minecraft.player);
        return Result.afterClick();
    }

    static int playerQuantity(Minecraft minecraft, ResourceLocation item) {
        return quantity(minecraft, item, true);
    }
    static int containerQuantity(Minecraft minecraft, ResourceLocation item) {
        return quantity(minecraft, item, false);
    }
    private static int quantity(Minecraft minecraft, ResourceLocation item, boolean inPlayer) {
        return minecraft.player.containerMenu.slots.stream()
                .filter(slot -> (slot.container == minecraft.player.getInventory()) == inPlayer)
                .filter(slot -> !slot.getItem().isEmpty() && BuiltInRegistries.ITEM.getKey(slot.getItem().getItem()).equals(item))
                .mapToInt(slot -> slot.getItem().getCount()).sum();
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
