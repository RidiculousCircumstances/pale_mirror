package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyId;
import java.util.*;

/** Transport settles custody; its registered authorization owner releases only the unfulfilled promise. */
public final class ShipmentCargoDisposition {
    private ShipmentCargoDisposition() { }
    public static FrontierWorldState apply(FrontierWorldState state, SubjectId subject, ShipmentCargoDispositionObserved observed) {
        var shipment = state.shipments().shipments().get(observed.shipmentId());
        if (shipment == null || !subject.equals(shipment.id()) || shipment.status() != Shipment.Status.CARRYING
                || shipment.mobileContainerId().isPresent()
                || shipment.revision() != observed.expectedRevision() || shipment.pendingPhysicalStep().isPresent()
                || shipment.reception().isPresent() || !shipment.execution().equals(observed.identity().execution())
                || state.actorLocations().get(shipment.execution().actorId()).condition().status() != ActorLifeStatus.DEAD)
            throw new IllegalArgumentException("cargo disposition has no exact dead courier and settled physical boundary");
        ActorBodyAuthority.requireRetiredDeath(state, observed.identity().body());
        state.actorExecutions().requireRetained(shipment.execution());
        ShipmentAuthorizationComposition.validate(state, shipment, false);
        var resources = state.inventory().fungibleResources();
        var bindings = resources.bindings().values().stream().filter(b -> b.accountId().equals(shipment.carriedAccountId())).toList();
        var body = ActorBodyId.entityId(state.bootstrap().worldId(), shipment.execution().actorId());
        if (bindings.size() != 1 || bindings.getFirst().authorityEpoch() != observed.identity().body().physicalEpoch()
                || !bindings.getFirst().address().equals(new PhysicalStackAddress.ActorHand(shipment.execution().actorId(), body, ActorContainerItemOrder.Hand.MAIN))
                || !bindings.getFirst().lotQuantities().equals(shipment.lotQuantities())
                || !bindings.getFirst().claimQuantities().equals(Map.of(shipment.authorization().claimId(), shipment.quantity())))
            throw new IllegalArgumentException("cargo disposition lost its exact pre-loot bound allocation");
        long epoch = observed.identity().body().physicalEpoch();
        resources = switch (observed.outcome()) {
            case WORLD_DROP -> resources.releaseClaims(Set.of(shipment.authorization().claimId()))
                    .releaseObservedActorAccountToWorld(shipment.carriedAccountId(), shipment.execution().actorId(), body, epoch, observed.worldCarrier().orElseThrow());
            case MISSING_BEFORE_LOOT -> resources.destroyObserved(shipment.carriedAccountId(), epoch,
                    shipment.lotQuantities(), Map.of(shipment.authorization().claimId(), shipment.quantity()), List.of());
        };
        var movements = new LinkedHashMap<>(state.actorMovements());
        var movement = movements.get(shipment.execution().actorId());
        if (movement != null && !movement.executionId().equals(shipment.execution()))
            throw new IllegalArgumentException("cargo disposition cannot erase a successor's movement");
        movements.remove(shipment.execution().actorId());
        var changes = ShipmentAuthorizationComposition.allocationDisposed(state, shipment, state.inventory().withFungibleResources(resources))
                .shipments(state.shipments().replace(shipment, shipment.withStatus(Shipment.Status.CARGO_DISPOSED)))
                .actorExecutions(ActorExecutionComposition.LIFECYCLE.retire(state, shipment.execution().actorId(), shipment.execution().activityKind(), shipment.id()))
                .actorMovements(movements);
        return state.withChanges(changes);
    }
}
