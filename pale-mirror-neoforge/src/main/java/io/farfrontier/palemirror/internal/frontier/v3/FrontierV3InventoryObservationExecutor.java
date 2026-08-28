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
import io.farfrontier.palemirror.frontier.v3.model.HiveOrganKind;
import io.farfrontier.palemirror.frontier.v3.model.InventoryCustody;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Bounded loaded-chunk observation of exact items crossing an owned hive STORE chest and a
 * player's real inventory. It never writes a chest or player inventory: unverifiable drift
 * quarantines the v3 runtime rather than being adopted or repaired.
 */
final class FrontierV3InventoryObservationExecutor {
    private FrontierV3InventoryObservationExecutor() { }

    static void tick(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        FrontierWorldState state = state(runtime);
        if (state == null) return;
        for (StoreChest store : stores(state)) {
            if (!level.hasChunkAt(store.position())) continue;
            if (!(level.getBlockEntity(store.position()) instanceof ChestBlockEntity chest)
                    || !store.containerId().value().equals(chest.getPersistentData().getString(FrontierV3CargoHandoffExecutor.CONTAINER_ID_KEY))) continue;
            if (observeOne(level, runtime, state, store, chest)) return;
        }
    }

    private static boolean observeOne(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                      FrontierWorldState state, StoreChest store, ChestBlockEntity chest) {
        for (int slot = 0; slot < chest.getContainerSize(); slot++) {
            InventoryCustody.ContainerSlot custody = new InventoryCustody.ContainerSlot(store.containerId(), slot);
            Optional<ExactItemStack> canonical = state.inventory().itemAt(store.containerId(), slot);
            ItemStack actual = chest.getItem(slot);
            if (canonical.isPresent()) {
                ExactItemStack expected = canonical.orElseThrow();
                if (FrontierV3CargoHandoffExecutor.exactMatch(actual, expected)) continue;
                List<ServerPlayer> holders = playersHolding(level, expected);
                if (actual.isEmpty() && holders.size() == 1) {
                    submit(runtime, expected.id(), custody, new InventoryCustody.Player(holders.getFirst().getUUID()));
                    return true;
                }
                throw new IllegalStateException("unreconciled exact item drift at " + store.containerId().value() + " slot " + slot);
            }
            if (actual.isEmpty()) continue;
            ExactItemStack playerItem = playerOwnedExact(state, actual).orElse(null);
            if (playerItem == null || !(playerItem.custody() instanceof InventoryCustody.Player)
                    || !playersHolding(level, playerItem).isEmpty()) {
                throw new IllegalStateException("foreign or duplicated item in owned v3 container " + store.containerId().value() + " slot " + slot);
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
        return state.bootstrap().hive().organs().stream().filter(organ -> organ.kind() == HiveOrganKind.STORE)
                .map(organ -> new StoreChest(new BlockPos(organ.anchor().x(), organ.anchor().y() + 1, organ.anchor().z()), organ.containerId().orElseThrow()))
                .sorted(Comparator.comparing(StoreChest::containerId)).toList();
    }
    private static void submit(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, SubjectId itemId,
                               InventoryCustody from, InventoryCustody to) {
        CheckpointImage checkpoint = runtime.checkpointImage().orElseThrow(() -> new IllegalStateException("v3 runtime is inactive"));
        String direction = from instanceof InventoryCustody.Player ? "deposit" : "withdraw";
        CommandId commandId = new CommandId("executor:item-" + direction + "-" + itemId.value().replace(':', '-') + "-r" + checkpoint.revision().value());
        CommandResult result = runtime.submit(new FrontierCommand(1, commandId, checkpoint.worldId(), checkpoint.revision(), checkpoint.instant(),
                FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR, CauseChain.root(commandId), new ExactItemCustodyChanged(itemId, from, to)))
                .orElseThrow(() -> new IllegalStateException("v3 runtime is inactive"));
        if (!(result instanceof CommandResult.Accepted)) throw new IllegalStateException("exact item custody observation was rejected: " + result);
    }
    private static FrontierWorldState state(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        return runtime.checkpointImage().map(image -> new FrontierWorldStateCodec().decode(image.canonicalState())).orElse(null);
    }
    record StoreChest(BlockPos position, SubjectId containerId) { }
}
