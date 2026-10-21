package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyId;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import java.util.ArrayList;

/** Attached inventory delegates drops to the same source/birth operation as a fixed
 * container. This owner retains only pre-loot admission and fatality sequencing. */
final class FrontierV3AttachedStorageDeathResources {
    private FrontierV3AttachedStorageDeathResources() { }
    static net.minecraft.nbt.CompoundTag capture(ServerLevel level, FrontierWorldState state, Mob body) {
        var actor = FrontierV3ActorCarrierComposition.declaredBy(body).orElseThrow().actorId();
        var asset = state.transportFleet().assets().get(actor); var tag = new net.minecraft.nbt.CompoundTag();
        if (asset == null) return tag;
        var physical = FrontierV3PhysicalContainer.loaded(level, state, asset.containerId()).orElseThrow(
                () -> new IllegalStateException("fatality lacks its exact attached storage"));
        tag.putString("container", asset.containerId().value()); var slots = new net.minecraft.nbt.ListTag();
        for (int slot = 0; slot < physical.inventory().getContainerSize(); slot++)
            slots.add(physical.inventory().getItem(slot).saveOptional(level.registryAccess()));
        tag.put("slots", slots); return tag;
    }
    static boolean settled(FrontierWorldState state, ActorBodyId id) {
        return state.inventory().surfaces().values().stream()
                .filter(surface -> surface.location().equals(new ContainerLocation.Mobile(id.actorId())))
                .allMatch(surface -> state.inventory().items().values().stream().noneMatch(item -> item.custody()
                        instanceof InventoryCustody.ContainerSlot slot && slot.containerId().equals(surface.containerId()))
                    && state.inventory().fungibleResources().accounts().values().stream().noneMatch(account ->
                        account.custody().equals(new ResourceCustody.Container(surface.containerId()))));
    }
    static void recover(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
            Mob body, ActorBodyId id, FrontierV3BodyResourceSnapshot snapshot) {
        var state = runtime.decodedState().orElseThrow(); ActorBodyAuthority.requireRetiredDeath(state, id);
        var tag = snapshot.attached(); if (tag.isEmpty()) return;
        var container = new io.farfrontier.palemirror.frontier.v3.api.SubjectId(tag.getString("container"));
        var surface = state.inventory().surfaces().get(container); var declaration = state.inventory().containers().get(container);
        if (surface == null || declaration == null || !surface.location().equals(new ContainerLocation.Mobile(id.actorId()))
                || !(body instanceof net.minecraft.world.entity.animal.horse.Donkey donkey))
            throw new IllegalStateException("retired attached inventory lost its declared body/container relation");
        var slots = tag.getList("slots", net.minecraft.nbt.Tag.TAG_COMPOUND);
        if (!slots.equals(tag.get("slots")) || slots.size() != declaration.slotCount() || slots.size() > 54)
            throw new IllegalArgumentException("retired attached inventory has invalid native slot evidence");
        var before = new ArrayList<ItemStack>();
        for (int i = 0; i < slots.size(); i++) before.add(slots.getCompound(i).isEmpty() ? ItemStack.EMPTY
                : ItemStack.parse(level.registryAccess(), slots.getCompound(i)).orElseThrow());
        settle(level, runtime, body, id, FrontierV3PhysicalContainer.attached(container, declaration.slotCount(), donkey), before);
    }
    static FrontierV3ActorDeathResourceComposition.AfterFatality prepare(ServerLevel level,
            FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, Mob body, ActorBodyId id) {
        var state = runtime.decodedState().orElseThrow();
        var asset = state.transportFleet().assets().get(id.actorId());
        if (asset == null) return () -> { };
        var physical = FrontierV3PhysicalContainer.loaded(level, state, asset.containerId()).orElse(null);
        if (physical == null || !ActorBodyAuthority.current(state, id.actorId()).equals(id)) return () -> { };
        var snapshots = new ArrayList<ItemStack>();
        for (int slot = 0; slot < physical.inventory().getContainerSize(); slot++) snapshots.add(physical.inventory().getItem(slot).copy());
        return () -> settle(level, runtime, body, id, physical, snapshots);
    }
    private static void settle(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
            Mob body, ActorBodyId id, FrontierV3PhysicalContainer physical, java.util.List<ItemStack> snapshots) {
            for (int slot = 0; slot < snapshots.size(); slot++) {
                if (runtime.status().kind() != FrontierV3RuntimeStatus.Kind.ACTIVE) return;
                var current = runtime.decodedState().orElseThrow(); ActorBodyAuthority.requireRetiredDeath(current, id);
                var before = snapshots.get(slot); var actual = physical.inventory().getItem(slot);
                if (before.isEmpty() || !ItemStack.matches(before, actual)) continue;
                var address = new PhysicalStackAddress.ContainerSlot(new InventoryCustody.ContainerSlot(physical.containerId(), slot));
                boolean managed = current.inventory().fungibleResources().bindings().values().stream().anyMatch(b -> b.address().equals(address))
                        || current.inventory().itemAt(physical.containerId(), slot).isPresent();
                if (!managed) continue; // Ordinary untracked native loot remains Minecraft's responsibility.
                if (!FrontierV3ResourceHandoffLedger.get(level).canPrepareDrop(physical.containerId(), slot)) continue;
                FrontierV3ContainerDropOperation.perform(level, runtime, physical, slot, actual, body.getX(), body.getY() + .25, body.getZ());
            }
    }
}
