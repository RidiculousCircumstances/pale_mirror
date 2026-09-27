package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.ExactItemStack;
import io.farfrontier.palemirror.frontier.v3.model.InventoryCustody;
import io.farfrontier.palemirror.frontier.v3.model.ActorContainerItemOrder;
import io.farfrontier.palemirror.frontier.v3.model.MaterialSourceSelection;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalStackAddress;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Physical half of an actor's exact container/hand transfer. The owning process must first
 * establish the actor, source/destination, station and durable intent; this class neither
 * selects a job nor changes canonical custody. A successful return is only an observation
 * for that owner's receipt, never permission to infer a new item or economic owner.
 */
final class FrontierV3ActorItemTransfer {
    private FrontierV3ActorItemTransfer() { }

    static boolean take(ChestBlockEntity chest, Villager actor, ExactItemStack expected,
                        InventoryCustody.ContainerSlot source, EquipmentSlot hand) {
        Objects.requireNonNull(chest, "source chest");
        Objects.requireNonNull(actor, "item actor");
        Objects.requireNonNull(expected, "expected item");
        Objects.requireNonNull(source, "source slot");
        requireHand(hand);
        if (source.slot() >= chest.getContainerSize()
                || !FrontierV3CargoHandoffExecutor.exactMatch(chest.getItem(source.slot()), expected)
                || !actor.getItemBySlot(hand).isEmpty()) return false;
        ItemStack stack = chest.getItem(source.slot());
        chest.setItem(source.slot(), ItemStack.EMPTY);
        chest.setChanged();
        actor.setItemSlot(hand, stack);
        return chest.getItem(source.slot()).isEmpty()
                && FrontierV3CargoHandoffExecutor.exactMatch(actor.getItemBySlot(hand), expected);
    }

    static boolean place(ChestBlockEntity chest, Villager actor, ExactItemStack expected,
                         InventoryCustody.ContainerSlot destination, EquipmentSlot hand) {
        Objects.requireNonNull(chest, "destination chest");
        Objects.requireNonNull(actor, "item actor");
        Objects.requireNonNull(expected, "expected item");
        Objects.requireNonNull(destination, "destination slot");
        requireHand(hand);
        if (destination.slot() >= chest.getContainerSize()
                || !chest.getItem(destination.slot()).isEmpty()
                || !FrontierV3CargoHandoffExecutor.exactMatch(actor.getItemBySlot(hand), expected)) return false;
        ItemStack stack = actor.getItemBySlot(hand);
        actor.setItemSlot(hand, ItemStack.EMPTY);
        chest.setItem(destination.slot(), stack);
        chest.setChanged();
        return actor.getItemBySlot(hand).isEmpty()
                && FrontierV3CargoHandoffExecutor.exactMatch(chest.getItem(destination.slot()), expected);
    }

    /**
     * One process-declared fungible container/hand step. The caller owns the durable intent and
     * canonical receipt; this adapter only checks and mutates the bounded loaded Minecraft
     * stacks. It knows neither recipes nor jobs, and source slots come from current bindings.
     */
    static final class FungibleStep {
        private final ActorContainerItemOrder order;
        private final ChestBlockEntity chest;
        private final Villager actor;
        private final UUID declaredBodyId;
        private final List<MaterialSourceSelection.Slice> source;
        private final int destinationSlot;
        private final Item item;

