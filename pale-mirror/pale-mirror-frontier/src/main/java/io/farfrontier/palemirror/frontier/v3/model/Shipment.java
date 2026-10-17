package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionId;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorActivityKind;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Logistics owns the transport obligation; its resource allocation and actor body live elsewhere. */
public record Shipment(SubjectId id, ResourceClaimDelegation authorization, ActorExecutionId execution,
                       ResourceSourceEndpoint sender, ShipmentEndpoint receiver, SubjectId sourceAccountId,
                       SubjectId carriedAccountId, SubjectId receivingAccountId, String itemKind,
                       Map<SubjectId, Integer> lotQuantities, Status status, long revision,
                       Optional<ShipmentPhysicalStep> pendingPhysicalStep, Optional<ShipmentReception> reception,
                       Optional<SubjectId> transportMissionId, Optional<SubjectId> mobileContainerId) {
    public enum Status { AWAITING_LOAD, CARRYING, DELIVERED, ALLOCATION_WITHDRAWN, CARGO_DISPOSED, RETURNING, RETURNED }
    public Shipment {
        Objects.requireNonNull(id); Objects.requireNonNull(authorization); Objects.requireNonNull(execution);
        Objects.requireNonNull(sender); Objects.requireNonNull(receiver); Objects.requireNonNull(sourceAccountId);
        Objects.requireNonNull(carriedAccountId); Objects.requireNonNull(receivingAccountId);
        Objects.requireNonNull(itemKind); Objects.requireNonNull(status);
        pendingPhysicalStep = Objects.requireNonNull(pendingPhysicalStep);
        reception = Objects.requireNonNull(reception);
        transportMissionId = Objects.requireNonNull(transportMissionId);
        mobileContainerId = Objects.requireNonNull(mobileContainerId, "explicit carried storage");
        lotQuantities = Map.copyOf(Objects.requireNonNull(lotQuantities));
        boolean ownExecution = execution.activityKind() == ActorActivityKind.COURIER && execution.activityOwnerId().equals(id);
        boolean groupExecution = execution.activityKind() == ActorActivityKind.GROUP_MEMBER && transportMissionId.isPresent();
        if (!authorization.executorId().equals(id) || !(ownExecution || groupExecution)
                || sender.container().filter(receiver.containerId()::equals).isPresent()
                || sourceAccountId.equals(carriedAccountId) || carriedAccountId.equals(receivingAccountId)
                || sourceAccountId.equals(receivingAccountId) || revision < 1 || !itemKind.matches("[a-z][a-z0-9_-]{0,31}:[a-z0-9][a-z0-9_./-]{0,127}")
                || lotQuantities.isEmpty() && status != Status.DELIVERED && status != Status.RETURNED || lotQuantities.size() > 64
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
            ResourceSourceEndpoint sender, ShipmentEndpoint receiver, SubjectId sourceAccountId, SubjectId carriedAccountId,
            SubjectId receivingAccountId, String itemKind, Map<SubjectId, Integer> lots, Status status, long revision,
            Optional<ShipmentPhysicalStep> pending, Optional<ShipmentReception> reception, Optional<SubjectId> mission) {
        this(id, authorization, execution, sender, receiver, sourceAccountId, carriedAccountId, receivingAccountId,
                itemKind, lots, status, revision, pending, reception, mission, Optional.empty());
    }
    public ResourceCustody carriedCustody() {
        return mobileContainerId.<ResourceCustody>map(ResourceCustody.Container::new)
                .orElseGet(() -> new ResourceCustody.Actor(execution.actorId()));
    }
    public Shipment withMobileStorage(SubjectId containerId, SubjectId accountId) {
        if (status != Status.AWAITING_LOAD || revision != 1 || pendingPhysicalStep.isPresent()
                || reception.isPresent() || mobileContainerId.isPresent())
            throw new IllegalArgumentException("mobile shipment storage must be declared before loading");
        return new Shipment(id, authorization, execution, sender, receiver, sourceAccountId, accountId,
                receivingAccountId, itemKind, lotQuantities, status, revision, pendingPhysicalStep, reception,
                transportMissionId, Optional.of(containerId));
    }
    public Shipment(SubjectId id, ResourceClaimDelegation authorization, ActorExecutionId execution,
            ResourceSourceEndpoint sender, ShipmentEndpoint receiver, SubjectId sourceAccountId, SubjectId carriedAccountId,
            SubjectId receivingAccountId, String itemKind, Map<SubjectId, Integer> lots, Status status, long revision,
            Optional<ShipmentPhysicalStep> pending, Optional<ShipmentReception> reception) {
        this(id, authorization, execution, sender, receiver, sourceAccountId, carriedAccountId, receivingAccountId,
                itemKind, lots, status, revision, pending, reception, Optional.empty());
    }
    public Shipment withMission(SubjectId mission) {
        if (status != Status.AWAITING_LOAD || revision != 1 || transportMissionId.isPresent() || pendingPhysicalStep.isPresent())
            throw new IllegalArgumentException("mission binding must be declared at fresh shipment admission");
        return new Shipment(id, authorization, execution, sender, receiver, sourceAccountId, carriedAccountId,
                receivingAccountId, itemKind, lotQuantities, status, revision, pendingPhysicalStep, reception, Optional.of(mission), mobileContainerId);
    }
    public Shipment(SubjectId id, ResourceClaimDelegation authorization, ActorExecutionId execution,
            ResourceSourceEndpoint sender, ShipmentEndpoint receiver, SubjectId sourceAccountId, SubjectId carriedAccountId,
            SubjectId receivingAccountId, String itemKind, Map<SubjectId, Integer> lots, Status status, long revision) {
        this(id, authorization, execution, sender, receiver, sourceAccountId, carriedAccountId, receivingAccountId,
                itemKind, lots, status, revision, Optional.empty(), Optional.empty());
    }
    public Shipment(SubjectId id, ResourceClaimDelegation authorization, ActorExecutionId execution,
            ResourceSourceEndpoint sender, ShipmentEndpoint receiver, SubjectId sourceAccountId, SubjectId carriedAccountId,
            SubjectId receivingAccountId, String itemKind, Map<SubjectId, Integer> lots, Status status, long revision,
            Optional<ShipmentPhysicalStep> pending) {
        this(id, authorization, execution, sender, receiver, sourceAccountId, carriedAccountId, receivingAccountId,
                itemKind, lots, status, revision, pending, Optional.empty());
    }
    public Shipment prepare(ShipmentPhysicalStep step) {
        if (terminal() || pendingPhysicalStep.isPresent() || reception.isPresent()) throw new IllegalArgumentException("shipment cannot prepare a second effect");
        return new Shipment(id, authorization, execution, sender, receiver, sourceAccountId, carriedAccountId,
                receivingAccountId, itemKind, lotQuantities, status, revision, Optional.of(step), reception, transportMissionId, mobileContainerId);
    }
    public int quantity() { return lotQuantities.values().stream().mapToInt(Integer::intValue).sum(); }
    public boolean carrying() { return status == Status.CARRYING || status == Status.RETURNING; }
    /** Immutable sale destination is not changed when logistics returns unsold cargo. */
    public ShipmentEndpoint unloadingEndpoint() {
        if (status != Status.RETURNING) return receiver;
        if (!(sender instanceof ShipmentEndpoint endpoint)) throw new IllegalArgumentException("return requires a receiving source port");
        return endpoint;
    }
    public SubjectId unloadingAccountId() { return status == Status.RETURNING ? sourceAccountId : receivingAccountId; }
    public Shipment returned(Map<SubjectId, Integer> lots) {
        if (status != Status.RETURNING || reception.isPresent()) throw new IllegalArgumentException("shipment has no declared return");
        FungibleResourceLedger.requireSubset(lotQuantities, lots, "returned cargo portion");
        if (lots.isEmpty()) throw new IllegalArgumentException("empty cargo return");
        var remaining = new java.util.LinkedHashMap<>(lotQuantities);
        lots.forEach((id, quantity) -> { int rest = remaining.get(id) - quantity; if (rest == 0) remaining.remove(id); else remaining.put(id, rest); });
        return new Shipment(id, authorization, execution, sender, receiver, sourceAccountId, carriedAccountId,
                receivingAccountId, itemKind, remaining, remaining.isEmpty() ? Status.RETURNED : Status.RETURNING,
                Math.addExact(revision, 1), Optional.empty(), Optional.empty(), transportMissionId, mobileContainerId);
    }
    public Shipment retargetStorage(ShipmentEndpoint destination, SubjectId account) {
        if (!ShipmentStorageRetargeted.unbegun(this) || !receiver.settlementId().equals(destination.settlementId())
                || receiver.containerId().equals(destination.containerId())) throw new IllegalArgumentException("shipment storage replacement is not safe");
        return new Shipment(id, authorization, execution, sender, destination, sourceAccountId, carriedAccountId, account, itemKind,
                lotQuantities, status, Math.addExact(revision, 1), Optional.empty(), Optional.empty(), transportMissionId, mobileContainerId);
    }
    public Shipment resumed(ActorExecutionId successor) {
        if (terminal() || pendingPhysicalStep.isPresent() || !execution.actorId().equals(successor.actorId())
                || execution.activityKind() != successor.activityKind() || !execution.activityOwnerId().equals(successor.activityOwnerId())
                || successor.generation() <= execution.generation())
            throw new IllegalArgumentException("shipment continuation has a foreign or stale successor");
        return new Shipment(id, authorization, successor, sender, receiver, sourceAccountId, carriedAccountId,
                receivingAccountId, itemKind, lotQuantities, status, revision, Optional.empty(), reception, transportMissionId, mobileContainerId);
    }
    public io.farfrontier.palemirror.frontier.v3.model.navigation.MovementOrder movementOrder() {
        if (terminal()) throw new IllegalArgumentException("terminal shipment has no movement authority");
        return new io.farfrontier.palemirror.frontier.v3.model.navigation.MovementOrder(id, execution.actorId(),
                status == Status.AWAITING_LOAD ? 0 : 1, revision,
                java.util.List.of(status == Status.AWAITING_LOAD ? sender.station() : unloadingEndpoint().station()),
                TraversalCapability.PEDESTRIAN,
                io.farfrontier.palemirror.frontier.v3.model.navigation.MovementOrder.ArrivalPolicy.EXACT_STATION);
    }
    public boolean terminal() { return status == Status.DELIVERED || status == Status.ALLOCATION_WITHDRAWN || status == Status.CARGO_DISPOSED || status == Status.RETURNED; }
    public Shipment withStatus(Status next) {
        if (terminal() || !(status == Status.AWAITING_LOAD && (next == Status.CARRYING || next == Status.ALLOCATION_WITHDRAWN)
                || carrying() && next == Status.CARGO_DISPOSED && pendingPhysicalStep.isEmpty() && reception.isEmpty()
                || status == Status.CARRYING && next == Status.RETURNING && pendingPhysicalStep.isEmpty() && reception.isEmpty()))
            throw new IllegalArgumentException("shipment transition cannot forget carried resources");
        return new Shipment(id, authorization, execution, sender, receiver, sourceAccountId, carriedAccountId,
                receivingAccountId, itemKind, lotQuantities, next, Math.addExact(revision, 1), Optional.empty(), Optional.empty(), transportMissionId, mobileContainerId);
    }
    public ActorResourceItemOrder itemOrder() {
        return itemOrder(lotQuantities);
    }
    public ActorResourceItemOrder itemOrder(Map<SubjectId, Integer> portionLots) {
        if (terminal()) throw new IllegalArgumentException("terminal shipment has no item authority");
        if (reception.isPresent()) throw new IllegalArgumentException("shipment awaits exact recipient acknowledgement");
        FungibleResourceLedger.requireSubset(lotQuantities, portionLots, "shipment declared portion");
        if (status == Status.AWAITING_LOAD && !portionLots.equals(lotQuantities))
            throw new IllegalArgumentException("shipment pickup must retain its entire authorized load");
        boolean take = status == Status.AWAITING_LOAD;
        var portion = new ActorContainerItemOrder.Portion.Fungible(
                take ? sourceAccountId : carriedAccountId,
                take ? sender.custody() : carriedCustody(),
                take ? carriedAccountId : unloadingAccountId(),
                take ? carriedCustody() : new ResourceCustody.Container(unloadingEndpoint().containerId()),
                Optional.of(authorization.claimId()), itemKind, portionLots, Optional.of(authorization));
        var slot = mobileContainerId.<ActorItemSlot>map(ActorItemSlot.AttachedStorage::new)
                .orElseGet(() -> new ActorItemSlot.Hand(ActorContainerItemOrder.Hand.MAIN));
        return take ? ResourceSourceComposition.pickup(sender, id, execution.actorId(), portion, slot, 0, revision)
                : new ActorContainerItemOrder(id, execution.actorId(), ActorContainerItemOrder.Direction.PLACE, portion,
                    unloadingEndpoint().receivingPort(), unloadingEndpoint().station(), slot, 1, revision);
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
                Math.addExact(revision, 1), Optional.empty(), Optional.of(receipt), transportMissionId, mobileContainerId);
    }
    public Shipment acknowledged(SubjectId receiptId) {
        if (reception.isEmpty() || !reception.orElseThrow().id().equals(receiptId) || pendingPhysicalStep.isPresent())
            throw new IllegalArgumentException("shipment acknowledgement has no exact outstanding reception");
        return new Shipment(id, authorization, execution, sender, receiver, sourceAccountId, carriedAccountId,
                receivingAccountId, itemKind, lotQuantities, status, Math.addExact(revision, 1), Optional.empty(), Optional.empty(), transportMissionId, mobileContainerId);
    }
}
