package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.ExactItemCustodyChanged;
import io.farfrontier.palemirror.frontier.v3.model.ExactItemStack;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.InventoryCustody;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.MinecartChest;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Observes the first physical step after a player opens an interrupted HOT cargo cart. It never
 * writes inventories and visits one exact missing stack per tick, so a native container transfer
 * becomes a durable {@code WorldCarrier -> Player} fact without loading a chunk.
 */
final class FrontierV3CargoCarrierObservationExecutor {
    private FrontierV3CargoCarrierObservationExecutor() { }

    static void tick(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        FrontierWorldState state = runtime.decodedState().orElse(null);
        if (state == null) return;
        for (UUID carrierId : state.inventory().worldCarrierItems().keySet().stream().sorted().toList()) {
            Entity entity = level.getEntity(carrierId);
            if (!(entity instanceof MinecartChest cart) || entity.isRemoved()) continue;
            for (SubjectId itemId : state.inventory().worldCarrierItems().get(carrierId)) {
                ExactItemStack item = state.inventory().items().get(itemId);
                InventoryCustody.WorldCarrier custody = new InventoryCustody.WorldCarrier(carrierId);
                if (item == null || !item.custody().equals(custody)) throw new IllegalStateException("world carrier reverse index is not exact");
                if (contains(cart, item, carrierId)) continue;
                List<ServerPlayer> holders = level.players().stream().filter(player -> FrontierV3InventoryObservationExecutor.hasExactItem(player, item))
                        .sorted(Comparator.comparing(ServerPlayer::getUUID)).toList();
                if (holders.size() != 1) continue;
                FrontierV3CommandSubmission.submit(runtime, "cargo-carrier-withdrawal", item.id().value(),
                        new ExactItemCustodyChanged(item.id(), custody, new InventoryCustody.Player(holders.getFirst().getUUID())));
                return;
            }
        }
    }

    private static boolean contains(MinecartChest cart, ExactItemStack item, UUID carrierId) {
        for (int slot = 0; slot < cart.getContainerSize(); slot++) {
            if (FrontierV3CargoHandoffExecutor.exactMatch(cart.getItem(slot), item)
                    && FrontierV3CargoHandoffExecutor.worldCarrierId(cart.getItem(slot)).filter(carrierId::equals).isPresent()) return true;
        }
        return false;
    }
}
