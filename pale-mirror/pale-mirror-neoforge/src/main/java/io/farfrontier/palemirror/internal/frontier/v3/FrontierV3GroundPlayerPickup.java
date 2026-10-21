package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import java.util.*;

/** Wraps the causal Vanilla inventory add. It never discovers ownership by matching player stacks. */
public final class FrontierV3GroundPlayerPickup {
    public static boolean add(ItemEntity entity, Inventory inventory, ItemStack stack) {
        if (!(entity.level() instanceof ServerLevel level) || !FrontierV3PhysicalWorld.isPhysical(level)) return inventory.add(stack);
        var runtime = FrontierV3ServerLifecycle.runtimeFor(level.getServer());
        if (runtime == null || runtime.status().kind() != FrontierV3RuntimeStatus.Kind.ACTIVE) return inventory.add(stack);
        return add(level, entity, inventory, stack, runtime);
    }
    static boolean add(ServerLevel level, ItemEntity entity, Inventory inventory, ItemStack stack,
            FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        var journal = FrontierV3ResourceHandoffLedger.get(level);
        // A newly spawned managed drop can still precede its canonical account.
        // Absence of that account is not permission to bypass its causal witness.
        if (journal.blocks(entity.getUUID())) return false;
        var state = runtime.decodedState().orElseThrow(); var resources = state.inventory().fungibleResources();
        var account = resources.accounts().values().stream().filter(value -> value.custody() instanceof ResourceCustody.Ground ground
                && ground.entityId().equals(entity.getUUID())).findFirst().orElse(null);
        if (account == null) return inventory.add(stack);
        if (journal.blocksAccount(account.id())) return false;
        var bindings = resources.bindings().values().stream().filter(value -> value.accountId().equals(account.id())).toList();
        if (bindings.size() != 1 || stack != entity.getItem() || stack.getComponentsPatch().size() != 0
                || stack.getCount() != bindings.getFirst().quantity()
                || !BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(bindings.getFirst().itemKind()))
            throw new IllegalStateException("ground player pickup lost its exact current native source: " + account.id());
        int before = stack.getCount(); int capacity = capacity(inventory, stack);
        int amount = inventory.player.hasInfiniteMaterials() ? before : Math.min(before, capacity);
        if (amount == 0) return inventory.add(stack);
        if (!(inventory.player instanceof net.minecraft.server.level.ServerPlayer player)) return false;
        var source = amount == before
                ? FrontierV3OperationEntityColumns.current(level, entity.chunkPosition()).orElse(null)
                : FrontierV3OperationEntityParticipant.nativeObject(entity);
        if (source == null) return false; // Native column read is pending; no witnessed mutation yet.
        var ports = List.of(source, FrontierV3OperationPlayers.current(player));
        var receipt = GroundResourcePlayerWithdrawal.prepare(state, account.id(), inventory.player.getUUID(), UUID.randomUUID(), amount);
        journal.prepareLoss(receipt); journal.persist(level); // Before either the player inventory or source stack changes.
        var declaration = new FrontierV3OperationReceipt.Declaration(FrontierV3OperationReceipt.Protocol.PLAYER_GROUND_PICKUP,
                receipt.economicOwner(), receipt.witnessId().toString(), FrontierV3OperationConfirmation.fingerprint(receipt),
                ports.stream().map(FrontierV3MinecraftOperation.Participant::identity).collect(java.util.stream.Collectors.toSet()));
        var added = new boolean[1];
        var result = FrontierV3MinecraftOperation.apply(level, declaration, ports,
                () -> !entity.isRemoved() && entity.getItem() == stack && stack.getCount() == before,
                () -> {
                    added[0] = inventory.add(stack);
                    if (stack.getCount() != before - amount) return false;
                    if (stack.isEmpty()) FrontierV3GroundItemDestruction.discardConsumed(entity);
                    return true;
                }, () -> stack.getCount() == before - amount && (amount != before || entity.isRemoved()));
        if (result.status() == FrontierV3MinecraftOperation.Status.WAITING_DURABILITY
                || result.status() == FrontierV3MinecraftOperation.Status.NOT_APPLIED) {
            journal.retireLoss(receipt); journal.persist(level); return false;
        }
        if (result.complete()) reconcile(level, runtime, receipt);
        return added[0];
    }
    /** Recovery consumes causal operation proof, never a matching source/player count. */
    static boolean reconcile(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
            GroundResourcePlayerWithdrawal receipt) {
        var journal = FrontierV3ResourceHandoffLedger.get(level); var ledger = FrontierV3OperationLedger.get(level);
        var linked = journal.lossOperation(receipt).orElse(null);
        var operation = linked == null ? ledger.find(FrontierV3OperationReceipt.Protocol.PLAYER_GROUND_PICKUP,
                receipt.economicOwner(), receipt.witnessId().toString()).orElse(null) : ledger.find(linked.ticket()).orElse(null);
        if (operation == null) {
            // A positive retirement fence is valid only with the owner's retained exact
            // operation identity. Missing operation/count equality alone proves nothing.
            if (linked == null || !ledger.prunable(linked.nativeReceipt(linked.declaration().participants().iterator().next()))) return false;
        } else {
            if (!operation.declaration().fingerprint().equals(FrontierV3OperationConfirmation.fingerprint(receipt)))
                throw new IllegalStateException("ground pickup changed its durable native intent");
            if (!operation.applied().equals(operation.declaration().participants())) {
                var player = level.getServer().getPlayerList().getPlayer(receipt.playerId());
                if (player == null) return false;
                FrontierV3MinecraftOperation.Participant source;
                if (receipt.remaining().isEmpty()) {
                    var declared = operation.declaration().participants().stream()
                            .filter(p -> p.medium() == FrontierV3OperationReceipt.Medium.ENTITY_COLUMN).findFirst().orElseThrow();
                    source = FrontierV3OperationEntityColumns.current(level,
                            new net.minecraft.world.level.ChunkPos(Long.parseLong(declared.identity()))).orElse(null);
                } else {
                    var entity = level.getEntity(receipt.source().entityId());
                    source = entity == null || entity.isRemoved() ? null : FrontierV3OperationEntityParticipant.nativeObject(entity);
                }
                if (source == null) return false;
                var ports = List.of(source, FrontierV3OperationPlayers.current(player));
                if (!ports.stream().map(FrontierV3MinecraftOperation.Participant::identity).collect(java.util.stream.Collectors.toSet())
                        .equals(operation.declaration().participants())) {
                    FrontierV3OperationDiagnostics.result(level, operation, FrontierV3MinecraftOperation.Status.AMBIGUOUS); return false;
                }
                // Exact native receipts attest the completed callback. Subsequent player
                // inventory use cannot become permission to repeat or undo that callback.
                var observed = FrontierV3MinecraftOperation.observe(level, operation.declaration(), ports, () -> true).orElseThrow();
                if (!observed.complete()) return false;
                operation = ledger.require(operation.ticket());
            }
            journal.bindLossOperation(receipt, operation); journal.persist(level);
            if (!FrontierV3OperationConfirmation.submit(level, runtime, operation.ticket(), receipt)) return false;
        }
        journal.retireLoss(receipt); journal.persist(level); return true;
    }
    /** Ordinary Vanilla add(-1): main slots, matching offhand, never an empty offhand or armour slot. */
    static int capacity(Inventory inventory, ItemStack source) {
        int result = 0;
        for (var destination : inventory.items) result = Math.addExact(result, capacity(inventory, source, destination, true));
        result = Math.addExact(result, capacity(inventory, source, inventory.getItem(40), false));
        return result;
    }
    private static int capacity(Inventory inventory, ItemStack source, ItemStack destination, boolean emptyAllowed) {
        if (destination.isEmpty()) return emptyAllowed ? inventory.getMaxStackSize(source) : 0;
        return destination.isStackable() && ItemStack.isSameItemSameComponents(source, destination)
                ? Math.max(0, inventory.getMaxStackSize(destination) - destination.getCount()) : 0;
    }
    private FrontierV3GroundPlayerPickup() { }
}
