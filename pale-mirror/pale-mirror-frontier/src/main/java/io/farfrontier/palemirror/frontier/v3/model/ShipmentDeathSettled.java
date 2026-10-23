package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorActuationId;
import java.util.Objects;

/** Releases an uncollected allocation, never a carried or possibly transferred cargo. */
public record ShipmentDeathSettled(SubjectId shipmentId, long expectedRevision, ActorActuationId identity) implements FrontierPayload {
    public ShipmentDeathSettled {
        Objects.requireNonNull(shipmentId); Objects.requireNonNull(identity);
        if (expectedRevision < 1) throw new IllegalArgumentException("invalid shipment death revision");
    }
    @Override public String type() { return "frontier.shipment_death_settled"; }
    @Override public boolean requiresDurableBeforeEffect() { return true; }
    public FrontierWorldState apply(FrontierWorldState state, SubjectId subject) {
        ActorBodyAuthority.requireRetiredDeath(state, identity.body());
        state.actorExecutions().requireRetained(identity.execution());
        var shipment = state.shipments().shipments().get(shipmentId);
        if (shipment == null || !subject.equals(shipmentId) || shipment.revision() != expectedRevision
                || !shipment.execution().equals(identity.execution()) || shipment.status() != Shipment.Status.AWAITING_LOAD
                || shipment.pendingPhysicalStep().isPresent() || shipment.reception().isPresent())
            throw new IllegalArgumentException("dead courier still owns a physical effect or collected cargo");
        var inventory = shipment.cargo() instanceof ShipmentCargo.Individual ? state.inventory()
                : state.inventory().withFungibleResources(state.inventory().fungibleResources().releaseClaims(java.util.Set.of(shipment.authorization().claimId())));
        var changes = ShipmentAuthorizationComposition.allocationDisposed(state, shipment, inventory)
                .shipments(state.shipments().replace(shipment, shipment.withStatus(Shipment.Status.ALLOCATION_WITHDRAWN)))
                .actorExecutions(TransportCargoExecution.afterParcelClosed(state, shipment));
        return state.withChanges(changes);
    }
}
