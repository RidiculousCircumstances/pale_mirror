package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import java.util.*;

/** Uses Vanilla to prepare detached drop fragments, then commits their source and
 * births through one operation. No world insertion or original-stack split happens
 * before all participants are known. Inventory policy keeps its existing owners. */
final class FrontierV3ContainerDropOperation {
    private static final ThreadLocal<Context> ACTIVE = new ThreadLocal<>();
    private static final class Context {
        final ServerLevel level;
        final SubjectId container;
        final int slot;
        final List<ItemEntity> spawned = new ArrayList<>();
        final Map<UUID, ItemStack> expected = new HashMap<>();
        final List<FungibleResourceHandoffObserved> resources = new ArrayList<>();
        final List<ExactItemCustodyChanged> exactItems = new ArrayList<>();
        FrontierWorldState projected;
        Context(ServerLevel level, FrontierWorldState state, SubjectId container, int slot) {
            this.level = level; projected = state; this.container = container; this.slot = slot;
        }
    }
    private FrontierV3ContainerDropOperation() { }
    static void perform(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
            FrontierV3PhysicalContainer physical, int slot, ItemStack stack, double x, double y, double z) {
        var state = runtime.decodedState().orElseThrow(); var address = new InventoryCustody.ContainerSlot(physical.containerId(), slot);
        var binding = binding(state, address); var exact = state.inventory().itemAt(address.containerId(), slot).orElse(null);
        if (binding == null && exact == null) { Containers.dropItemStack(level, x, y, z, stack); return; }
        if (binding != null && !matches(stack, binding) || exact != null && !FrontierV3ExactItemPresentation.exactMatch(stack, exact))
            throw new IllegalStateException("native container drop source differs from its admitted custody");
        var journal = FrontierV3ResourceHandoffLedger.get(level);
        if (!journal.canPrepareDrop(physical.containerId(), slot))
            throw new IllegalStateException("native container drop owner cannot admit the exact source");
        if (binding != null && journal.blocksAccount(binding.accountId()))
            throw new IllegalStateException("native container drop has an unresolved earlier resource operation");
        var context = new Context(level, state, physical.containerId(), slot);
        var previous = ACTIVE.get(); ACTIVE.set(context);
        try { Containers.dropItemStack(level, x, y, z, stack.copy()); }
        finally { if (previous == null) ACTIVE.remove(); else ACTIVE.set(previous); }
        var ports = new ArrayList<FrontierV3MinecraftOperation.Participant>(); ports.add(physical.persistence().get());
        context.spawned.forEach(drop -> ports.add(FrontierV3OperationEntityParticipant.birth(drop)));
        var before = stack.copy(); var intent = new net.minecraft.nbt.CompoundTag();
        intent.putString("container", address.containerId().value()); intent.putInt("slot", slot);
        intent.put("stack", before.save(level.registryAccess()));
        var declaration = new FrontierV3OperationReceipt.Declaration(FrontierV3OperationReceipt.Protocol.CONTAINER_DROP,
                FrontierExecutionSubjects.PHYSICAL_EXECUTOR, UUID.randomUUID().toString(), FrontierV3OperationFingerprint.of(intent),
                ports.stream().map(FrontierV3MinecraftOperation.Participant::identity).collect(java.util.stream.Collectors.toSet()));
        var operations = FrontierV3OperationLedger.get(level);
        if (!operations.canPrepare(declaration)) throw new IllegalStateException("native container drop cannot discard stock under journal backpressure");
        var transaction = new FrontierV3ContainerDropTransaction(address.containerId(), slot, operations.prepare(declaration),
                context.resources, context.exactItems);
        journal.prepareDrop(transaction); journal.persist(level); operations.persist(level);
        var result = FrontierV3MinecraftOperation.apply(level, declaration, ports, () -> ItemStack.matches(stack, before)
                && context.spawned.stream().allMatch(e -> level.getEntity(e.getUUID()) == null), () -> {
            for (var drop : context.spawned) {
                var remaining = stack.copy(); var fragment = context.expected.get(drop.getUUID());
                // Vanilla/mod join hooks may refuse a fragment. Keep every uninserted
                // unit in the real source; never clear the entire stack in advance.
                if (!level.addFreshEntity(drop))
                    throw new IllegalStateException("native container drop spawn rejected: " + drop.getUUID());
                if (!ItemStack.matches(stack, remaining) || drop.isRemoved() || level.getEntity(drop.getUUID()) != drop
                        || !ItemStack.matches(drop.getItem(), fragment) || fragment.getCount() > stack.getCount())
                    throw new IllegalStateException("container drop endpoints changed during native admission");
                stack.shrink(fragment.getCount()); physical.inventory().setChanged();
            }
            return true;
        }, () -> stack.isEmpty() && !context.spawned.isEmpty()
                && context.spawned.stream().allMatch(e -> !e.isRemoved() && level.getEntity(e.getUUID()) == e
                    && ItemStack.matches(e.getItem(), context.expected.get(e.getUUID()))));
        if (!result.complete()) throw new IllegalStateException("native container drop did not complete: " + result.status() + " ticket=" + transaction.id());
        var applied = transaction.bind(operations.require(result.ticket())); journal.updateDrop(transaction, applied); journal.persist(level);
        if (!reconcile(level, runtime, applied))
            throw new IllegalStateException("native container drop awaits canonical confirmation: " + transaction.id());
    }
    static boolean addFreshEntity(Level level, Entity entity) {
        var context = ACTIVE.get();
        if (context == null || context.level != level || !(entity instanceof ItemEntity drop)) return level.addFreshEntity(entity);
        var state = context.projected;
        var address = new InventoryCustody.ContainerSlot(context.container, context.slot);
        var binding = binding(state, address);
        if (binding != null) {
            var actual = drop.getItem();
            if (!actual.getComponentsPatch().isEmpty() || !BuiltInRegistries.ITEM.getKey(actual.getItem()).toString().equals(binding.itemKind())
                    || actual.getCount() > binding.quantity()) throw new IllegalStateException("native drop fragment differs from exact source");
            var p = drop.blockPosition(); var ledger = state.inventory().fungibleResources();
            var receipt = FungiblePhysicalHandoff.departToNew(ledger, binding.accountId(), binding.authorityEpoch(), binding,
                    binding.quantity() - actual.getCount(), new SubjectId("custody:ground-" + drop.getUUID()),
                    new ResourceCustody.Ground(drop.getUUID(), new BlockPosition(p.getX(), p.getY(), p.getZ())), 1,
                    new PhysicalStackAddress.WorldEntity(drop.getUUID())).forfeitAffectedClaims(ledger);
            context.resources.add(receipt); context.projected = FungibleResourceHandoffStateSupport.apply(state, receipt);
            drop.getPersistentData().putBoolean(FrontierV3GroundItemPosition.MARKER, true);
        } else {
            var exact = state.inventory().itemAt(context.container, context.slot).orElseThrow();
            if (!FrontierV3ExactItemPresentation.exactMatch(drop.getItem(), exact))
                throw new IllegalStateException("native exact drop fragment changed the exact item");
            var receipt = new ExactItemCustodyChanged(exact.id(), address, new InventoryCustody.WorldCarrier(drop.getUUID()));
            context.exactItems.add(receipt); FrontierV3ExactItemPresentation.bindWorldCarrier(drop.getItem(), drop.getUUID());
            context.projected = state.withInventory(state.inventory().moveObservedItem(exact.id(), receipt.from(), receipt.to()));
        }
        // The native splitting/randomization algorithm operates on a private stack.
        // Return success to its planner; the actual insertion happens only after prepare.
        context.spawned.add(drop); context.expected.put(drop.getUUID(), drop.getItem().copy()); return true;
    }
    static boolean reconcile(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, FrontierV3ContainerDropTransaction transaction) {
        var operations = FrontierV3OperationLedger.get(level); var journal = FrontierV3ResourceHandoffLedger.get(level);
        var current = operations.find(transaction.id()).orElse(null);
        if (current == null) {
            if (!operations.prunable(transaction.operation().nativeReceipt(transaction.operation().declaration().participants().iterator().next()))) return false;
            // Positive retirement with a PREPARED owner link is the explicit
            // unattempted-cancellation cut. Neither cut permits another spawn.
        } else {
            if (!FrontierV3MinecraftOperation.recoverOutcome(level, current.ticket())) return false;
            current = operations.require(current.ticket());
            var linked = transaction.bind(current); journal.updateDrop(transaction, linked); journal.persist(level); transaction = linked;
            if (!FrontierV3OperationConfirmation.submit(level, runtime, current.ticket(), transaction.outcome())) return false;
        }
        journal.retireDrop(transaction); journal.persist(level); return true;
    }
    private static PhysicalStackBinding binding(FrontierWorldState state, InventoryCustody.ContainerSlot slot) {
        var address = new PhysicalStackAddress.ContainerSlot(slot);
        return state.inventory().fungibleResources().bindings().values().stream().filter(b -> b.address().equals(address)).findFirst().orElse(null);
    }
    private static boolean matches(ItemStack stack, PhysicalStackBinding binding) {
        return !stack.isEmpty() && stack.getComponentsPatch().isEmpty() && stack.getCount() == binding.quantity()
                && BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(binding.itemKind());
    }
}
