package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.internal.frontier.v3.mixin.FrontierV3HorseMenuAccessor;
import io.farfrontier.palemirror.internal.frontier.v3.mixin.FrontierV3CompoundContainerAccessor;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.CompoundContainer;
import net.minecraft.world.entity.animal.horse.Donkey;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.HorseInventoryMenu;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

/** Menu-provider mapping only; accounting and durable click protocol remain common. */
record FrontierV3ContainerMenuTarget(ContainerRecord record, FrontierV3PhysicalContainer physical,
                                     int firstCargoMenuSlot, int equipmentSlotCount) {
    static boolean supported(AbstractContainerMenu menu) {
        return menu instanceof ChestMenu || menu instanceof HorseInventoryMenu
                || menu instanceof io.farfrontier.palemirror.internal.production.ProductionBenchMenu;
    }

    int cargoSlot(int menuSlot) {
        int slot = menuSlot - firstCargoMenuSlot;
        return slot >= 0 && slot < record.slotCount() ? slot : -1;
    }

    boolean equipmentSlot(int menuSlot) { return menuSlot >= 0 && menuSlot < equipmentSlotCount; }

    static java.util.List<FrontierV3ContainerMenuTarget> resolveAll(ServerPlayer player, AbstractContainerMenu menu,
                                                                 FrontierWorldState state) {
        if (menu instanceof ChestMenu chest && chest.getContainer() instanceof CompoundContainer) {
            var leaves = new java.util.ArrayList<net.minecraft.world.Container>();
            flatten(chest.getContainer(), leaves);
            var targets = new java.util.ArrayList<FrontierV3ContainerMenuTarget>();
            int offset = 0;
            for (var leaf : leaves) {
                if (leaf instanceof ChestBlockEntity block) {
                    var record = FrontierV3ContainerClickExecutor.ownedContainer(state, block.getBlockPos());
                    if (record != null) {
                        var physical = FrontierV3PhysicalContainer.loaded(player.serverLevel(), state, record.id()).orElseThrow(
                                () -> new IllegalArgumentException("composed member has no current physical provider"));
                        if (physical.inventory() != leaf || leaf.getContainerSize() != record.slotCount())
                            throw new IllegalArgumentException("composed member differs from its declared provider");
                        targets.add(new FrontierV3ContainerMenuTarget(record, physical, offset, 0));
                    }
                }
                offset = Math.addExact(offset, leaf.getContainerSize());
            }
            if (!targets.isEmpty() && (targets.size() != leaves.size() || offset != chest.getContainer().getContainerSize()
                    || targets.stream().map(t -> t.record().id()).distinct().count() != targets.size()))
                throw new IllegalArgumentException("composed menu contains an unadmitted or duplicate member");
            return java.util.List.copyOf(targets);
        }
        var target = resolve(player, menu, state);
        return target == null ? java.util.List.of() : java.util.List.of(target);
    }
    private static void flatten(net.minecraft.world.Container container, java.util.List<net.minecraft.world.Container> leaves) {
        if (leaves.size() >= 54) throw new IllegalArgumentException("unbounded menu composition");
        if (container instanceof CompoundContainer && container instanceof FrontierV3CompoundContainerAccessor access) {
            flatten(access.frontierV3$first(), leaves); flatten(access.frontierV3$second(), leaves);
        } else if (container instanceof CompoundContainer) {
            throw new IllegalArgumentException("compound provider lacks exact member declaration");
        } else leaves.add(container);
    }

    static FrontierV3ContainerMenuTarget resolve(ServerPlayer player, AbstractContainerMenu menu, FrontierWorldState state) {
        if (menu instanceof io.farfrontier.palemirror.internal.production.ProductionBenchMenu benchMenu
                && benchMenu.inventory() instanceof io.farfrontier.palemirror.internal.production.ProductionBenchBlockEntity bench) {
            var record = FrontierV3ContainerClickExecutor.ownedContainer(state, bench.getBlockPos());
            if (record == null) return null;
            var physical = FrontierV3PhysicalContainer.loaded(player.serverLevel(), state, record.id()).orElse(null);
            if (physical == null || physical.inventory() != bench || record.productionStation().isEmpty())
                throw new IllegalArgumentException("station menu lacks its exact registered physical provider");
            return new FrontierV3ContainerMenuTarget(record, physical, 0, 0);
        }
        if (menu instanceof ChestMenu chestMenu && chestMenu.getContainer() instanceof ChestBlockEntity chest) {
            var record = FrontierV3ContainerClickExecutor.ownedContainer(state, chest.getBlockPos());
            if (record == null) return null;
            var physical = FrontierV3PhysicalContainer.loaded(player.serverLevel(), state, record.id()).orElse(null);
            if (physical == null || physical.inventory() != chest)
                throw new IllegalArgumentException("declared menu container has no current physical provider");
            return new FrontierV3ContainerMenuTarget(record, physical, 0, 0);
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
            return new FrontierV3ContainerMenuTarget(record, physical, 2, 2);
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
