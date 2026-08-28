package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.CauseChain;
import io.farfrontier.palemirror.frontier.v3.api.CheckpointImage;
import io.farfrontier.palemirror.frontier.v3.api.CommandId;
import io.farfrontier.palemirror.frontier.v3.api.CommandResult;
import io.farfrontier.palemirror.frontier.v3.api.FrontierCommand;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.ExactItemCustodyChanged;
import io.farfrontier.palemirror.frontier.v3.model.ExactItemStack;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldRuntimeDefinition;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.model.HiveOrgan;
import io.farfrontier.palemirror.frontier.v3.model.ContainerSurfaceStatus;
import io.farfrontier.palemirror.frontier.v3.model.InventoryCustody;
import io.farfrontier.palemirror.frontier.v3.model.InventoryConflict;
import io.farfrontier.palemirror.frontier.v3.model.InventoryConflictKind;
import io.farfrontier.palemirror.frontier.v3.model.InventoryConflictObserved;
import io.farfrontier.palemirror.frontier.v3.model.ResourceDeposited;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Bounded loaded-chunk observation of exact items crossing an owned hive STORE chest and a
 * player's real inventory. It never writes a chest or player inventory: unverifiable drift
 * becomes a bounded durable conflict rather than being adopted or repaired.
 */
final class FrontierV3InventoryObservationExecutor {
    static final String HOPPER_CARRIER_ID_KEY = "pale_mirror_frontier_v3_hopper_carrier";
    private FrontierV3InventoryObservationExecutor() { }

    static void tick(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        FrontierWorldState state = state(runtime);
        if (state == null) return;
        FrontierV3HopperCarrierLedger hopperCarriers = FrontierV3HopperCarrierLedger.get(level);
        for (StoreChest store : stores(state)) {
            if (!level.hasChunkAt(store.position())) continue;
            if (!(level.getBlockEntity(store.position()) instanceof ChestBlockEntity chest)
                    || !store.containerId().value().equals(chest.getPersistentData().getString(FrontierV3CargoHandoffExecutor.CONTAINER_ID_KEY))) continue;
            if (observeOne(level, runtime, state, hopperCarriers, store, chest)) return;
        }
    }

    static boolean observeOne(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                              FrontierWorldState state, FrontierV3HopperCarrierLedger hopperCarriers, StoreChest store, ChestBlockEntity chest) {
        for (int slot = 0; slot < chest.getContainerSize(); slot++) {
            InventoryCustody.ContainerSlot custody = new InventoryCustody.ContainerSlot(store.containerId(), slot);
            Optional<ExactItemStack> canonical = state.inventory().itemAt(store.containerId(), slot);
            ItemStack actual = chest.getItem(slot);
            if (canonical.isPresent()) {
                ExactItemStack expected = canonical.orElseThrow();
                if (FrontierV3CargoHandoffExecutor.exactMatch(actual, expected)) continue;
                List<ItemEntity> carriers = nearbyCarriers(level, chest.getBlockPos(), expected);
                if (actual.isEmpty() && carriers.size() == 1) {
                    ItemEntity carrier = carriers.getFirst();
                    FrontierV3CargoHandoffExecutor.bindWorldCarrier(carrier.getItem(), carrier.getUUID());
                    carrier.setItem(carrier.getItem());
                    submit(runtime, expected.id(), custody, new InventoryCustody.WorldCarrier(carrier.getUUID()));
                    return true;
                }
                List<HopperCandidate> hoppers = nearbyHoppers(level, chest.getBlockPos(), expected);
                if (actual.isEmpty() && hoppers.size() == 1) {
                    HopperCandidate hopper = hoppers.getFirst(); HopperCarrierBinding binding = bindHopperCarrier(hopperCarriers, hopper.hopper(), hopper.slot());
                    if (binding.status() == HopperCarrierStatus.CONFLICT) {
                        recordConflict(runtime, state, expected.id(), store.containerId(), slot, InventoryConflictKind.FOREIGN_OR_DUPLICATE);
                        return true;
                    }
                    submit(runtime, expected.id(), custody, new InventoryCustody.WorldCarrier(binding.carrierId()));
                    return true;
                }
                List<ServerPlayer> holders = playersHolding(level, expected);
                if (actual.isEmpty() && holders.size() == 1) {
                    submit(runtime, expected.id(), custody, new InventoryCustody.Player(holders.getFirst().getUUID()));
                    return true;
                }
                recordConflict(runtime, state, expected.id(), store.containerId(), slot,
                        actual.isEmpty() ? InventoryConflictKind.MISSING : InventoryConflictKind.FOREIGN_OR_DUPLICATE);
                return true;
            }
            if (actual.isEmpty()) continue;
            Optional<SubjectId> taggedItem = FrontierV3CargoHandoffExecutor.itemId(actual);
            if (taggedItem.isEmpty() || FrontierV3CargoHandoffExecutor.pendingIngress(actual) && !state.inventory().items().containsKey(taggedItem.orElseThrow())) {
                deposit(runtime, state, store.containerId(), slot, actual);
                chest.setChanged();
                return true;
            }
            ExactItemStack playerItem = playerOwnedExact(state, actual).orElse(null);
            ExactItemStack carrierItem = worldCarrierOwnedExact(state, actual).orElse(null);
            if (carrierItem != null) {
                submit(runtime, carrierItem.id(), carrierItem.custody(), custody);
                return true;
            }
            if (playerItem == null || !(playerItem.custody() instanceof InventoryCustody.Player) || !playersHolding(level, playerItem).isEmpty()) {
                recordConflict(runtime, state, store.containerId(), store.containerId(), slot, InventoryConflictKind.FOREIGN_OR_DUPLICATE);
                return true;
            }
            submit(runtime, playerItem.id(), playerItem.custody(), custody);
            return true;
        }
        return false;
    }

