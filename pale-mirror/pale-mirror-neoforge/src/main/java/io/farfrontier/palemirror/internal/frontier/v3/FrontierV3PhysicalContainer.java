package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.animal.horse.Donkey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import java.util.Objects;
import java.util.Optional;

/** Thin physical port. Declared identity selects the provider; a nearby body never does. */
record FrontierV3PhysicalContainer(SubjectId containerId, Container inventory) {
    FrontierV3PhysicalContainer { Objects.requireNonNull(containerId); Objects.requireNonNull(inventory); }

    static Optional<FrontierV3PhysicalContainer> loaded(ServerLevel level, FrontierWorldState state, SubjectId id) {
        var record = state.inventory().containers().get(id); var surface = state.inventory().surfaces().get(id);
        if (record == null || surface == null) throw new IllegalArgumentException("undeclared physical container");
        return switch (surface.location()) {
            case ContainerLocation.Fixed fixed -> {
                var p = fixed.position(); var pos = new net.minecraft.core.BlockPos(p.x(), p.y(), p.z());
                if (!level.hasChunkAt(pos) || !(level.getBlockEntity(pos) instanceof ChestBlockEntity chest)
                        || !id.value().equals(chest.getPersistentData().getString(FrontierV3ExactItemPresentation.CONTAINER_ID_KEY)))
                    yield Optional.empty();
                if (chest.getContainerSize() != record.slotCount()) throw new IllegalArgumentException("declared chest capacity differs");
                yield Optional.of(new FrontierV3PhysicalContainer(id, chest));
            }
            case ContainerLocation.Mobile mobile -> {
                var actor = state.actorLocations().get(mobile.actorId());
                if (actor == null || actor.kind() != ActorKind.PACK_ANIMAL)
                    throw new IllegalArgumentException("mobile container lacks its typed body producer");
                var entity = level.getEntity(io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyId.entityId(
                        state.bootstrap().worldId(), mobile.actorId()));
                if (!(entity instanceof Donkey donkey) || !FrontierV3ActorBodyController.recognizes(state, donkey)
                        || !FrontierV3AmbientActorExecutor.owned(donkey, mobile.actorId(), actor.kind()) || !donkey.hasChest())
                    yield Optional.empty();
                if (donkey.getInventory().getContainerSize() != record.slotCount() + 1)
                    throw new IllegalArgumentException("declared pack capacity differs from physical animal");
                yield Optional.of(new FrontierV3PhysicalContainer(id, new CargoSlots(donkey.getInventory(), record.slotCount())));
            }
        };
    }

    static FrontierV3PhysicalContainer declaredChest(SubjectId id, ChestBlockEntity chest) {
        if (!id.value().equals(chest.getPersistentData().getString(FrontierV3ExactItemPresentation.CONTAINER_ID_KEY)))
            throw new IllegalArgumentException("physical chest has a foreign declaration");
        return new FrontierV3PhysicalContainer(id, chest);
    }

    /** Saddle slot belongs to the animal equipment, not its goods inventory. No item copy. */
    private record CargoSlots(Container delegate, int size) implements Container {
        private int physical(int slot) {
            if (slot < 0 || slot >= size) throw new IllegalArgumentException("invalid mobile cargo slot");
            return slot + 1;
        }
        @Override public int getContainerSize() { return size; }
        @Override public boolean isEmpty() {
            for (int i = 0; i < size; i++) if (!getItem(i).isEmpty()) return false;
            return true;
        }
        @Override public ItemStack getItem(int slot) { return delegate.getItem(physical(slot)); }
        @Override public ItemStack removeItem(int slot, int quantity) { return delegate.removeItem(physical(slot), quantity); }
        @Override public ItemStack removeItemNoUpdate(int slot) { return delegate.removeItemNoUpdate(physical(slot)); }
        @Override public void setItem(int slot, ItemStack stack) { delegate.setItem(physical(slot), stack); }
        @Override public void setChanged() { delegate.setChanged(); }
        @Override public boolean stillValid(net.minecraft.world.entity.player.Player player) { return delegate.stillValid(player); }
        @Override public void clearContent() { for (int i = 0; i < size; i++) setItem(i, ItemStack.EMPTY); }
    }
}