        FungibleStep(ActorContainerItemOrder order, ChestBlockEntity chest, Villager actor, UUID declaredBodyId,
                     List<MaterialSourceSelection.Slice> source, int destinationSlot) {
            this.order = Objects.requireNonNull(order, "fungible actor order");
            this.chest = Objects.requireNonNull(chest, "declared material container");
            this.actor = Objects.requireNonNull(actor, "declared physical actor");
            this.declaredBodyId = Objects.requireNonNull(declaredBodyId, "declared scene body");
            this.source = List.copyOf(Objects.requireNonNull(source, "current source bindings"));
            this.destinationSlot = destinationSlot;
            if (!(order.portion() instanceof ActorContainerItemOrder.Portion.Fungible portion)
                    || !actor.getUUID().equals(declaredBodyId)
                    || !order.containerEndpoint().containerId().value().equals(chest.getPersistentData()
                    .getString(FrontierV3CargoHandoffExecutor.CONTAINER_ID_KEY))
                    || source.isEmpty() || source.stream().mapToInt(MaterialSourceSelection.Slice::moved).sum() != portion.quantity()
                    || source.stream().map(MaterialSourceSelection.Slice::address).distinct().count() != source.size()
                    || source.stream().map(MaterialSourceSelection.Slice::epoch).distinct().count() != 1)
                throw new IllegalArgumentException("fungible step has no exact declared body, container or bounded portion");
            ResourceLocation id = ResourceLocation.tryParse(portion.itemKind());
            this.item = id == null ? Items.AIR : BuiltInRegistries.ITEM.getOptional(id).orElse(Items.AIR);
            if (item == Items.AIR || portion.quantity() > item.getDefaultMaxStackSize())
                throw new IllegalArgumentException("fungible step has no stackable Minecraft item");
            boolean taking = order.direction() == ActorContainerItemOrder.Direction.TAKE;
            if (taking) {
                if (destinationSlot != -1 || source.stream().anyMatch(slice -> !(slice.address() instanceof PhysicalStackAddress.ContainerSlot address)
                        || !address.slot().containerId().equals(order.containerEndpoint().containerId())
                        || address.slot().slot() >= chest.getContainerSize()
                        || order.containerEndpoint() instanceof ActorContainerItemOrder.ContainerEndpoint.FungibleStation station
                        && address.slot().slot() != station.spec().outputSlot()))
                    throw new IllegalArgumentException("fungible TAKE has a foreign source slot");
            } else if (source.size() != 1 || !(source.getFirst().address() instanceof PhysicalStackAddress.ActorHand hand)
                    || !hand.actorId().equals(order.actorId()) || !hand.entityId().equals(declaredBodyId)
                    || destinationSlot < 0 || destinationSlot >= chest.getContainerSize()
                    || order.containerEndpoint() instanceof ActorContainerItemOrder.ContainerEndpoint.FungibleStation station
                    && destinationSlot != station.spec().inputSlot()) {
                throw new IllegalArgumentException("fungible PLACE has a foreign hand or destination port");
            }
        }

        boolean before() {
            if (!sourceMatches(false)) return false;
            return order.direction() == ActorContainerItemOrder.Direction.TAKE
                    ? actor.getItemBySlot(hand()).isEmpty() : chest.getItem(destinationSlot).isEmpty();
        }

        boolean after() {
            if (!sourceMatches(true)) return false;
            return order.direction() == ActorContainerItemOrder.Direction.TAKE
                    ? plain(actor.getItemBySlot(hand()), order.portion().quantity())
                    : plain(chest.getItem(destinationSlot), order.portion().quantity());
        }

        boolean apply() {
            if (!before()) return false;
            if (order.direction() == ActorContainerItemOrder.Direction.TAKE) {
                for (MaterialSourceSelection.Slice slice : source) {
                    int slot = ((PhysicalStackAddress.ContainerSlot) slice.address()).slot().slot();
                    ItemStack stack = chest.getItem(slot);
                    stack.shrink(slice.moved()); chest.setItem(slot, stack);
                }
                actor.setItemSlot(hand(), new ItemStack(item, order.portion().quantity()));
            } else {
                ItemStack held = actor.getItemBySlot(hand());
                chest.setItem(destinationSlot, held.copyWithCount(order.portion().quantity()));
                actor.setItemSlot(hand(), held.getCount() == order.portion().quantity()
                        ? ItemStack.EMPTY : held.copyWithCount(held.getCount() - order.portion().quantity()));
            }
            chest.setChanged();
            return after();
        }

        private boolean sourceMatches(boolean after) {
            for (MaterialSourceSelection.Slice slice : source) {
                int expected = slice.before() - (after ? slice.moved() : 0);
                ItemStack actual = switch (slice.address()) {
                    case PhysicalStackAddress.ContainerSlot address -> chest.getItem(address.slot().slot());
                    case PhysicalStackAddress.ActorHand ignored -> actor.getItemBySlot(hand());
                    default -> throw new IllegalArgumentException("fungible step source is not a chest or actor hand");
                };
                if (expected == 0 ? !actual.isEmpty() : !plain(actual, expected)) return false;
            }
            return true;
        }

        private boolean plain(ItemStack stack, int count) {
            return stack.getCount() == count && ItemStack.isSameItemSameComponents(stack, new ItemStack(item, count));
        }

        private EquipmentSlot hand() {
            return order.hand() == ActorContainerItemOrder.Hand.MAIN ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND;
        }
    }

    private static void requireHand(EquipmentSlot hand) {
        if (hand != EquipmentSlot.MAINHAND && hand != EquipmentSlot.OFFHAND) {
            throw new IllegalArgumentException("item transfer requires one declared actor hand");
        }
    }
}
