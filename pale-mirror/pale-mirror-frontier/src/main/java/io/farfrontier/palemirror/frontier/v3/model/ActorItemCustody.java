package io.farfrontier.palemirror.frontier.v3.model;

import java.util.List;
import java.util.Objects;

/** Shared COLD handoff preconditions; the owning process publishes inventory and job phase atomically. */
public final class ActorItemCustody {
    private ActorItemCustody() { }

    public static ExactInventory transferCold(FrontierWorldState state, ActorContainerItemOrder order) {
        Objects.requireNonNull(state, "actor item state");
        Objects.requireNonNull(order, "actor item order");
        ExactInventory inventory = state.inventory();
        ContainerRecord container = inventory.containers().get(order.containerEndpoint().containerId());
        if (container == null || !container.ownerId().equals(resourceOwner(inventory, order)))
            throw new IllegalArgumentException("actor item endpoint has no declared owner-compatible container");
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
            return inventory.transferActorOrder(order);
        if (order.direction() == ActorContainerItemOrder.Direction.TAKE
                && order.hand() == ActorContainerItemOrder.Hand.MAIN
                && !inventory.actorItems(order.actorId()).isEmpty())
            throw new IllegalArgumentException("actor already holds an exact item");
        return inventory.withFungibleResources(inventory.fungibleResources().transferActorOrderCold(order));
    }

    /** Loaded counterpart: the job owner supplies a durable intent and post-effect stack witness. */
    public static ExactInventory transferObserved(FrontierWorldState state, ActorContainerItemOrder order,
                                                  long sourceEpoch, long destinationEpoch,
                                                  List<FungiblePhysicalObservation.Stack> remainingSource,
                                                  List<FungiblePhysicalObservation.Stack> destination) {
        Objects.requireNonNull(state, "observed actor item state");
        Objects.requireNonNull(order, "observed actor item order");
        ExactInventory inventory = state.inventory();
        ContainerRecord container = inventory.containers().get(order.containerEndpoint().containerId());
        if (container == null || !container.ownerId().equals(resourceOwner(inventory, order))
                || !(ReferenceContainerCustody.hasOperationalCustody(state, container.id())
                || ReferenceContainerCustody.hasLiveCustody(state, container.id())
                && (BakeryPhysicalAuthority.pendingForContainer(state, container.id())
                    || ResidentMealPhysicalAuthority.pendingForContainer(state, container.id())))
                || ReferenceContainerCustody.blocksCanonicalUse(state, container.id()))
            throw new IllegalArgumentException("observed handoff has no current owned physical endpoint");
        if (order.containerEndpoint() instanceof ActorContainerItemOrder.ContainerEndpoint.ExactStationSlot
                || order.containerEndpoint() instanceof ActorContainerItemOrder.ContainerEndpoint.FungibleStation)
            order.requireCurrentStation(inventory);
        if (order.portion() instanceof ActorContainerItemOrder.Portion.Exact) {
            if (!remainingSource.isEmpty() || !destination.isEmpty())
                throw new IllegalArgumentException("exact actor handoff may not retain fungible witness layouts");
            return inventory.transferActorOrder(order);
        }
        if (order.direction() == ActorContainerItemOrder.Direction.TAKE
                && order.hand() == ActorContainerItemOrder.Hand.MAIN
                && !inventory.actorItems(order.actorId()).isEmpty())
            throw new IllegalArgumentException("observed actor handoff would overwrite an exact item");
        return inventory.withFungibleResources(inventory.fungibleResources()
                .transferActorOrderObservedStacks(order, sourceEpoch, destinationEpoch, remainingSource, destination));
    }

    private static io.farfrontier.palemirror.frontier.v3.api.SubjectId resourceOwner(
            ExactInventory inventory, ActorContainerItemOrder order) {
        return switch (order.portion()) {
            case ActorContainerItemOrder.Portion.Exact exact -> exact.item().economicOwnerId();
            case ActorContainerItemOrder.Portion.Fungible fungible -> fungible.lotQuantities().keySet().stream()
                    .map(id -> inventory.fungibleResources().lots().get(id))
                    .map(lot -> {
                        if (lot == null) throw new IllegalArgumentException("actor item order names an unknown lot");
                        return lot.economicOwnerId();
                    })
                    .distinct().reduce((left, right) -> {
                        throw new IllegalArgumentException("actor item order cannot mix economic owners");
                    }).orElseThrow();
        };
    }
}
