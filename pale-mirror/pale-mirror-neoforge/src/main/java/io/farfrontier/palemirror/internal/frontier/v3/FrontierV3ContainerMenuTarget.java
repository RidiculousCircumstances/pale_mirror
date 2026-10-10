package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.internal.frontier.v3.mixin.FrontierV3HorseMenuAccessor;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.CompoundContainer;
import net.minecraft.world.entity.animal.horse.Donkey;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.HorseInventoryMenu;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

/** Menu-provider mapping only; accounting and durable click protocol remain common. */
record FrontierV3ContainerMenuTarget(ContainerRecord record, FrontierV3PhysicalContainer physical,
                                     int firstCargoMenuSlot) {
    static boolean supported(AbstractContainerMenu menu) {
        return menu instanceof ChestMenu || menu instanceof HorseInventoryMenu;
    }

    int cargoSlot(int menuSlot) {
        int slot = menuSlot - firstCargoMenuSlot;
        return slot >= 0 && slot < record.slotCount() ? slot : -1;
    }

    boolean equipmentSlot(int menuSlot) { return menuSlot >= 0 && menuSlot < firstCargoMenuSlot; }

    static FrontierV3ContainerMenuTarget resolve(ServerPlayer player, AbstractContainerMenu menu, FrontierWorldState state) {
        if (menu instanceof ChestMenu chestMenu && chestMenu.getContainer() instanceof ChestBlockEntity chest) {
            var record = FrontierV3ContainerClickExecutor.ownedContainer(state, chest.getBlockPos());
            if (record == null) return null;
            var physical = FrontierV3PhysicalContainer.loaded(player.serverLevel(), state, record.id()).orElse(null);
            if (physical == null || physical.inventory() != chest)
                throw new IllegalArgumentException("declared menu container has no current physical provider");
            return new FrontierV3ContainerMenuTarget(record, physical, 0);
        }
        if (menu instanceof HorseInventoryMenu && menu instanceof FrontierV3HorseMenuAccessor access
                && access.frontierV3$horse() instanceof Donkey donkey) {
            var body = FrontierV3ActorCarrierComposition.declaredBy(donkey).orElse(null);
            if (body == null) return null;
            var asset = state.transportFleet().assets().get(body.actorId());
            if (asset == null) throw new IllegalArgumentException("declared pack body has no current cargo owner");
            var record = state.inventory().containers().get(asset.containerId());
            var surface = state.inventory().surfaces().get(asset.containerId());
            if (record == null || surface == null || surface.status() != ContainerSurfaceStatus.ACTIVE
                    || !(surface.location() instanceof ContainerLocation.Mobile mobile)
                    || !mobile.actorId().equals(body.actorId()) || !FrontierV3ActorBodyController.recognizes(state, donkey))
                throw new IllegalArgumentException("declared mobile cargo has no active exact surface/body");
            var physical = FrontierV3PhysicalContainer.loaded(player.serverLevel(), state, record.id()).orElse(null);
            // Minecraft's saddle/armor menu slots are not part of the declared 15-slot cargo.
            if (physical == null || menu.slots.size() < record.slotCount() + 2
                    || menu.getSlot(2).container != donkey.getInventory())
                throw new IllegalArgumentException("declared mobile menu has no current cargo provider");
            return new FrontierV3ContainerMenuTarget(record, physical, 2);
        }
        return null;
    }

    static boolean managedCompound(ServerPlayer player, AbstractContainerMenu menu, FrontierWorldState state) {
        if (!(menu instanceof ChestMenu chestMenu) || !(chestMenu.getContainer() instanceof CompoundContainer compound)) return false;
        return state.inventory().surfaces().entrySet().stream()
                .filter(entry -> entry.getValue().fixed()
                        && ReferenceContainerCustody.isReferenceContainer(state, entry.getKey()))
                .map(entry -> entry.getValue().position())
                .map(p -> new net.minecraft.core.BlockPos(p.x(), p.y(), p.z()))
                .filter(player.serverLevel()::hasChunkAt).map(player.serverLevel()::getBlockEntity)
                .anyMatch(entity -> entity instanceof ChestBlockEntity chest && compound.contains(chest));
    }
}
