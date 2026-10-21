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
    static FrontierV3ActorDeathResourceComposition.AfterFatality prepare(ServerLevel level,
            FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, Mob body, ActorBodyId id) {
        var state = runtime.decodedState().orElseThrow();
        var asset = state.transportFleet().assets().get(id.actorId());
        if (asset == null) return () -> { };
        var physical = FrontierV3PhysicalContainer.loaded(level, state, asset.containerId()).orElse(null);
        if (physical == null || !ActorBodyAuthority.current(state, id.actorId()).equals(id)) return () -> { };
        var snapshots = new ArrayList<ItemStack>();
        for (int slot = 0; slot < physical.inventory().getContainerSize(); slot++) snapshots.add(physical.inventory().getItem(slot).copy());
        return () -> {
            for (int slot = 0; slot < snapshots.size(); slot++) {
                if (runtime.status().kind() != FrontierV3RuntimeStatus.Kind.ACTIVE) return;
                var current = runtime.decodedState().orElseThrow(); ActorBodyAuthority.requireRetiredDeath(current, id);
                var before = snapshots.get(slot); var actual = physical.inventory().getItem(slot);
                if (before.isEmpty() || !ItemStack.matches(before, actual)) continue;
                var address = new PhysicalStackAddress.ContainerSlot(new InventoryCustody.ContainerSlot(asset.containerId(), slot));
                boolean managed = current.inventory().fungibleResources().bindings().values().stream().anyMatch(b -> b.address().equals(address))
                        || current.inventory().itemAt(asset.containerId(), slot).isPresent();
                if (!managed) continue; // Ordinary untracked native loot remains Minecraft's responsibility.
                FrontierV3ContainerDropOperation.perform(level, runtime, physical, slot, actual, body.getX(), body.getY() + .25, body.getZ());
            }
        };
    }
}
