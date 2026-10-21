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
import java.util.*;

/** Vanilla drop adapter: the exact source slot and actual spawned entity, not proximity matching. */
public final class FrontierV3ContainerDrops {
    private FrontierV3ContainerDrops() { }
    public static void drop(Level level, double x, double y, double z, ItemStack stack, Container container) {
        if (stack.isEmpty() || !(level instanceof ServerLevel server) || !FrontierV3PhysicalWorld.isPhysical(server)) {
            Containers.dropItemStack(level, x, y, z, stack); return;
        }
        var runtime = FrontierV3ServerLifecycle.runtimeFor(server.getServer());
        if (runtime == null || runtime.status().kind() != FrontierV3RuntimeStatus.Kind.ACTIVE) {
            Containers.dropItemStack(level, x, y, z, stack); return;
        }
        var state = runtime.decodedState().orElseThrow();
        var physical = FrontierV3PhysicalContainer.observedFixed(server, state, container).orElse(null);
        if (physical == null) { Containers.dropItemStack(level, x, y, z, stack); return; }
        for (int slot = 0; slot < container.getContainerSize(); slot++) if (container.getItem(slot) == stack) {
            FrontierV3ContainerDropOperation.perform(server, runtime, physical, slot, stack, x, y, z); return;
        }
        throw new IllegalStateException("managed native drop has no exact source slot");
    }
    public static boolean addFreshEntity(Level level, Entity entity) {
        return FrontierV3ContainerDropOperation.addFreshEntity(level, entity);
    }
    static boolean reconcileOne(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        for (var transaction : FrontierV3ResourceHandoffLedger.get(level).containerDrops())
            if (FrontierV3ContainerDropOperation.reconcile(level, runtime, transaction)) return true;
        for (var entry : FrontierV3ResourceHandoffLedger.get(level).pending().entrySet())
            if (reconcile(level, runtime, entry.getKey(), entry.getValue())) return true;
        return false;
    }
    static boolean reconcile(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
            UUID id, FungibleResourceHandoffObserved receipt) {
        if (FrontierV3ResourceHandoffLedger.get(level).handoffOperation(id).isPresent())
            return FrontierV3NativeHandoffOperation.reconcile(level, runtime, id, receipt);
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
