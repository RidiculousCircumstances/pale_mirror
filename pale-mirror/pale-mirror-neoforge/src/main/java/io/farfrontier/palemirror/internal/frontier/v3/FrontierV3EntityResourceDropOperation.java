package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.model.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import java.util.*;

/** Native slot -> item-entity transfer. Resource owners supply the admitted slot and
 * canonical consequence; the adapter never classifies their work or death state. */
final class FrontierV3EntityResourceDropOperation {
    private FrontierV3EntityResourceDropOperation() { }
    static ResourceCustody.Ground ground(ItemEntity drop) {
        var position = drop.blockPosition();
        return new ResourceCustody.Ground(drop.getUUID(), new BlockPosition(position.getX(), position.getY(), position.getZ()));
    }
    static void perform(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
            Mob body, ActorItemSlot slot, ItemEntity drop, FrontierPayload outcome) {
        var before = FrontierV3ActorResourceSlots.get(body, slot).copy();
        if (before.isEmpty() || !ItemStack.matches(before, drop.getItem()))
            throw new IllegalArgumentException("resource drop differs from the admitted source slot");
        perform(level, runtime, body, slot, before, drop, outcome);
    }
    /** The resource owner may change custody metadata on its detached destination.
     * Both images are retained for validation; item kind and quantity cannot change. */
    static void perform(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
            Mob body, ActorItemSlot slot, ItemStack sourceWitness, ItemEntity drop, FrontierPayload outcome) {
        var before = sourceWitness.copy(); var after = drop.getItem().copy();
        if (before.isEmpty() || !ItemStack.isSameItem(before, after) || before.getCount() != after.getCount()
                || !ItemStack.matches(before, FrontierV3ActorResourceSlots.get(body, slot)))
            throw new IllegalArgumentException("resource drop changed the admitted native item or quantity");
        var source = FrontierV3OperationEntityParticipant.nativeObject(body);
        var destination = FrontierV3OperationEntityParticipant.birth(drop);
        var ports = List.of(source, destination);
        var declaration = new FrontierV3OperationReceipt.Declaration(FrontierV3OperationReceipt.Protocol.ENTITY_RESOURCE_DROP,
                FrontierExecutionSubjects.PHYSICAL_EXECUTOR, UUID.randomUUID().toString(),
                FrontierV3OperationConfirmation.fingerprint(outcome), Set.of(source.identity(), destination.identity()));
        var journal = FrontierV3ResourceHandoffLedger.get(level); var operations = FrontierV3OperationLedger.get(level);
        if (!journal.canPrepareEntityDrop(drop.getUUID()) || !operations.canPrepare(declaration))
            throw new IllegalStateException("resource drop cannot discard stock under journal backpressure");
        var transaction = new FrontierV3EntityResourceDropTransaction(source.identity(), destination.identity(), operations.prepare(declaration), outcome);
        journal.prepareEntityDrop(transaction); journal.persist(level); operations.persist(level);
        var result = FrontierV3MinecraftOperation.applyBatch(level, List.of(new FrontierV3MinecraftOperation.Request(declaration,
                ports,
                () -> ItemStack.matches(before, FrontierV3ActorResourceSlots.get(body, slot)) && level.getEntity(drop.getUUID()) == null,
                FrontierV3NativeEffect.refusing(() -> {
                    // A native join refusal must not eat the source. Admission callbacks
                    // run synchronously; revalidate before clearing after a successful birth.
                    if (!level.addFreshEntity(drop)) return false;
                    if (!ItemStack.matches(before, FrontierV3ActorResourceSlots.get(body, slot))
                            || level.getEntity(drop.getUUID()) != drop || drop.isRemoved() || !ItemStack.matches(after, drop.getItem()))
                        throw new IllegalStateException("resource endpoints changed during native drop admission");
                    FrontierV3ActorResourceSlots.set(body, slot, ItemStack.EMPTY);
                    return true;
                }, () -> !drop.isAddedToLevel() && level.getEntity(drop.getUUID()) == null
                        && ItemStack.matches(before, FrontierV3ActorResourceSlots.get(body, slot))),
                () -> FrontierV3ActorResourceSlots.get(body, slot).isEmpty() && level.getEntity(drop.getUUID()) == drop
                        && !drop.isRemoved() && ItemStack.matches(after, drop.getItem())))).getFirst();
        if (!result.complete()) throw new IllegalStateException("resource drop did not complete: " + result.status() + " ticket=" + transaction.id());
        var applied = transaction.bind(operations.require(result.ticket()));
        journal.updateEntityDrop(transaction, applied); journal.persist(level);
        if (!reconcile(level, runtime, applied))
            throw new IllegalStateException("resource drop awaits canonical confirmation: " + transaction.id());
    }
    static boolean reconcileOne(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        for (var transaction : FrontierV3ResourceHandoffLedger.get(level).entityDrops())
            if (reconcile(level, runtime, transaction)) return true;
        return false;
    }
    static boolean reconcile(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
            FrontierV3EntityResourceDropTransaction transaction) {
        var operations = FrontierV3OperationLedger.get(level); var journal = FrontierV3ResourceHandoffLedger.get(level);
        var current = operations.find(transaction.id()).orElse(null);
        if (current == null) {
            if (!operations.prunable(transaction.operation().nativeReceipt(transaction.source()))) return false;
            // Positive retirement alone closes a cancelled PREPARED or completed
            // APPLIED owner cut. Neither authorizes another spawn or canonical command.
        } else {
            transaction.bind(current); // Validate identity even before examining native markers.
            if (current.cancelled()) {
                // Positive refusal leaves custody unchanged. Never submit the retained
                // successful-transfer consequence for a cancelled operation.
                operations.closeOwner(current.ticket()); operations.retire(current.ticket()); operations.persist(level);
                journal.retireEntityDrop(transaction); journal.persist(level); return true;
            }
            if (!FrontierV3MinecraftOperation.recoverOutcome(level, current.ticket())) return false;
            current = operations.require(current.ticket());
            var applied = transaction.bind(current); journal.updateEntityDrop(transaction, applied); journal.persist(level); transaction = applied;
            if (!FrontierV3OperationConfirmation.submit(level, runtime, current.ticket(), transaction.outcome())) return false;
        }
        journal.retireEntityDrop(transaction); journal.persist(level); return true;
    }
}
