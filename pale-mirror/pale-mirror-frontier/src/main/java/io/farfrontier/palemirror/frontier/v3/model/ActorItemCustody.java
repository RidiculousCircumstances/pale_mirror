package io.farfrontier.palemirror.frontier.v3.model;

import java.util.List;
import java.util.Objects;

/** Shared COLD handoff preconditions; the owning process publishes inventory and job phase atomically. */
public final class ActorItemCustody {
    private ActorItemCustody() { }

    public static ExactInventory transferCold(FrontierWorldState state, ActorContainerItemOrder order) {
        var changes = transferColdUpdate(state, order);
        return changes.inventoryOnly();
    }
    public static FrontierWorldStateUpdate transferColdUpdate(FrontierWorldState state, ActorContainerItemOrder order) {
        Objects.requireNonNull(state, "actor item state");
        Objects.requireNonNull(order, "actor item order");
        ActorClaimDelegationComposition.validate(state, order);
        ExactInventory inventory = state.inventory();
        ContainerRecord container = inventory.containers().get(order.containerEndpoint().containerId());
        requireEndpoint(container, order);
        if (order.containerEndpoint() instanceof ActorContainerItemOrder.ContainerEndpoint.ExactStationSlot
                || order.containerEndpoint() instanceof ActorContainerItemOrder.ContainerEndpoint.FungibleStation)
            order.requireCurrentStation(inventory);
        if (order.direction() == ActorContainerItemOrder.Direction.PLACE
                && order.containerEndpoint() instanceof ActorContainerItemOrder.ContainerEndpoint.FungibleStation station
                && inventory.itemAt(station.containerId(), station.spec().inputSlot()).isPresent())
            throw new IllegalArgumentException("station input port is occupied by an exact stack");
        ActorLocation actor = state.actorLocations().get(order.actorId());
        if (actor == null || actor.condition().status() != ActorLifeStatus.ALIVE
                || !actor.supportingSurface().equals(order.station())
                || !FrontierSceneAdmission.available(state, List.of(order.actorId()))
                || state.sceneLeases().values().stream().anyMatch(lease -> lease.retainsMemberCustody(order.actorId())))
            throw new IllegalArgumentException("cold actor item handoff has no exclusive living actor at its station");
        if (ReferenceContainerCustody.hasLiveCustody(state, container.id())
                || ReferenceContainerCustody.blocksCanonicalUse(state, container.id()))
            throw new IllegalArgumentException("cold actor item handoff competes with physical container authority");
        if (order.portion() instanceof ActorContainerItemOrder.Portion.Exact)
            return FrontierWorldStateUpdate.begin().inventory(inventory.transferActorOrder(order));
        if (order.direction() == ActorContainerItemOrder.Direction.TAKE
                && order.actorSlot().equals(new ActorItemSlot.Hand(ActorContainerItemOrder.Hand.MAIN))
                && !inventory.actorItems(order.actorId()).isEmpty())
            throw new IllegalArgumentException("actor already holds an exact item");
        var preparation = ActorClaimDelegationComposition.prepare(state, order);
        inventory = preparation.inventory();
        return preparation.ownerChanges().inventory(inventory.withFungibleResources(inventory.fungibleResources().transferActorOrderCold(preparation.order())));
    }

    /** Loaded counterpart: the job owner supplies a durable intent and post-effect stack witness. */
    public static ExactInventory transferObserved(FrontierWorldState state, ActorContainerItemOrder order,
                                                  long sourceEpoch, long destinationEpoch,
                                                  List<FungiblePhysicalObservation.Stack> remainingSource,
                                                  List<FungiblePhysicalObservation.Stack> destination) {
        var changes = transferObservedUpdate(state, order, sourceEpoch, destinationEpoch, remainingSource, destination);
        return changes.inventoryOnly();
    }
    public static FrontierWorldStateUpdate transferObservedUpdate(FrontierWorldState state, ActorContainerItemOrder order,
                                                  long sourceEpoch, long destinationEpoch,
                                                  List<FungiblePhysicalObservation.Stack> remainingSource,
                                                  List<FungiblePhysicalObservation.Stack> destination) {
        Objects.requireNonNull(state, "observed actor item state");
        Objects.requireNonNull(order, "observed actor item order");
        ActorClaimDelegationComposition.validate(state, order);
        ExactInventory inventory = state.inventory();
        ContainerRecord container = inventory.containers().get(order.containerEndpoint().containerId());
        requireEndpoint(container, order);
        if (!(ReferenceContainerCustody.hasOperationalCustody(state, container.id())
                || ReferenceContainerCustody.hasLiveCustody(state, container.id())
                && ContainerPhysicalAuthorityComposition.pending(state, container.id()))
                || ReferenceContainerCustody.blocksCanonicalUse(state, container.id()))
            throw new IllegalArgumentException("observed handoff has no current owned physical endpoint");
        if (order.containerEndpoint() instanceof ActorContainerItemOrder.ContainerEndpoint.ExactStationSlot
                || order.containerEndpoint() instanceof ActorContainerItemOrder.ContainerEndpoint.FungibleStation)
            order.requireCurrentStation(inventory);
        if (order.portion() instanceof ActorContainerItemOrder.Portion.Exact) {
            if (!remainingSource.isEmpty() || !destination.isEmpty())
                throw new IllegalArgumentException("exact actor handoff may not retain fungible witness layouts");
            return FrontierWorldStateUpdate.begin().inventory(inventory.transferActorOrder(order));
        }
        if (order.direction() == ActorContainerItemOrder.Direction.TAKE
                && order.actorSlot().equals(new ActorItemSlot.Hand(ActorContainerItemOrder.Hand.MAIN))
                && !inventory.actorItems(order.actorId()).isEmpty())
            throw new IllegalArgumentException("observed actor handoff would overwrite an exact item");
        var preparation = ActorClaimDelegationComposition.prepare(state, order);
        inventory = preparation.inventory();
        return preparation.ownerChanges().inventory(inventory.withFungibleResources(inventory.fungibleResources()
                .transferActorOrderObservedStacks(preparation.order(), sourceEpoch, destinationEpoch, remainingSource, destination)));
    }
    private static void requireEndpoint(ContainerRecord container, ActorContainerItemOrder order) {
        if (container == null || order.portion() instanceof ActorContainerItemOrder.Portion.Exact exact
                && !container.ownerId().equals(exact.item().economicOwnerId())) {
            throw new IllegalArgumentException("actor item endpoint has no compatible declared container");
        }
        // Fungible custody and title are independent. The calling owner authorizes the order;
        // the resource ledger validates quantities/claims and preserves each lot's actual owner.
    }
}
