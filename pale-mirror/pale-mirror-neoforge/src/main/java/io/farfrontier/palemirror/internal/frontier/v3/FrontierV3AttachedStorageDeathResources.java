package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyId;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Exact attached-container pre-loot evidence. Slots locate stacks; resource lots retain identity/title. */
final class FrontierV3AttachedStorageDeathResources {
    private static final String RECEIPT = "pmv3_attached_storage_death_handoff_v1";
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
                var current = runtime.decodedState().orElseThrow();
                if (runtime.status().kind() != FrontierV3RuntimeStatus.Kind.ACTIVE) return;
                ActorBodyAuthority.requireRetiredDeath(current, id);
                var before = snapshots.get(slot); var actual = physical.inventory().getItem(slot);
                if (before.isEmpty() || !ItemStack.matches(before, actual)) continue;
                var address = new PhysicalStackAddress.ContainerSlot(new InventoryCustody.ContainerSlot(asset.containerId(), slot));
                var resources = current.inventory().fungibleResources();
                var binding = resources.bindings().values().stream().filter(b -> b.address().equals(address)).findFirst().orElse(null);
                if (binding == null || !matches(actual, binding)) continue; // Unknown native stock is never invented canonical stock.
                UUID carrier = dropId(current, id, binding);
                if (level.getEntity(carrier) != null) continue;
                var receipt = FungiblePhysicalHandoff.departToNew(resources, binding.accountId(), binding.authorityEpoch(), binding, 0,
                        new SubjectId("custody:world-" + carrier), new ResourceCustody.WorldCarrier(carrier), 1,
                        new PhysicalStackAddress.WorldEntity(carrier)).forfeitAffectedClaims(resources);
                if (!receipt.forfeitedClaimIds().isEmpty() && !FungibleClaimForfeitureStateSupport.supports(current, receipt)) continue;
                var drop = new ItemEntity(level, body.getX(), body.getY() + .25, body.getZ(), before.copy());
                drop.setUUID(carrier);
                drop.getPersistentData().putByteArray(RECEIPT, FrontierWorldRuntimeDefinition.payloadCodecs().encode(receipt));
                if (!level.addFreshEntity(drop)) continue;
                physical.inventory().setItem(slot, ItemStack.EMPTY);
                if (level.getEntity(carrier) == drop && matches(drop.getItem(), binding)) submit(runtime, receipt);
            }
        };
    }
    static boolean reconcileOneDrop(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, FrontierWorldState state) {
        for (var asset : state.transportFleet().assets().values().stream().sorted(Comparator.comparing(a -> a.actorId())).toList()) {
            if (state.actorLocations().get(asset.actorId()).condition().status() != ActorLifeStatus.DEAD) continue;
            var retired = state.fencedRecovery().tombstones().get(ActorBodyId.recoveryBindingId(asset.actorId()));
            if (retired == null) continue;
            var body = new ActorBodyId(asset.actorId(), retired.retiredEpoch());
            ActorBodyAuthority.requireRetiredDeath(state, body);
            for (var binding : state.inventory().fungibleResources().bindings().values()) {
                if (!(binding.address() instanceof PhysicalStackAddress.ContainerSlot slot)
                        || !slot.slot().containerId().equals(asset.containerId())) continue;
                UUID carrier = dropId(state, body, binding);
                if (!(level.getEntity(carrier) instanceof ItemEntity drop) || drop.isRemoved() || !matches(drop.getItem(), binding)
                        || !drop.getPersistentData().contains(RECEIPT, net.minecraft.nbt.Tag.TAG_BYTE_ARRAY)) continue;
                FungibleResourceHandoffObserved receipt;
                try { receipt = (FungibleResourceHandoffObserved) FrontierWorldRuntimeDefinition.payloadCodecs()
                        .decode("frontier.fungible_resource_handoff_observed", drop.getPersistentData().getByteArray(RECEIPT)); }
                catch (IllegalArgumentException invalid) { continue; }
                if (!receipt.sourceAccountId().equals(binding.accountId()) || receipt.sourceEpoch() != binding.authorityEpoch()
                        || !receipt.lotQuantities().equals(binding.lotQuantities())
                        || !receipt.destinationAccount().custody().equals(new ResourceCustody.WorldCarrier(carrier))) continue;
                if (submit(runtime, receipt)) return true;
            }
        }
        return false;
    }
    private static boolean matches(ItemStack item, PhysicalStackBinding binding) {
        return !item.isEmpty() && item.getCount() == binding.quantity()
                && item.getComponentsPatch().isEmpty() && BuiltInRegistries.ITEM.getKey(item.getItem()).toString().equals(binding.itemKind());
    }
    private static UUID dropId(FrontierWorldState state, ActorBodyId body, PhysicalStackBinding binding) {
        return UUID.nameUUIDFromBytes((state.bootstrap().worldId().value() + "\nattached-death\n" + body.actorId().value()
                + "\n" + body.physicalEpoch() + "\n" + binding.id().value()).getBytes(StandardCharsets.UTF_8));
    }
    private static boolean submit(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, FungibleResourceHandoffObserved receipt) {
        return FrontierV3CommandSubmission.submit(runtime, "attached-storage-death", receipt.destinationAccount().id().value(), receipt)
                instanceof CommandResult.Accepted;
    }
}
