package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import java.util.*;

/** Exact removal callback. Chunk departure, a witnessed merge and courier pickup are not destruction. */
public final class FrontierV3GroundItemDestruction {
    private static final ThreadLocal<UUID> CONSUMED_REMOVAL = new ThreadLocal<>();
    private static final ThreadLocal<UUID> NATIVE_REMOVAL = new ThreadLocal<>();
    /** Only an effect executor may remove the empty source of its retained causal witness. */
    public static void discardConsumed(ItemEntity entity) {
        if (!entity.getItem().isEmpty()) throw new IllegalArgumentException("consumed ground source is not empty");
        if (CONSUMED_REMOVAL.get() != null) throw new IllegalStateException("nested ground source removal");
        CONSUMED_REMOVAL.set(entity.getUUID());
        try { entity.discard(); } finally { CONSUMED_REMOVAL.remove(); }
    }
    public static boolean before(Entity nativeEntity, Entity.RemovalReason reason) {
        if (!(nativeEntity instanceof ItemEntity entity) || entity.isRemoved()
                || reason != Entity.RemovalReason.KILLED && reason != Entity.RemovalReason.DISCARDED
                || !(entity.level() instanceof ServerLevel level) || !FrontierV3PhysicalWorld.isPhysical(level)) return true;
        var runtime = FrontierV3ServerLifecycle.runtimeFor(level.getServer());
        if (runtime == null || runtime.status().kind() != FrontierV3RuntimeStatus.Kind.ACTIVE) return true;
        if (entity.getUUID().equals(NATIVE_REMOVAL.get())) return true;
        return before(level, entity, reason, runtime);
    }
    /** A false result cancels the outer call: an admitted destruction has already
     * executed the original native method once inside the common operation. */
    static boolean before(ServerLevel level, ItemEntity entity, Entity.RemovalReason reason,
            FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        if (reason != Entity.RemovalReason.KILLED && reason != Entity.RemovalReason.DISCARDED || entity.isRemoved()) return true;
        var journal = FrontierV3ResourceHandoffLedger.get(level);
        if (journal.blocks(entity.getUUID())) return reason == Entity.RemovalReason.DISCARDED
                && entity.getItem().isEmpty() && entity.getUUID().equals(CONSUMED_REMOVAL.get());
        var state = runtime.decodedState().orElseThrow(); var resources = state.inventory().fungibleResources();
        var account = resources.accounts().values().stream().filter(value -> value.custody() instanceof ResourceCustody.Ground ground
                && ground.entityId().equals(entity.getUUID())).findFirst().orElse(null);
        if (account == null) return true;
        if (journal.blocksAccount(account.id())) return false;
        var bindings = resources.bindings().values().stream().filter(binding -> binding.accountId().equals(account.id())).toList();
        if (bindings.size() != 1) throw new IllegalStateException("ground destruction lost its exact stack binding: " + account.id());
        var binding = bindings.getFirst(); var stack = entity.getItem();
        if (stack.isEmpty() || stack.getCount() != binding.quantity() || !stack.getComponentsPatch().isEmpty()
                || !BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(binding.itemKind())) {
            // Recognized pickup/merge effects have their own witness above. Unclassified
            // native drift cannot erase this body and leave canonical stock behind.
            io.farfrontier.palemirror.PaleMirrorMod.LOGGER.warn(
                    "PMV3_GROUND_REMOVAL_HELD entity={} account={} reason={} detail=unwitnessed-stack-drift expectedKind={} expectedQuantity={} actual={}",
                    entity.getUUID(), account.id().value(), reason, binding.itemKind(), binding.quantity(), stack);
            return false;
        }
        var owners = account.lotQuantities().keySet().stream().map(id -> resources.lots().get(id).economicOwnerId()).distinct().toList();
        if (owners.size() != 1) throw new IllegalStateException("ground destruction requires its exact declared resource owner");
        var loss = new GroundResourceDestroyed(account.id(), (ResourceCustody.Ground) account.custody(), binding.authorityEpoch(),
                owners.getFirst(), UUID.randomUUID(), reason.name(), account.lotQuantities(), account.claimQuantities().keySet());
        try { loss.apply(state, loss.economicOwner()); }
        catch (IllegalArgumentException pending) {
            io.farfrontier.palemirror.PaleMirrorMod.LOGGER.warn("PMV3_GROUND_REMOVAL_HELD entity={} account={} reason={} detail={}",
                    entity.getUUID(), account.id().value(), reason, pending.getMessage());
            return false;
        }
        // setRemoved cannot suspend halfway through removal. Preserve this column's
        // ordered native receipt bag before entering the callback (no terrain load).
        FrontierV3OperationEntityColumns.forRemoval(level, entity.chunkPosition());
        var port = FrontierV3OperationEntityColumns.current(level, entity.chunkPosition()).orElseThrow();
        journal.prepareLoss(loss); journal.persist(level);
        var declaration = new FrontierV3OperationReceipt.Declaration(FrontierV3OperationReceipt.Protocol.GROUND_DESTRUCTION,
                loss.economicOwner(), loss.witnessId().toString(), FrontierV3OperationConfirmation.fingerprint(loss), Set.of(port.identity()));
        var result = FrontierV3MinecraftOperation.apply(level, declaration, List.of(port), () -> !entity.isRemoved(), () -> {
            var parent = NATIVE_REMOVAL.get(); NATIVE_REMOVAL.set(entity.getUUID());
            try { entity.setRemoved(reason); } finally {
                if (parent == null) NATIVE_REMOVAL.remove(); else NATIVE_REMOVAL.set(parent);
            }
            return entity.isRemoved();
        }, () -> entity.isRemoved() && entity.getRemovalReason() == reason);
        if (result.status() == FrontierV3MinecraftOperation.Status.WAITING_DURABILITY
                || result.status() == FrontierV3MinecraftOperation.Status.NOT_APPLIED) {
            journal.retireLoss(loss); journal.persist(level);
        } else if (result.complete()) reconcile(level, runtime, loss);
        return false;
    }
    static boolean reconcileOne(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        for (var loss : FrontierV3ResourceHandoffLedger.get(level).losses()) {
            if (loss instanceof GroundResourcePlayerWithdrawal pickup) {
                if (FrontierV3GroundPlayerPickup.reconcile(level, runtime, pickup)) return true;
                continue;
            }
            var position = loss.source().position();
            if (!level.hasChunkAt(new BlockPos(position.x(), position.y(), position.z()))) continue;
            if (reconcile(level, runtime, loss)) return true;
        }
        return false;
    }
    static boolean reconcile(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, GroundStockExit loss) {
        if (loss instanceof GroundResourcePlayerWithdrawal pickup) return FrontierV3GroundPlayerPickup.reconcile(level, runtime, pickup);
        if (!(loss instanceof GroundResourceDestroyed)) throw new IllegalArgumentException("undeclared ground exit producer");
        return FrontierV3GroundLossOperation.reconcile(level, runtime, loss, FrontierV3OperationReceipt.Protocol.GROUND_DESTRUCTION);
    }
    private FrontierV3GroundItemDestruction() { }
}
