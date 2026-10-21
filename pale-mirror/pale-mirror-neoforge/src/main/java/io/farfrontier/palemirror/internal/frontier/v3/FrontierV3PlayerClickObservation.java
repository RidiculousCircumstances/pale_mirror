package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import java.util.*;

/** Captures the causal callback's result, not a later layout poll. Exact items and
 * fungible stock share one command while retaining their existing accounting owners. */
final class FrontierV3PlayerClickObservation {
    private FrontierV3PlayerClickObservation() { }
    static ContainerPlayerEditObserved capture(ServerPlayer player, FrontierWorldState state,
            FrontierV3ContainerClickTransaction transaction) {
        var members = new ArrayList<ContainerPlayerEditObserved.Member>();
        ExactItemCustodyChanged ingress = null; var seenExact = new HashSet<io.farfrontier.palemirror.frontier.v3.api.SubjectId>();
        for (var member : transaction.members()) {
            var physical = FrontierV3PhysicalContainer.loaded(player.serverLevel(), state, member.containerId()).orElseThrow();
            var stacks = new ArrayList<FungiblePhysicalObservation.Stack>();
            for (int slot = 0; slot < physical.inventory().getContainerSize(); slot++) {
                var stack = physical.inventory().getItem(slot);
                var expected = state.inventory().itemAt(member.containerId(), slot);
                if (expected.isPresent() && !FrontierV3ExactItemPresentation.exactMatch(stack, expected.orElseThrow()))
                    throw new IllegalStateException("native click changed an exact item outside its admitted ingress");
                if (stack.isEmpty()) continue;
                var destination = new InventoryCustody.ContainerSlot(member.containerId(), slot);
                var id = FrontierV3ExactItemPresentation.itemId(stack);
                if (id.isPresent()) {
                    var item = state.inventory().items().get(id.orElseThrow());
                    if (item == null || !FrontierV3ExactItemPresentation.exactMatch(stack, item) || !seenExact.add(item.id()))
                        throw new IllegalStateException("native click produced a foreign or duplicate exact item");
                    if (item.custody().equals(destination)) continue;
                    if (!item.custody().equals(new InventoryCustody.Player(player.getUUID())) || ingress != null
                            || FrontierV3InventoryObservationExecutor.hasExactItem(player, item)
                            || FrontierV3ExactItemPresentation.exactMatch(player.containerMenu.getCarried(), item))
                        throw new IllegalStateException("native click lost its exact player source");
                    ingress = new ExactItemCustodyChanged(item.id(), item.custody(), destination);
                } else {
                    if (!ItemStack.isSameItemSameComponents(stack, new ItemStack(stack.getItem(), stack.getCount())))
                        throw new IllegalStateException("native click produced unsupported item components");
                    stacks.add(new FungiblePhysicalObservation.Stack(new PhysicalStackAddress.ContainerSlot(destination),
                            BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), stack.getCount()));
                }
            }
            members.add(new ContainerPlayerEditObserved.Member(member.containerId(), member.accountId(), member.ownerId(),
                    member.authorityEpoch(), member.before(), stacks));
        }
        var first = transaction.members().getFirst();
        var returning = first.returnSource().map(source -> {
            var actual = player.getInventory().getItem(source.playerSlot());
            if (!actual.isEmpty() && !BuiltInRegistries.ITEM.getKey(actual.getItem()).toString().equals(source.itemKind()))
                throw new IllegalStateException("native click changed the tracked player source kind");
            return new ContainerPlayerEditObserved.PlayerReturn(source.bindingId(), source.accountId(), source.epoch(),
                    source.playerSlot(), source.itemKind(), source.quantity(), actual.isEmpty() ? 0 : actual.getCount());
        });
        return new ContainerPlayerEditObserved(player.getUUID(), first.interactionId(), members, returning, Optional.ofNullable(ingress));
    }
}
