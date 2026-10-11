package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import java.util.*;

/** Vanilla drop adapter: the exact source slot and actual spawned entity, not proximity matching. */
public final class FrontierV3ContainerDrops {
    private record Source(ServerLevel level, SubjectId container, int slot) { }
    private static final ThreadLocal<Source> ACTIVE = new ThreadLocal<>();
    private FrontierV3ContainerDrops() { }
    public static void drop(Level level, double x, double y, double z, ItemStack stack, Container container) {
        var previous = ACTIVE.get(); var source = source(level, container, stack);
        if (source == null) ACTIVE.remove(); else ACTIVE.set(source);
        try { Containers.dropItemStack(level, x, y, z, stack); }
        finally { if (previous == null) ACTIVE.remove(); else ACTIVE.set(previous); }
    }
    private static Source source(Level level, Container container, ItemStack stack) {
        if (!(level instanceof ServerLevel server) || !(container instanceof ChestBlockEntity chest)
                || !FrontierV3PhysicalWorld.isPhysical(server)) return null;
        var runtime = FrontierV3ServerLifecycle.runtimeFor(server.getServer());
        if (runtime == null || runtime.status().kind() != FrontierV3RuntimeStatus.Kind.ACTIVE) return null;
        String declared = chest.getPersistentData().getString(FrontierV3ExactItemPresentation.CONTAINER_ID_KEY);
        if (declared.isBlank()) return null;
        var id = new SubjectId(declared); var state = runtime.decodedState().orElseThrow();
        if (!state.inventory().containers().containsKey(id)
                || !FrontierV3PhysicalContainer.observedChest(id, chest).declared()) return null;
        for (int slot = 0; slot < container.getContainerSize(); slot++) if (container.getItem(slot) == stack) return new Source(server, id, slot);
        return null;
    }
    public static boolean addFreshEntity(Level level, Entity entity) {
        var source = ACTIVE.get();
        if (source == null || source.level() != level || !(entity instanceof ItemEntity drop)) return level.addFreshEntity(entity);
        var runtime = FrontierV3ServerLifecycle.runtimeFor(source.level().getServer());
        var journal = FrontierV3ResourceHandoffLedger.get(source.level());
        var state = journal.projected(runtime.decodedState().orElseThrow()); var resources = state.inventory().fungibleResources();
        var address = new PhysicalStackAddress.ContainerSlot(new InventoryCustody.ContainerSlot(source.container(), source.slot()));
        var binding = resources.bindings().values().stream().filter(value -> value.address().equals(address)).findFirst().orElse(null);
        if (binding == null) return level.addFreshEntity(entity); // Unknown native stock is not adopted.
        var actual = drop.getItem();
        if (!actual.getComponentsPatch().isEmpty() || !BuiltInRegistries.ITEM.getKey(actual.getItem()).toString().equals(binding.itemKind())
                || actual.getCount() > binding.quantity()) throw new IllegalStateException("causal container drop contradicts its exact source binding");
        var p = drop.blockPosition();
        var receipt = FungiblePhysicalHandoff.departToNew(resources, binding.accountId(), binding.authorityEpoch(), binding,
                binding.quantity() - actual.getCount(), new SubjectId("custody:ground-" + drop.getUUID()),
                new ResourceCustody.Ground(drop.getUUID(), new BlockPosition(p.getX(), p.getY(), p.getZ())), 1,
                new PhysicalStackAddress.WorldEntity(drop.getUUID())).forfeitAffectedClaims(resources);
        journal.prepare(drop.getUUID(), receipt); journal.persist(source.level());
        drop.getPersistentData().putBoolean(FrontierV3GroundItemPosition.MARKER, true);
        boolean added = level.addFreshEntity(entity);
        if (added) reconcile(source.level(), runtime, drop.getUUID(), receipt);
        return added;
    }
    static boolean reconcileOne(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        for (var entry : FrontierV3ResourceHandoffLedger.get(level).pending().entrySet())
            if (reconcile(level, runtime, entry.getKey(), entry.getValue())) return true;
        return false;
    }
    static boolean reconcile(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
            UUID id, FungibleResourceHandoffObserved receipt) {
        var state = runtime.decodedState().orElseThrow(); var resources = state.inventory().fungibleResources();
        var arrived = resources.accounts().get(receipt.destinationAccount().id());
        if (arrived != null && arrived.equals(receipt.withoutForfeitedClaims().destinationAccount())) {
            var journal = FrontierV3ResourceHandoffLedger.get(level); journal.retire(id, receipt); journal.persist(level); return true;
        }
        if (!(level.getEntity(id) instanceof ItemEntity drop) || drop.isRemoved()) return false;
        var binding = receipt.destinationBindings().getFirst();
        if (!drop.getItem().getComponentsPatch().isEmpty() || drop.getItem().getCount() != binding.quantity()
                || !BuiltInRegistries.ITEM.getKey(drop.getItem().getItem()).toString().equals(binding.itemKind())) return false;
        if (!receipt.forfeitedClaimIds().isEmpty() && !FungibleClaimForfeitureStateSupport.supports(state, receipt)) return false;
        if (!(FrontierV3CommandSubmission.submit(runtime, "container-causal-drop", id.toString(), receipt) instanceof CommandResult.Accepted)) return false;
        var journal = FrontierV3ResourceHandoffLedger.get(level); journal.retire(id, receipt); journal.persist(level); return true;
    }
}
