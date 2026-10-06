package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionId;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorActivityKind;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Logistics owns the transport obligation; its resource allocation and actor body live elsewhere. */
public record Shipment(SubjectId id, ResourceClaimDelegation authorization, ActorExecutionId execution,
                       ShipmentEndpoint sender, ShipmentEndpoint receiver, SubjectId sourceAccountId,
                       SubjectId carriedAccountId, SubjectId receivingAccountId, String itemKind,
                       Map<SubjectId, Integer> lotQuantities, Status status, long revision,
                       Optional<ShipmentPhysicalStep> pendingPhysicalStep, Optional<ShipmentReception> reception) {
    public enum Status { AWAITING_LOAD, CARRYING, DELIVERED, ALLOCATION_WITHDRAWN, CARGO_DISPOSED }
    public Shipment {
        Objects.requireNonNull(id); Objects.requireNonNull(authorization); Objects.requireNonNull(execution);
        Objects.requireNonNull(sender); Objects.requireNonNull(receiver); Objects.requireNonNull(sourceAccountId);
        Objects.requireNonNull(carriedAccountId); Objects.requireNonNull(receivingAccountId);
        Objects.requireNonNull(itemKind); Objects.requireNonNull(status);
        pendingPhysicalStep = Objects.requireNonNull(pendingPhysicalStep);
        reception = Objects.requireNonNull(reception);
        lotQuantities = Map.copyOf(Objects.requireNonNull(lotQuantities));
        if (!authorization.executorId().equals(id) || !execution.activityOwnerId().equals(id)
                || execution.activityKind() != ActorActivityKind.COURIER || sender.containerId().equals(receiver.containerId())
                || sourceAccountId.equals(carriedAccountId) || carriedAccountId.equals(receivingAccountId)
                || sourceAccountId.equals(receivingAccountId) || revision < 1 || !itemKind.matches("[a-z][a-z0-9_-]{0,31}:[a-z0-9][a-z0-9_./-]{0,127}")
                || lotQuantities.isEmpty() && status != Status.DELIVERED || lotQuantities.size() > 64
                || lotQuantities.values().stream().anyMatch(q -> q == null || q < 1 || q > 64)
                || lotQuantities.values().stream().mapToInt(Integer::intValue).sum() > 64)
            throw new IllegalArgumentException("shipment requires exact endpoints, courier authority and one bounded allocation");
        if (pendingPhysicalStep.isPresent()) {
            var step = pendingPhysicalStep.orElseThrow();
            if (step.status() != status || step.shipmentRevision() != revision || !step.observation().actuation().execution().equals(execution)
                    || reception.isPresent())
                throw new IllegalArgumentException("shipment retains a foreign physical step");
            FungibleResourceLedger.requireSubset(lotQuantities, step.lotQuantities(), "shipment physical portion");
        }
        if (reception.isPresent() && status != Status.CARRYING && status != Status.DELIVERED)
            throw new IllegalArgumentException("unloaded reception has a foreign transport phase");
    }
    public Shipment(SubjectId id, ResourceClaimDelegation authorization, ActorExecutionId execution,
            ShipmentEndpoint sender, ShipmentEndpoint receiver, SubjectId sourceAccountId, SubjectId carriedAccountId,
            SubjectId receivingAccountId, String itemKind, Map<SubjectId, Integer> lots, Status status, long revision) {
        this(id, authorization, execution, sender, receiver, sourceAccountId, carriedAccountId, receivingAccountId,
                itemKind, lots, status, revision, Optional.empty(), Optional.empty());
    }
    public Shipment(SubjectId id, ResourceClaimDelegation authorization, ActorExecutionId execution,
            ShipmentEndpoint sender, ShipmentEndpoint receiver, SubjectId sourceAccountId, SubjectId carriedAccountId,
            SubjectId receivingAccountId, String itemKind, Map<SubjectId, Integer> lots, Status status, long revision,
            Optional<ShipmentPhysicalStep> pending) {
        this(id, authorization, execution, sender, receiver, sourceAccountId, carriedAccountId, receivingAccountId,
                itemKind, lots, status, revision, pending, Optional.empty());
    }
    public Shipment prepare(ShipmentPhysicalStep step) {
        if (terminal() || pendingPhysicalStep.isPresent() || reception.isPresent()) throw new IllegalArgumentException("shipment cannot prepare a second effect");
        return new Shipment(id, authorization, execution, sender, receiver, sourceAccountId, carriedAccountId,
                receivingAccountId, itemKind, lotQuantities, status, revision, Optional.of(step), reception);
    }
    public int quantity() { return lotQuantities.values().stream().mapToInt(Integer::intValue).sum(); }
    public Shipment resumed(ActorExecutionId successor) {
        if (terminal() || pendingPhysicalStep.isPresent() || !execution.actorId().equals(successor.actorId())
                || execution.activityKind() != successor.activityKind() || !id.equals(successor.activityOwnerId())
                || successor.generation() <= execution.generation())
            throw new IllegalArgumentException("shipment continuation has a foreign or stale successor");
        return new Shipment(id, authorization, successor, sender, receiver, sourceAccountId, carriedAccountId,
                receivingAccountId, itemKind, lotQuantities, status, revision, Optional.empty(), reception);
    }
    public io.farfrontier.palemirror.frontier.v3.model.navigation.MovementOrder movementOrder() {
        if (terminal()) throw new IllegalArgumentException("terminal shipment has no movement authority");
        return new io.farfrontier.palemirror.frontier.v3.model.navigation.MovementOrder(id, execution.actorId(),
                status == Status.AWAITING_LOAD ? 0 : 1, revision,
                java.util.List.of(status == Status.AWAITING_LOAD ? sender.station() : receiver.station()),
                TraversalCapability.PEDESTRIAN,
                io.farfrontier.palemirror.frontier.v3.model.navigation.MovementOrder.ArrivalPolicy.EXACT_STATION);
    }
    public boolean terminal() { return status == Status.DELIVERED || status == Status.ALLOCATION_WITHDRAWN || status == Status.CARGO_DISPOSED; }
    public Shipment withStatus(Status next) {
        if (terminal() || !(status == Status.AWAITING_LOAD && (next == Status.CARRYING || next == Status.ALLOCATION_WITHDRAWN)
                || status == Status.CARRYING && next == Status.CARGO_DISPOSED && pendingPhysicalStep.isEmpty() && reception.isEmpty()))
            throw new IllegalArgumentException("shipment transition cannot forget carried resources");
        return new Shipment(id, authorization, execution, sender, receiver, sourceAccountId, carriedAccountId,
                receivingAccountId, itemKind, lotQuantities, next, Math.addExact(revision, 1));
    }
    public ActorContainerItemOrder itemOrder() {
        return itemOrder(lotQuantities);
    }
    public ActorContainerItemOrder itemOrder(Map<SubjectId, Integer> portionLots) {
        if (terminal()) throw new IllegalArgumentException("terminal shipment has no item authority");
        if (reception.isPresent()) throw new IllegalArgumentException("shipment awaits exact recipient acknowledgement");
        FungibleResourceLedger.requireSubset(lotQuantities, portionLots, "shipment declared portion");
        if (status == Status.AWAITING_LOAD && !portionLots.equals(lotQuantities))
            throw new IllegalArgumentException("shipment pickup must retain its entire authorized load");
        boolean take = status == Status.AWAITING_LOAD;
        ShipmentEndpoint endpoint = take ? sender : receiver;
        var portion = new ActorContainerItemOrder.Portion.Fungible(
                take ? sourceAccountId : carriedAccountId,
                take ? new ResourceCustody.Container(sender.containerId()) : new ResourceCustody.Actor(execution.actorId()),
                take ? carriedAccountId : receivingAccountId,
                take ? new ResourceCustody.Actor(execution.actorId()) : new ResourceCustody.Container(receiver.containerId()),
                Optional.of(authorization.claimId()), itemKind, portionLots, Optional.of(authorization));
        return new ActorContainerItemOrder(id, execution.actorId(), take ? ActorContainerItemOrder.Direction.TAKE
                : ActorContainerItemOrder.Direction.PLACE, portion,
                new ActorContainerItemOrder.ContainerEndpoint.FungibleContainer(endpoint.containerId()), endpoint.station(),
                ActorContainerItemOrder.Hand.MAIN, take ? 0 : 1, revision);
    }
    public Shipment unloaded(ShipmentReception receipt) {
        if (status != Status.CARRYING || reception.isPresent()
                || !ShipmentReception.unloaded(this, receipt.lotQuantities()).equals(receipt))
            throw new IllegalArgumentException("shipment unload has stale or competing reception evidence");
        FungibleResourceLedger.requireSubset(lotQuantities, receipt.lotQuantities(), "unloaded portion");
        var remaining = new java.util.LinkedHashMap<>(lotQuantities);
        receipt.lotQuantities().forEach((lot, quantity) -> {
            int left = remaining.get(lot) - quantity;
            if (left == 0) remaining.remove(lot); else remaining.put(lot, left);
        });
        return new Shipment(id, authorization, execution, sender, receiver, sourceAccountId, carriedAccountId,
                receivingAccountId, itemKind, remaining, remaining.isEmpty() ? Status.DELIVERED : Status.CARRYING,
                Math.addExact(revision, 1), Optional.empty(), Optional.of(receipt));
    }
    public Shipment acknowledged(SubjectId receiptId) {
        if (reception.isEmpty() || !reception.orElseThrow().id().equals(receiptId) || pendingPhysicalStep.isPresent())
            throw new IllegalArgumentException("shipment acknowledgement has no exact outstanding reception");
        return new Shipment(id, authorization, execution, sender, receiver, sourceAccountId, carriedAccountId,
                receivingAccountId, itemKind, lotQuantities, status, Math.addExact(revision, 1), Optional.empty(), Optional.empty());
    }
}