    private static Optional<ExactItemStack> playerOwnedExact(FrontierWorldState state, ItemStack stack) {
        return state.inventory().items().values().stream().filter(item -> item.custody() instanceof InventoryCustody.Player)
                .filter(item -> FrontierV3CargoHandoffExecutor.exactMatch(stack, item)).findFirst();
    }
    private static Optional<ExactItemStack> worldCarrierOwnedExact(FrontierWorldState state, ItemStack stack) {
        Optional<java.util.UUID> carrierId = FrontierV3CargoHandoffExecutor.worldCarrierId(stack);
        if (carrierId.isEmpty()) return Optional.empty();
        InventoryCustody.WorldCarrier custody = new InventoryCustody.WorldCarrier(carrierId.orElseThrow());
        return state.inventory().items().values().stream().filter(item -> item.custody().equals(custody))
                .filter(item -> FrontierV3CargoHandoffExecutor.exactMatch(stack, item)).findFirst();
    }
    private static List<ItemEntity> nearbyCarriers(ServerLevel level, BlockPos source, ExactItemStack expected) {
        return level.getEntitiesOfClass(ItemEntity.class, new AABB(source).inflate(4.0D), entity -> FrontierV3CargoHandoffExecutor.exactMatch(entity.getItem(), expected))
                .stream().sorted(Comparator.comparing(ItemEntity::getUUID)).toList();
    }
    private static List<HopperCandidate> nearbyHoppers(ServerLevel level, BlockPos source, ExactItemStack expected) {
        return java.util.stream.Stream.of(source.above(), source.below(), source.north(), source.south(), source.east(), source.west())
                .filter(level::hasChunkAt).map(level::getBlockEntity).filter(HopperBlockEntity.class::isInstance).map(HopperBlockEntity.class::cast)
                .flatMap(hopper -> java.util.stream.IntStream.range(0, hopper.getContainerSize()).mapToObj(slot -> new HopperCandidate(hopper, slot, hopper.getItem(slot))))
                .filter(candidate -> FrontierV3CargoHandoffExecutor.exactMatch(candidate.stack(), expected))
                .sorted(Comparator.comparingLong(candidate -> candidate.hopper().getBlockPos().asLong())).toList();
    }
    static HopperCarrierBinding bindHopperCarrier(FrontierV3HopperCarrierLedger ledger, HopperBlockEntity hopper, int slot) {
        ItemStack stack = hopper.getItem(slot);
        if (stack.isEmpty()) throw new IllegalArgumentException("cannot bind an empty hopper slot");
        Optional<UUID> stackCarrier = FrontierV3CargoHandoffExecutor.worldCarrierId(stack);
        UUID id = hopper.getPersistentData().hasUUID(HOPPER_CARRIER_ID_KEY) ? hopper.getPersistentData().getUUID(HOPPER_CARRIER_ID_KEY) : null;
        if (id == null) {
            if (stackCarrier.isPresent()) return new HopperCarrierBinding(stackCarrier.orElseThrow(), HopperCarrierStatus.CONFLICT);
            id = UUID.randomUUID();
        } else if (stackCarrier.isPresent() && !id.equals(stackCarrier.orElseThrow())) {
            return new HopperCarrierBinding(id, HopperCarrierStatus.CONFLICT);
        }
        if (!ledger.claim(id, hopper.getBlockPos())) return new HopperCarrierBinding(id, HopperCarrierStatus.CONFLICT);
        boolean current = stackCarrier.isPresent();
        hopper.getPersistentData().putUUID(HOPPER_CARRIER_ID_KEY, id);
        if (!current) { FrontierV3CargoHandoffExecutor.bindWorldCarrier(stack, id); hopper.setItem(slot, stack); }
        hopper.setChanged(); return new HopperCarrierBinding(id, current ? HopperCarrierStatus.CURRENT : HopperCarrierStatus.APPLIED);
    }
    private static List<ServerPlayer> playersHolding(ServerLevel level, ExactItemStack expected) {
        return level.players().stream().filter(player -> hasExactItem(player, expected)).sorted(Comparator.comparing(ServerPlayer::getUUID)).toList();
    }
    static boolean hasExactItem(ServerPlayer player, ExactItemStack expected) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            if (FrontierV3CargoHandoffExecutor.exactMatch(player.getInventory().getItem(slot), expected)) return true;
        }
        return false;
    }
    private static List<StoreChest> stores(FrontierWorldState state) {
        return state.inventory().surfaces().values().stream().filter(surface -> surface.status() == ContainerSurfaceStatus.ACTIVE)
                .map(surface -> new StoreChest(new BlockPos(surface.position().x(), surface.position().y(), surface.position().z()), surface.containerId()))
                .sorted(Comparator.comparing(StoreChest::containerId)).toList();
    }
    private static void submit(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, SubjectId itemId,
                               InventoryCustody from, InventoryCustody to) {
        CheckpointImage checkpoint = runtime.checkpointImage().orElseThrow(() -> new IllegalStateException("v3 runtime is inactive"));
        String direction = from instanceof InventoryCustody.ContainerSlot ? "outbound" : "inbound";
        CommandId commandId = new CommandId("executor:item-" + direction + "-" + itemId.value().replace(':', '-') + "-r" + checkpoint.revision().value());
        CommandResult result = runtime.submit(new FrontierCommand(1, commandId, checkpoint.worldId(), checkpoint.revision(), checkpoint.instant(),
                FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR, CauseChain.root(commandId), new ExactItemCustodyChanged(itemId, from, to)))
                .orElseThrow(() -> new IllegalStateException("v3 runtime is inactive"));
        if (!(result instanceof CommandResult.Accepted)) throw new IllegalStateException("exact item custody observation was rejected: " + result);
    }
    /**
     * Admits one ordinary player-provided stack as one exact canonical resource.  The marker is
     * deliberately written before the WAL command: should the JVM stop between those operations,
     * the next observation retries that same pending identity instead of fabricating a second
     * stack. A tagged-but-unknown stack without the pending marker remains visible conflict evidence.
     */
    private static void deposit(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, FrontierWorldState state, SubjectId container, int slot, ItemStack actual) {
        SubjectId itemId = FrontierV3CargoHandoffExecutor.itemId(actual).orElseGet(() -> {
            SubjectId created = new SubjectId("item:ingress-" + UUID.randomUUID());
            FrontierV3CargoHandoffExecutor.bindExactItemId(actual, created);
            FrontierV3CargoHandoffExecutor.markPendingIngress(actual);
            return created;
        });
        String kind = BuiltInRegistries.ITEM.getKey(actual.getItem()).toString();
        var target = state.inventory().containers().get(container);
        if (target == null) throw new IllegalArgumentException("physical deposit targets an unknown exact container");
        ExactItemStack deposited = new ExactItemStack(itemId, target.ownerId(), kind, actual.getCount(), new InventoryCustody.ContainerSlot(container, slot));
        FrontierV3CommandSubmission.submit(runtime, "resource-deposit", itemId.value(), new ResourceDeposited(deposited));
        FrontierV3CargoHandoffExecutor.clearPendingIngress(actual);
    }
    private static void recordConflict(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, FrontierWorldState state, SubjectId subject,
                                       SubjectId container, int slot, InventoryConflictKind kind) {
        SubjectId id = new SubjectId("conflict:inventory-" + subject.value().replace(':', '-') + "-" + container.value().replace(':', '-') + "-" + slot + "-k" + kind.ordinal());
        InventoryConflict conflict = new InventoryConflict(id, subject, container, slot, kind);
        if (state.inventory().conflicts().containsKey(id)) return;
        CheckpointImage checkpoint = runtime.checkpointImage().orElseThrow(() -> new IllegalStateException("v3 runtime is inactive"));
        CommandId commandId = new CommandId("executor:inventory-conflict-" + id.value().replace(':', '-') + "-r" + checkpoint.revision().value());
        CommandResult result = runtime.submit(new FrontierCommand(1, commandId, checkpoint.worldId(), checkpoint.revision(), checkpoint.instant(),
                FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR, CauseChain.root(commandId), new InventoryConflictObserved(conflict)))
                .orElseThrow(() -> new IllegalStateException("v3 runtime is inactive"));
        if (!(result instanceof CommandResult.Accepted)) throw new IllegalStateException("inventory conflict observation was rejected: " + result);
    }
    private static FrontierWorldState state(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        return runtime.decodedState().orElse(null);
    }
    record StoreChest(BlockPos position, SubjectId containerId) { }
    record HopperCandidate(HopperBlockEntity hopper, int slot, ItemStack stack) { }
    record HopperCarrierBinding(UUID carrierId, HopperCarrierStatus status) { }
    enum HopperCarrierStatus { APPLIED, CURRENT, CONFLICT }
}
