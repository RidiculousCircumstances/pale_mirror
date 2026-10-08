package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.*;

/** Transport's durable resource effects. Common bodies alone record pose; ledgers alone move stock. */
public final class ShipmentPhysicalStateSupport {
    public enum PreparationAdmission { READY, WAITING_FOR_CUSTODY, WAITING_FOR_SERVICE, WAITING_FOR_RESOURCE_LAYOUT }

    /** Read-only start gate shared by the HOT adapter and the authoritative command owner. */
    public static PreparationAdmission preparationAdmission(FrontierWorldState state, Shipment shipment) {
        var container = shipment.itemOrder().containerEndpoint().containerId();
        if (!ReferenceContainerCustody.hasOperationalCustody(state, container)
                || shipment.mobileContainerId().filter(attached ->
                    !ReferenceContainerCustody.hasOperationalCustody(state, attached)).isPresent())
            return PreparationAdmission.WAITING_FOR_CUSTODY;
        if (!ShipmentServiceAccess.available(state, shipment)) return PreparationAdmission.WAITING_FOR_SERVICE;
        return switch (MaterialSourcePreparation.review(state, shipment.itemOrder()).status()) {
            case READY -> PreparationAdmission.READY;
            case WAITING_FOR_CUSTODY -> PreparationAdmission.WAITING_FOR_CUSTODY;
            case WAITING_FOR_LAYOUT -> PreparationAdmission.WAITING_FOR_RESOURCE_LAYOUT;
        };
    }

