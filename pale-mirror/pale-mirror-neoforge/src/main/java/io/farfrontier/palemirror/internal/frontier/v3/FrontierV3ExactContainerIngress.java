package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.CommandResult;
import io.farfrontier.palemirror.frontier.v3.model.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import java.util.*;

/** Exact-item ingress under the existing durable player click fence, separate from fungible accounting.
 * Neither tags nor a resource name invent an identity: the canonical exact record supplies it. */
final class FrontierV3ExactContainerIngress {
    enum Result { NONE, WAITING, ADVANCED }
    private FrontierV3ExactContainerIngress() { }

    static boolean admissible(FrontierWorldState state, ServerPlayer player, ItemStack incoming) {
        if (incoming.isEmpty()) return true;
        var id = FrontierV3ExactItemPresentation.itemId(incoming);
        if (id.isEmpty()) return ItemStack.isSameItemSameComponents(incoming, new ItemStack(incoming.getItem(), incoming.getCount()));
        var exact = state.inventory().items().get(id.orElseThrow());
        return exact != null && exact.custody().equals(new InventoryCustody.Player(player.getUUID()))
                && FrontierV3ExactItemPresentation.exactMatch(incoming, exact);
    }

    static Result reconcile(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
            FrontierWorldState state, FrontierV3ContainerClickTransaction transaction) {
        ExactItemCustodyChanged transfer = null;
        for (var member : transaction.members()) {
            var physical = FrontierV3PhysicalContainer.loaded(level, state, member.containerId()).orElse(null);
            if (physical == null) return Result.WAITING;
            var fungible = new ArrayList<FungiblePhysicalObservation.Stack>();
            for (int slot = 0; slot < physical.inventory().getContainerSize(); slot++) {
                var actual = physical.inventory().getItem(slot);
                if (actual.isEmpty()) continue;
                var destination = new InventoryCustody.ContainerSlot(member.containerId(), slot);
                var id = FrontierV3ExactItemPresentation.itemId(actual);
                var exact = id.map(state.inventory().items()::get).orElse(null);
                if (exact != null && FrontierV3ExactItemPresentation.exactMatch(actual, exact)) {
                    if (exact.custody().equals(destination)) continue;
                    var lease = state.replicaCustody().custodyByScope().get(ReferenceContainerCustody.scopeId(member.containerId()));
                    if (lease == null || lease.authorityEpoch() != member.authorityEpoch()
                            || !ReferenceContainerCustody.hasOperationalCustody(state, member.containerId())) return Result.WAITING;
                    if (!exact.custody().equals(new InventoryCustody.Player(member.playerId()))
                            || member.before().stream().anyMatch(s -> s.address().equals(new PhysicalStackAddress.ContainerSlot(destination))))
                        return Result.NONE; // Not this player's witnessed empty-slot ingress.
                    if (transfer != null) return Result.NONE; // One ordinary exact stack, never guessed multi-item drift.
                    transfer = new ExactItemCustodyChanged(exact.id(), exact.custody(), destination);
                } else {
                    if (id.isPresent() || !ItemStack.isSameItemSameComponents(actual, new ItemStack(actual.getItem(), actual.getCount())))
                        return Result.NONE;
                    fungible.add(new FungiblePhysicalObservation.Stack(new PhysicalStackAddress.ContainerSlot(destination),
                            net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(actual.getItem()).toString(), actual.getCount()));
                }
            }
            // Exact ingress does not have permission to erase or reclassify a simultaneous resource edit.
            if (!fungible.equals(member.before())) return Result.NONE;
        }
        if (transfer == null) return Result.NONE;
        var player = level.getServer().getPlayerList().getPlayer(transaction.members().getFirst().playerId());
        if (player == null) return Result.WAITING;
        var exact = state.inventory().items().get(transfer.itemId());
        if (level.getServer().getPlayerList().getPlayers().stream().anyMatch(p ->
                FrontierV3InventoryObservationExecutor.hasExactItem(p, exact)
                        || FrontierV3ExactItemPresentation.exactMatch(p.containerMenu.getCarried(), exact))) return Result.NONE;
        var result = FrontierV3CommandSubmission.submitResult(runtime, "exact-container-player-ingress",
                transaction.members().getFirst().interactionId() + "/" + exact.id().value(), transfer);
        if (!(result instanceof CommandResult.Accepted)) FrontierV3ContainerClickExecutor.conflict(runtime,
                transaction.members().getFirst(), "exact player ingress rejected: " + result);
        return result instanceof CommandResult.Accepted ? Result.ADVANCED : Result.WAITING;
    }
}
