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
        journal.prepareLoss(loss); journal.persist(level); return true;
    }
    public static void after(Entity entity) {
        if (!(entity instanceof ItemEntity) || !entity.isRemoved() || !(entity.level() instanceof ServerLevel level)
                || !FrontierV3PhysicalWorld.isPhysical(level)) return;
        var runtime = FrontierV3ServerLifecycle.runtimeFor(level.getServer());
        if (runtime == null || runtime.status().kind() != FrontierV3RuntimeStatus.Kind.ACTIVE) return;
        for (var loss : FrontierV3ResourceHandoffLedger.get(level).losses())
            if (loss instanceof GroundResourceDestroyed && loss.source().entityId().equals(entity.getUUID())) reconcile(level, runtime, loss);
    }
    static boolean reconcileOne(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        for (var loss : FrontierV3ResourceHandoffLedger.get(level).losses()) {
            var position = loss.source().position();
            if (!level.hasChunkAt(new BlockPos(position.x(), position.y(), position.z()))) continue;
            var entity = level.getEntity(loss.source().entityId());
            if (loss instanceof GroundResourcePlayerWithdrawal pickup && !matchesPickupPostimage(entity, pickup)) continue;
            if (loss instanceof GroundResourceDestroyed && entity != null && !entity.isRemoved()) continue;
            if (reconcile(level, runtime, loss)) return true;
        }
        return false;
    }
    static boolean reconcile(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, GroundStockExit loss) {
        var state = runtime.decodedState().orElseThrow(); var journal = FrontierV3ResourceHandoffLedger.get(level);
        var account = state.inventory().fungibleResources().accounts().get(loss.sourceAccountId());
        boolean applied = account == null;
        if (loss instanceof GroundResourcePlayerWithdrawal pickup && account != null) {
            var expected = new HashMap<>(pickup.beforeLots());
            pickup.departedLots().forEach((id, quantity) -> { int left = expected.getOrDefault(id, 0) - quantity; if (left == 0) expected.remove(id); else expected.put(id, left); });
            applied = account.custody().equals(pickup.source()) && account.lotQuantities().equals(expected)
                    && Collections.disjoint(account.claimQuantities().keySet(), pickup.forfeitedClaims());
        }
        if (applied) {
            journal.retireLoss(loss); journal.persist(level); return true;
        }
        if (!(FrontierV3CommandSubmission.submit(runtime, "ground-causal-stock-exit", loss.witnessId().toString(), loss) instanceof CommandResult.Accepted)) return false;
        journal.retireLoss(loss); journal.persist(level); return true;
    }
    private static boolean matchesPickupPostimage(Entity entity, GroundResourcePlayerWithdrawal receipt) {
        if (receipt.remaining().isEmpty()) return entity == null || entity.isRemoved()
                || entity instanceof ItemEntity item && item.getItem().isEmpty();
        if (!(entity instanceof ItemEntity item) || item.isRemoved()) return false;
        var expected = receipt.remaining().getFirst(); var stack = item.getItem();
        return stack.getCount() == expected.quantity() && stack.getComponentsPatch().isEmpty()
                && BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(expected.itemKind());
    }
    private FrontierV3GroundItemDestruction() { }
}
