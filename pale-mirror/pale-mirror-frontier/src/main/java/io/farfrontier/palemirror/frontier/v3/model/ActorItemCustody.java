package io.farfrontier.palemirror.frontier.v3.model;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorActuationId;

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
        requireAttachedStorage(state, order, false, Optional.empty());
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
                || !ActorExecutionCoordinator.coldAvailable(state, order.actorId()))
            throw new IllegalArgumentException("cold actor item handoff has no exclusive living actor at its station");
        if (ReferenceContainerCustody.hasLiveCustody(state, container.id())
                || ReferenceContainerCustody.blocksCanonicalUse(state, container.id()))
            throw new IllegalArgumentException("cold actor item handoff competes with physical container authority");
        if (order.portion() instanceof ActorContainerItemOrder.Portion.Exact) {
            requireExactDestinationSlot(state, order);
            return FrontierWorldStateUpdate.begin().inventory(inventory.transferActorOrder(order));
        }
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
        return observed(state, order, sourceEpoch, destinationEpoch, remainingSource, destination, Optional.empty());
    }
    public static FrontierWorldStateUpdate transferRetiredObservedUpdate(FrontierWorldState state, ActorContainerItemOrder order,
            ActorActuationId identity, long sourceEpoch, long destinationEpoch,
            List<FungiblePhysicalObservation.Stack> source, List<FungiblePhysicalObservation.Stack> destination) {
        requireRetired(state, order, identity);
        return observed(state, order, sourceEpoch, destinationEpoch, source, destination, Optional.of(identity));
    }
    public static ExactInventory transferRetiredObserved(FrontierWorldState state, ActorContainerItemOrder order,
            ActorActuationId identity, long sourceEpoch, long destinationEpoch,
            List<FungiblePhysicalObservation.Stack> source, List<FungiblePhysicalObservation.Stack> destination) {
        return transferRetiredObservedUpdate(state, order, identity, sourceEpoch, destinationEpoch, source, destination).inventoryOnly();
    }
    private static FrontierWorldStateUpdate observed(FrontierWorldState state, ActorContainerItemOrder order,
            long sourceEpoch, long destinationEpoch, List<FungiblePhysicalObservation.Stack> remainingSource,
            List<FungiblePhysicalObservation.Stack> destination, Optional<ActorActuationId> retired) {
        Objects.requireNonNull(state, "observed actor item state");
        Objects.requireNonNull(order, "observed actor item order");
        ActorClaimDelegationComposition.validate(state, order);
        ExactInventory inventory = state.inventory();
        ContainerRecord container = inventory.containers().get(order.containerEndpoint().containerId());
        requireEndpoint(container, order);
        requireAttachedStorage(state, order, true, retired);
        if (!(ReferenceContainerCustody.hasOperationalCustody(state, container.id())
                || ReferenceContainerCustody.hasLiveCustody(state, container.id())
                && ContainerPhysicalAuthorityComposition.pending(state, container.id()))
                || ReferenceContainerCustody.blocksCanonicalUse(state, container.id()))
            throw new IllegalArgumentException("observed handoff has no current owned physical endpoint");
        if (order.containerEndpoint() instanceof ActorContainerItemOrder.ContainerEndpoint.ExactStationSlot
                || order.containerEndpoint() instanceof ActorContainerItemOrder.ContainerEndpoint.FungibleStation)
            order.requireCurrentStation(inventory);
        if (order.portion() instanceof ActorContainerItemOrder.Portion.Exact) {
            requireExactDestinationSlot(state, order);
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
    private static void requireAttachedStorage(FrontierWorldState state, ActorContainerItemOrder order, boolean observed, Optional<ActorActuationId> retired) {
        if (!(order.actorSlot() instanceof ActorItemSlot.AttachedStorage storage)) return;
        var surface = state.inventory().surfaces().get(storage.containerId());
        if (surface == null || !surface.location().equals(new ContainerLocation.Mobile(order.actorId()))
                || (retired.isPresent() ? ReferenceContainerCustody.blocksRetiredAttachmentObservation(state, storage.containerId(), retired.orElseThrow().body())
                    : ReferenceContainerCustody.blocksCanonicalUse(state, storage.containerId())))
            throw new IllegalArgumentException("item order has no exact live body/storage attachment");
        if (observed ? !ReferenceContainerCustody.hasOperationalCustody(state, storage.containerId())
                    && !(ReferenceContainerCustody.hasLiveCustody(state, storage.containerId())
                        && ContainerPhysicalAuthorityComposition.pending(state, storage.containerId()))
                : ReferenceContainerCustody.hasLiveCustody(state, storage.containerId()))
            throw new IllegalArgumentException("attached item storage has competing or unavailable physical custody");
        // An observed prepared handoff already owns its destination reservation.
        // Re-admitting it would treat that same slot as a competing reservation.
        // The owner fences the receipt; exact post-layout binding and the inventory
        // update still reject foreign contents and newly overcommitted capacity.
        if (!observed && order.direction() == ActorContainerItemOrder.Direction.TAKE
                && !ContainerStorageAdmission.receive(state, storage.containerId(), order.portion().itemKind(),
                    order.portion().quantity(), java.util.Optional.empty()))
            throw new IllegalArgumentException("attached storage has no capacity for the declared portion");
    }

    /** Ground adapter counterpart. Only an existing exact item-entity binding can fund the pickup. */
    public static FrontierWorldStateUpdate transferObservedUpdate(FrontierWorldState state, ActorGroundItemOrder order,
            long sourceEpoch, long destinationEpoch, List<FungiblePhysicalObservation.Stack> remainingSource,
            List<FungiblePhysicalObservation.Stack> destination) {
        return observedGround(state, order, sourceEpoch, destinationEpoch, remainingSource, destination, Optional.empty());
    }
    public static FrontierWorldStateUpdate transferRetiredObservedUpdate(FrontierWorldState state, ActorGroundItemOrder order,
            ActorActuationId identity, long sourceEpoch, long destinationEpoch,
            List<FungiblePhysicalObservation.Stack> source, List<FungiblePhysicalObservation.Stack> destination) {
        requireRetired(state, order, identity);
        return observedGround(state, order, sourceEpoch, destinationEpoch, source, destination, Optional.of(identity));
    }
    private static FrontierWorldStateUpdate observedGround(FrontierWorldState state, ActorGroundItemOrder order,
            long sourceEpoch, long destinationEpoch, List<FungiblePhysicalObservation.Stack> remainingSource,
            List<FungiblePhysicalObservation.Stack> destination, Optional<ActorActuationId> retired) {
        Objects.requireNonNull(state); Objects.requireNonNull(order);
        var actor = state.actorLocations().get(order.actorId());
        var execution = state.actorExecutions().actors().get(order.actorId());
        if (actor == null || retired.isEmpty() && (actor.condition().status() != ActorLifeStatus.ALIVE
                || !actor.supportingSurface().equals(order.station()) || execution == null
                || execution.current().filter(current -> current.activityOwnerId().equals(order.ownerId())).isEmpty())
                || sourceEpoch != order.sourceEpoch())
            throw new IllegalArgumentException("ground pickup has no living exact execution at its declared source");
        var body = retired.map(ActorActuationId::body).orElseGet(() -> ActorBodyAuthority.current(state, order.actorId()));
        if (body.physicalEpoch() != destinationEpoch
                || retired.isEmpty() && ActorBodyAuthority.require(state, body).phase() != FencedRecoveryPhase.RUNNING)
            throw new IllegalArgumentException("ground pickup has a stale or non-operational body epoch");
        if (order.actorSlot().equals(new ActorItemSlot.Hand(ActorContainerItemOrder.Hand.MAIN))
                && !state.inventory().actorItems(order.actorId()).isEmpty())
            throw new IllegalArgumentException("ground pickup would overwrite exact equipment");
        var preparation = ActorClaimDelegationComposition.prepare(state, order);
        var resources = preparation.inventory().fungibleResources().transferActorOrderObservedStacks(preparation.order(), sourceEpoch,
                destinationEpoch, remainingSource, destination);
        return preparation.ownerChanges().inventory(preparation.inventory().withFungibleResources(resources));
    }

    private static void requireEndpoint(ContainerRecord container, ActorContainerItemOrder order) {
        if (container == null || order.portion() instanceof ActorContainerItemOrder.Portion.Exact exact
                && !container.ownerId().equals(exact.item().economicOwnerId())) {
            throw new IllegalArgumentException("actor item endpoint has no compatible declared container");
        }
        if (order.direction() == ActorContainerItemOrder.Direction.PLACE
                && container.storage().filter(policy -> !policy.filter().accepts(order.portion().itemKind())).isPresent())
            throw new IllegalArgumentException("declared storage filter refuses this resource portion");
        // Fungible custody and title are independent. The calling owner authorizes the order;
        // the resource ledger validates quantities/claims and preserves each lot's actual owner.
    }
    private static void requireRetired(FrontierWorldState state, ActorResourceItemOrder order, ActorActuationId identity) {
        if (!order.actorId().equals(identity.body().actorId())) throw new IllegalArgumentException("retired transfer names a foreign actor");
        ActorBodyAuthority.requireRetiredDeath(state, identity.body());
        state.actorExecutions().requireRetained(identity.execution());
    }
    private static void requireExactDestinationSlot(FrontierWorldState state, ActorContainerItemOrder order) {
        if (!(order.actorSlot() instanceof ActorItemSlot.Hand hand) || hand.hand() != ActorContainerItemOrder.Hand.MAIN)
            throw new IllegalArgumentException("exact equipment declares the common primary equipment slot");
        if (order.direction() == ActorContainerItemOrder.Direction.TAKE && UnitInventoryPresentation.inventory(state, order.actorId()).values()
                .stream().anyMatch(stack -> stack.slot().equals(order.actorSlot())))
            throw new IllegalArgumentException("equipment pickup would overwrite a resource in its actual actor slot");
    }
}