    public static FrontierWorldState handCustody(FrontierWorldState state, SubjectId subject, ShipmentHandCustodyObserved observed) {
        Shipment shipment = state.shipments().shipments().get(observed.shipmentId());
        if (shipment == null || !subject.equals(shipment.id()) || shipment.status() != Shipment.Status.CARRYING
                || shipment.mobileContainerId().isPresent()
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
        if (shipment.reception().isPresent() || shipment.terminal()) return Optional.empty();
        if (shipment.status() == Shipment.Status.AWAITING_LOAD) return shipment.mobileContainerId().flatMap(container ->
                ContainerMaterialDestination.selectWhole(state, container, shipment.itemKind(), shipment.lotQuantities(), Optional.empty()))
                .map(d -> new Destination(d.slot(), d.before(), d.lots()));
        var container = shipment.receiver().containerId();
        int allowed = ShipmentStateSupport.coldTransferLots(state, shipment).values().stream().mapToInt(Integer::intValue).sum();
        if (allowed == 0) return Optional.empty();
        return ContainerMaterialDestination.select(state, container, shipment.itemKind(), shipment.lotQuantities(), allowed,
                ShipmentAuthorizationComposition.capacityCompletionOwner(shipment)).map(d -> new Destination(d.slot(), d.before(), d.lots()));
    }
    public static FrontierWorldState prepare(FrontierWorldState state, SubjectId subject, ShipmentHotPrepared prepared) {
        Shipment shipment = require(state, subject, prepared.shipmentId(), prepared.step());
        if (shipment.pendingPhysicalStep().isPresent()) throw new IllegalArgumentException("shipment already retains a physical effect");
        var order = shipment.itemOrder(prepared.step().lotQuantities());
        requireStorage(state, shipment);
        var admission = preparationAdmission(state, shipment);
        if (admission != PreparationAdmission.READY)
            throw new IllegalArgumentException("shipment cannot prepare: " + admission + " shipment="
                    + shipment.id().value() + " actor=" + shipment.execution().actorId().value()
                    + " container=" + order.containerEndpoint().containerId().value());
        if (!MaterialSourcePreparation.review(state, order).requireReady().equals(prepared.step().source()))
            throw new IllegalArgumentException("shipment effect source differs from current resource bindings");
        if (shipment.status() == Shipment.Status.CARRYING && shipment.mobileContainerId().isEmpty() && (prepared.step().source().size() != 1
                || !prepared.step().source().getFirst().address().equals(new PhysicalStackAddress.ActorHand(shipment.execution().actorId(),
                    io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyId.entityId(state.bootstrap().worldId(), shipment.execution().actorId()),
                    ActorContainerItemOrder.Hand.MAIN))))
            throw new IllegalArgumentException("shipment unload has a foreign physical source hand");
        long destinationEpoch = destinationEpoch(state, shipment, prepared.step().observation().actuation().body().physicalEpoch());
        boolean containerDestination = shipment.status() == Shipment.Status.CARRYING || shipment.mobileContainerId().isPresent();
        if (prepared.step().destinationEpoch() != destinationEpoch
                || !containerDestination && (prepared.step().destinationSlot() != -1 || prepared.step().destinationBefore() != 0)
                || containerDestination && !destination(state, shipment).equals(Optional.of(new Destination(
                    prepared.step().destinationSlot(), prepared.step().destinationBefore(), prepared.step().lotQuantities()))))
            throw new IllegalArgumentException("shipment effect has no exact available receiving surface");
        return state.withChanges(FrontierWorldStateUpdate.begin().shipments(state.shipments().replace(shipment, shipment.prepare(prepared.step()))));
    }
    public static FrontierWorldState observed(FrontierWorldState state, SubjectId subject, ShipmentHotTransferred receipt) {
        Shipment shipment = require(state, subject, receipt.shipmentId(), receipt.step());
        if (!shipment.pendingPhysicalStep().equals(Optional.of(receipt.step())))
            throw new IllegalArgumentException("shipment physical receipt lacks its exact durable pre-effect declaration");
        var order = shipment.itemOrder(receipt.step().lotQuantities());
        // Generic custody validates full stock layouts; the owner also requires this exact body/port.
        if (shipment.status() == Shipment.Status.AWAITING_LOAD && shipment.mobileContainerId().isEmpty()) {
            var hand = new PhysicalStackAddress.ActorHand(shipment.execution().actorId(),
                    io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyId.entityId(state.bootstrap().worldId(), shipment.execution().actorId()),
                    ActorContainerItemOrder.Hand.MAIN);
            if (!receipt.destination().equals(List.of(new FungiblePhysicalObservation.Stack(hand, shipment.itemKind(), shipment.quantity()))))
                throw new IllegalArgumentException("shipment pickup did not enter the declared physical courier hand");
        } else {
            var targetContainer = shipment.status() == Shipment.Status.AWAITING_LOAD ? shipment.mobileContainerId().orElseThrow() : shipment.receiver().containerId();
            var target = new PhysicalStackAddress.ContainerSlot(new InventoryCustody.ContainerSlot(targetContainer, receipt.step().destinationSlot()));
            int remaining = shipment.quantity() - order.portion().quantity();
            var expectedHand = remaining == 0 ? List.<FungiblePhysicalObservation.Stack>of() : List.of(new FungiblePhysicalObservation.Stack(
                    receipt.step().source().getFirst().address(), shipment.itemKind(), remaining));
            if (shipment.status() == Shipment.Status.CARRYING && shipment.mobileContainerId().isEmpty() && !receipt.remainingSource().equals(expectedHand)
                    || receipt.destination().stream().noneMatch(stack -> stack.address().equals(target)
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
    public static long destinationEpoch(FrontierWorldState state, Shipment shipment, long bodyEpoch) {
        if (shipment.status() == Shipment.Status.AWAITING_LOAD && shipment.mobileContainerId().isEmpty()) return bodyEpoch;
        var container = shipment.status() == Shipment.Status.AWAITING_LOAD ? shipment.mobileContainerId().orElseThrow() : shipment.receiver().containerId();
        return state.replicaCustody().custodyByScope().get(ReferenceContainerCustody.scopeId(container)).authorityEpoch();
    }
    private static void requireStorage(FrontierWorldState state, Shipment shipment) {
        shipment.mobileContainerId().ifPresent(container -> {
            var surface = state.inventory().surfaces().get(container);
            if (surface == null || !surface.location().equals(new ContainerLocation.Mobile(shipment.execution().actorId()))
                    || !ReferenceContainerCustody.hasOperationalCustody(state, container))
                throw new IllegalArgumentException("shipment lacks its exact operational attached storage");
        });
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
