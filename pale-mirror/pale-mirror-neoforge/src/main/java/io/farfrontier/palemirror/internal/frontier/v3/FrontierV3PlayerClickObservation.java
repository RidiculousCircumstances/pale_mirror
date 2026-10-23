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
        ExactItemIngress ingress = null; var seenExact = new HashSet<io.farfrontier.palemirror.frontier.v3.api.SubjectId>();
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
                    if (item == null || !FrontierV3ExactItemPresentation.exactMatch(stack,
                            FrontierV3ExactContainerIngress.observedCondition(stack, item)) || !seenExact.add(item.id()))
                        throw new IllegalStateException("native click produced a foreign or duplicate exact item");
                    if (item.custody().equals(destination)) continue;
                    if (!item.custody().equals(new InventoryCustody.Player(player.getUUID())) || ingress != null
                            || player.getInventory().items.stream().anyMatch(s -> FrontierV3ExactItemPresentation.itemId(s).filter(item.id()::equals).isPresent())
                            || FrontierV3ExactItemPresentation.itemId(player.containerMenu.getCarried()).filter(item.id()::equals).isPresent())
                        throw new IllegalStateException("native click lost its exact player source");
                    ingress = new ExactItemIngress.Returned(item,
                            FrontierV3ExactContainerIngress.observedCondition(stack, item).withCustody(destination));
                } else {
                    if (!FrontierV3ExactContainerIngress.plainSupported(stack))
                        throw new IllegalStateException("native click produced unsupported item components");
                    if (stack.isDamageableItem()) {
                        if (ingress != null) throw new IllegalStateException("one click produced multiple individual arrivals");
                        var itemId = new io.farfrontier.palemirror.frontier.v3.api.SubjectId("item:player-contribution:"
                                + UUID.nameUUIDFromBytes((transaction.id() + "/" + member.containerId().value() + "/" + slot)
                                    .getBytes(java.nio.charset.StandardCharsets.UTF_8)));
                        var item = new ExactItemStack(itemId, member.ownerId(), BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(),
                                1, destination, new ItemCondition(stack.getMaxDamage(), stack.getDamageValue()));
                        FrontierV3ExactItemPresentation.bindExactItemId(stack, itemId);
                        physical.inventory().setChanged();
                        ingress = new ExactItemIngress.Contribution(item);
                    } else stacks.add(new FungiblePhysicalObservation.Stack(new PhysicalStackAddress.ContainerSlot(destination),
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
