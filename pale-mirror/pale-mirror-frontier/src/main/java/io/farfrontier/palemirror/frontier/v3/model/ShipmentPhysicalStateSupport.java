package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.*;

/** Transport's durable resource effects. Common bodies alone record pose; ledgers alone move stock. */
public final class ShipmentPhysicalStateSupport {
    public static FrontierWorldState handCustody(FrontierWorldState state, SubjectId subject, ShipmentHandCustodyObserved observed) {
        Shipment shipment = state.shipments().shipments().get(observed.shipmentId());
        if (shipment == null || !subject.equals(shipment.id()) || shipment.status() != Shipment.Status.CARRYING
                || shipment.pendingPhysicalStep().isPresent() || !shipment.execution().equals(observed.identity().execution()))
            throw new IllegalArgumentException("shipment hand boundary has no exact carrying obligation");
        state.actorExecutions().requireRetained(shipment.execution());
        var body = observed.identity().body();
        var fence = ActorBodyAuthority.require(state, body);
        if (!body.equals(ActorBodyAuthority.current(state, shipment.execution().actorId()))
                || !observed.hand().address().equals(new PhysicalStackAddress.ActorHand(body.actorId(),
                    io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyId.entityId(state.bootstrap().worldId(), body.actorId()),
                    ActorContainerItemOrder.Hand.MAIN)) || !observed.hand().itemKind().equals(shipment.itemKind())
                || observed.hand().quantity() != shipment.quantity())
            throw new IllegalArgumentException("shipment hand boundary names a foreign body/stack");
        var ledger = state.inventory().fungibleResources();
        var bindings = ledger.bindings().values().stream().filter(binding -> binding.accountId().equals(shipment.carriedAccountId())).toList();
        FungibleResourceLedger replacement;
        switch (observed.boundary()) {
            case MATERIALIZED -> {
                ActorBodyAuthority.requireActuation(state, observed.identity());
                var lease = state.ambientLeases().get(body.actorId());
                if (lease == null || lease.status() != AmbientLeaseStatus.HOT || !bindings.isEmpty())
                    throw new IllegalArgumentException("shipment hand materialization has no unbound HOT account");
                replacement = ledger.rebind(shipment.carriedAccountId(), body.physicalEpoch(),
                        FungiblePhysicalObservation.bind(ledger, shipment.carriedAccountId(), body.physicalEpoch(), java.util.List.of(observed.hand())));
            }
            case SAVED_DEPARTURE -> {
                if (fence.phase() != FencedRecoveryPhase.RUNNING && fence.phase() != FencedRecoveryPhase.AMBIGUOUS
                        || bindings.size() != 1 || bindings.getFirst().authorityEpoch() != body.physicalEpoch()
                        || !bindings.getFirst().address().equals(observed.hand().address())
                        || !bindings.getFirst().lotQuantities().equals(shipment.lotQuantities())
                        || !bindings.getFirst().claimQuantities().equals(java.util.Map.of(shipment.authorization().claimId(), shipment.quantity())))
                    throw new IllegalArgumentException("shipment saved hand departure lacks its current exact binding");
                replacement = ledger.releaseBindings(shipment.carriedAccountId(), body.physicalEpoch());
            }
            default -> throw new IllegalArgumentException("unregistered shipment hand boundary");
        }
        return state.withInventory(state.inventory().withFungibleResources(replacement));
    }
    public static OptionalInt destinationSlot(FrontierWorldState state, Shipment shipment) {
        return destination(state, shipment).map(d -> OptionalInt.of(d.slot())).orElseGet(OptionalInt::empty);
    }
    public record Destination(int slot, int before, Map<SubjectId, Integer> lots) { }
    public static Optional<Destination> destination(FrontierWorldState state, Shipment shipment) {
        if (shipment.status() != Shipment.Status.CARRYING || shipment.reception().isPresent()) return Optional.empty();
        var container = shipment.receiver().containerId();
        int allowed = ShipmentStateSupport.coldTransferLots(state, shipment).values().stream().mapToInt(Integer::intValue).sum();
        if (allowed == 0) return Optional.empty();
        var record = state.inventory().containers().get(container);
        for (int slot = 0; slot < record.slotCount(); slot++) {
            if (state.reservedContainerSlots(container).contains(slot)
                    || state.inventory().occupiedSlots().containsKey(new InventoryCustody.ContainerSlot(container, slot))) continue;
            var stack = ReferenceContainerCustody.expectedFungibleSlot(state, container, slot);
            if (stack.isPresent() && !stack.orElseThrow().itemKind().equals(shipment.itemKind())) continue;
            int before = stack.map(ReferenceContainerCustody.ProjectedFungibleSlot::quantity).orElse(0);
            int take = Math.min(allowed, 64 - before);
            if (take > 0) return Optional.of(new Destination(slot, before, ShipmentStateSupport.portion(shipment.lotQuantities(), take)));
        }
        return Optional.empty();
    }
    public static FrontierWorldState prepare(FrontierWorldState state, SubjectId subject, ShipmentHotPrepared prepared) {
        Shipment shipment = require(state, subject, prepared.shipmentId(), prepared.step());
        if (shipment.pendingPhysicalStep().isPresent()) throw new IllegalArgumentException("shipment already retains a physical effect");
        var order = shipment.itemOrder(prepared.step().lotQuantities());
        if (!ReferenceContainerCustody.hasOperationalCustody(state, order.containerEndpoint().containerId())
                || !ShipmentServiceAccess.available(state, shipment))
            throw new IllegalArgumentException("shipment cannot prepare without current container custody and its service turn");
        if (!MaterialSourceSelection.select(state.inventory().fungibleResources(), order).equals(prepared.step().source()))
            throw new IllegalArgumentException("shipment effect source differs from current resource bindings");
        if (shipment.status() == Shipment.Status.CARRYING && (prepared.step().source().size() != 1
                || !prepared.step().source().getFirst().address().equals(new PhysicalStackAddress.ActorHand(shipment.execution().actorId(),
                    io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyId.entityId(state.bootstrap().worldId(), shipment.execution().actorId()),
                    ActorContainerItemOrder.Hand.MAIN))))
            throw new IllegalArgumentException("shipment unload has a foreign physical source hand");
        long destinationEpoch = shipment.status() == Shipment.Status.AWAITING_LOAD
                ? prepared.step().observation().actuation().body().physicalEpoch()
                : state.replicaCustody().custodyByScope().get(ReferenceContainerCustody.scopeId(shipment.receiver().containerId())).authorityEpoch();
        if (prepared.step().destinationEpoch() != destinationEpoch
                || shipment.status() == Shipment.Status.CARRYING
                    && (!destination(state, shipment).equals(Optional.of(new Destination(prepared.step().destinationSlot(), prepared.step().destinationBefore(), prepared.step().lotQuantities())))
                        || !ContainerStorageAdmission.receive(state, shipment.receiver().containerId(), shipment.itemKind(), order.portion().quantity(),
                            ShipmentAuthorizationComposition.capacityCompletionOwner(shipment))))
            throw new IllegalArgumentException("shipment effect has no exact available receiving surface");
        return state.withChanges(FrontierWorldStateUpdate.begin().shipments(state.shipments().replace(shipment, shipment.prepare(prepared.step()))));
    }
    public static FrontierWorldState observed(FrontierWorldState state, SubjectId subject, ShipmentHotTransferred receipt) {
        Shipment shipment = require(state, subject, receipt.shipmentId(), receipt.step());
        if (!shipment.pendingPhysicalStep().equals(Optional.of(receipt.step())))
            throw new IllegalArgumentException("shipment physical receipt lacks its exact durable pre-effect declaration");
        var order = shipment.itemOrder(receipt.step().lotQuantities());
        // Generic custody validates full stock layouts; the owner also requires this exact body/port.
        if (shipment.status() == Shipment.Status.AWAITING_LOAD) {
            var hand = new PhysicalStackAddress.ActorHand(shipment.execution().actorId(),
                    io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyId.entityId(state.bootstrap().worldId(), shipment.execution().actorId()),
                    ActorContainerItemOrder.Hand.MAIN);
            if (!receipt.destination().equals(List.of(new FungiblePhysicalObservation.Stack(hand, shipment.itemKind(), shipment.quantity()))))
                throw new IllegalArgumentException("shipment pickup did not enter the declared physical courier hand");
        } else {
            var target = new PhysicalStackAddress.ContainerSlot(new InventoryCustody.ContainerSlot(shipment.receiver().containerId(), receipt.step().destinationSlot()));
            int remaining = shipment.quantity() - order.portion().quantity();
            var expectedHand = remaining == 0 ? List.<FungiblePhysicalObservation.Stack>of() : List.of(new FungiblePhysicalObservation.Stack(
                    receipt.step().source().getFirst().address(), shipment.itemKind(), remaining));
            if (!receipt.remainingSource().equals(expectedHand) || receipt.destination().stream().noneMatch(stack -> stack.address().equals(target)
                    && stack.itemKind().equals(shipment.itemKind()) && stack.quantity() == receipt.step().destinationBefore() + order.portion().quantity()))
                throw new IllegalArgumentException("shipment unload did not preserve its exact remaining hand and receiver portion");
        }
        var transfer = ActorItemCustody.transferObservedUpdate(state, order, receipt.step().sourceEpoch(),
                receipt.step().destinationEpoch(), receipt.remainingSource(), receipt.destination());
        Shipment replacement = shipment.status() == Shipment.Status.AWAITING_LOAD ? shipment.withStatus(Shipment.Status.CARRYING)
                : shipment.unloaded(ShipmentReception.unloaded(shipment, receipt.step().lotQuantities()));
        var changes = transfer.shipments(state.shipments().replace(shipment, replacement));
        if (replacement.terminal()) changes.actorExecutions(ActorExecutionComposition.LIFECYCLE.retire(state,
                shipment.execution().actorId(), shipment.execution().activityKind(), shipment.id()));
        return state.withChanges(changes);
    }
    private static Shipment require(FrontierWorldState state, SubjectId subject, SubjectId id, ShipmentPhysicalStep step) {
        Shipment shipment = state.shipments().shipments().get(id);
        if (shipment == null || shipment.terminal() || !subject.equals(id) || shipment.status() != step.status()
                || shipment.revision() != step.shipmentRevision() || state.actorMovements().containsKey(shipment.execution().actorId()))
            throw new IllegalArgumentException("shipment physical effect has stale identity or an unfinished journey");
        ShipmentStateSupport.validateOrder(state, shipment.itemOrder(step.lotQuantities()));
        var actor = shipment.execution().actorId(); var lease = state.ambientLeases().get(actor);
        if (lease == null || lease.status() != AmbientLeaseStatus.HOT || lease.revision() != step.observation().scopeRevision()
                || ActorExecutionCoordinator.sceneOwns(state, actor))
            throw new IllegalArgumentException("shipment effect lacks its exact loaded presentation scope");
        step.observation().require(state, shipment.execution(), lease.revision(), shipment.itemOrder().station().standingBody());
        return shipment;
    }
    private ShipmentPhysicalStateSupport() { }
}
