package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.CommandResult;
import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorActuationId;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyId;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorHotObservation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import java.util.Comparator;
import java.util.List;

/** Registered logistics effects. Shared body/navigation/custody owners retain their own authority. */
final class FrontierV3ShipmentPhysicalExecutor {
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    private FrontierV3ShipmentPhysicalExecutor() { }

    static void tick(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        var state = runtime.decodedState().orElse(null);
        if (state == null) return;
        for (var shipment : state.shipments().shipments().values().stream().filter(value -> !value.terminal())
                .sorted(Comparator.comparing(Shipment::id)).toList()) {
            if (progress(level, runtime, state, shipment)) return;
        }
    }

    private static boolean progress(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
            FrontierWorldState state, Shipment shipment) {
        if (shipment.transportMissionId().map(state.shipments().missions()::get).flatMap(TransportMission::supplies)
                .filter(load -> !load.complete()).isPresent()) return false;
        var actor = shipment.execution().actorId();
        var entity = level.getEntity(ActorBodyId.entityId(state.bootstrap().worldId(), actor));
        if (entity == null) return savedDeparture(level, runtime, state, shipment);
        if (!(entity instanceof Mob worker) || !worker.isAlive()
                || !FrontierV3ActorBodyController.recognizesRecordedBody(level, state, worker)) return false;
        var lease = state.ambientLeases().get(actor);
        if (lease == null || lease.status() != AmbientLeaseStatus.HOT || ActorExecutionCoordinator.sceneOwns(state, actor)) return false;
        FrontierV3ActorActuation actuation;
        try { actuation = FrontierV3ActorActuation.capture(state, worker, shipment.execution(), runtime::decodedState); }
        catch (IllegalArgumentException stale) { return false; }
        if (!FrontierV3ActorBodyController.inspectCurrent(level, runtime, worker) || !actuation.current(worker)) return false;
        state = runtime.decodedState().orElseThrow();
        if (!shipment.equals(state.shipments().shipments().get(shipment.id()))) return false;
        var bindings = state.inventory().fungibleResources().bindings().values().stream()
                .filter(binding -> binding.accountId().equals(shipment.carriedAccountId())).toList();
        if (shipment.mobileContainerId().isEmpty() && shipment.status() == Shipment.Status.CARRYING && bindings.isEmpty() && shipment.pendingPhysicalStep().isEmpty()) {
            if (!FrontierV3ActorCarryProjection.witnessed(state, actor, worker)) return false;
            return accepted(level, runtime, shipment, "shipment-hand-materialized", new ShipmentHandCustodyObserved(
                    shipment.id(), actuation.id(), ShipmentHandCustodyObserved.Boundary.MATERIALIZED, hand(shipment, worker.getUUID())));
        }
        if (shipment.reception().isPresent() || state.actorMovements().containsKey(actor)
                || !FrontierV3SurfaceObservation.at(worker, shipment.itemOrder().station())) return false;
        var container = shipment.itemOrder().containerEndpoint().containerId();
        var surface = state.inventory().surfaces().get(container);
        if (surface == null) return false;
        var physical = FrontierV3PhysicalContainer.loaded(level, state, container).orElse(null);
        var attached = shipment.mobileContainerId().isPresent()
                ? FrontierV3PhysicalContainer.loaded(level, state, shipment.mobileContainerId().orElseThrow()).orElse(null) : null;
        if (shipment.mobileContainerId().isPresent() && (attached == null
                || !ReferenceContainerCustody.hasLiveCustody(state, attached.containerId())
                || ReferenceContainerCustody.blocksCanonicalUse(state, attached.containerId()))) return false;
        var chest = physical == null ? null : physical.inventory();
        if (chest == null || !ReferenceContainerCustody.hasLiveCustody(state, container)
                || ReferenceContainerCustody.blocksCanonicalUse(state, container)) return false;
        var pending = shipment.pendingPhysicalStep().orElse(null);
        // A busy service turn is ordinary waiting, not permission to submit a forbidden
        // physical transaction. Retained prepared effects finish under their original fence.
        if (pending == null && ShipmentPhysicalStateSupport.preparationAdmission(state, shipment)
                != ShipmentPhysicalStateSupport.PreparationAdmission.READY) return false;
        ShipmentPhysicalStep step;
        try {
            boolean containerDestination = shipment.status() == Shipment.Status.CARRYING || attached != null;
            var destination = pending == null && containerDestination
                    ? ShipmentPhysicalStateSupport.destination(state, shipment).orElse(null) : null;
            if (containerDestination && pending == null && destination == null) return false;
            var lots = pending != null ? pending.lotQuantities() : destination != null ? destination.lots() : shipment.lotQuantities();
            var order = shipment.itemOrder(lots);
            var source = pending == null ? MaterialSourceSelection.select(state.inventory().fungibleResources(), order) : pending.source();
            int destinationSlot = !containerDestination ? -1
                    : pending == null ? destination.slot() : pending.destinationSlot();
            if (shipment.status() == Shipment.Status.CARRYING && destinationSlot < 0) return false;
            long destinationEpoch = ShipmentPhysicalStateSupport.destinationEpoch(state, shipment, actuation.id().body().physicalEpoch());
            step = pending == null ? new ShipmentPhysicalStep(shipment.status(), shipment.revision(),
                    new ActorHotObservation(actuation.id(), lease.revision()), source, destinationSlot, destinationEpoch,
                    lots, destination == null ? 0 : destination.before()) : pending;
            var transfer = new FrontierV3ActorItemTransfer.FungibleStep(order, physical, attached, worker, worker.getUUID(), source, destinationSlot, step.destinationBefore());
            if (pending == null) {
                if (!transfer.before()) return conflict(runtime, shipment, container, "prepared source does not match its physical preimage");
                return accepted(level, runtime, shipment, "shipment-transfer-prepared", new ShipmentHotPrepared(shipment.id(), step));
            }
            if (!step.observation().actuation().equals(actuation.id()) || step.observation().scopeRevision() != lease.revision())
                return conflict(runtime, shipment, container, "retained physical effect has a stale body/scope fence");
            if (!transfer.after() && (!transfer.before() || !transfer.apply() || !transfer.after()))
                return conflict(runtime, shipment, container, "physical effect matches neither unapplied nor applied evidence");
            var layout = FrontierV3ContainerSurfaceExecutor.observedFungibleSlots(chest, state, container);
            boolean taking = shipment.status() == Shipment.Status.AWAITING_LOAD;
            int remaining = shipment.quantity() - order.portion().quantity();
            var remainingHand = remaining == 0 ? List.<FungiblePhysicalObservation.Stack>of()
                    : List.of(new FungiblePhysicalObservation.Stack(step.source().getFirst().address(), shipment.itemKind(), remaining));
            var carriedLayout = attached == null ? taking ? List.of(hand(shipment, worker.getUUID())) : remainingHand
                    : FrontierV3ContainerSurfaceExecutor.observedFungibleSlots(attached.inventory(), state, attached.containerId());
            var receipt = new ShipmentHotTransferred(shipment.id(), step, taking ? layout : carriedLayout, taking ? carriedLayout : layout);
            boolean committed = accepted(level, runtime, shipment, "shipment-transfer-observed", receipt);
            if (committed) {
                if (!FrontierV3ReferenceContainerCustodyExecutor.checkpointConfirmedContainerMutation(runtime, container, physical)
                        || attached != null && !FrontierV3ReferenceContainerCustodyExecutor.checkpointConfirmedContainerMutation(runtime, attached.containerId(), attached))
                    throw new IllegalStateException("shipment resource effect lacks its next container replica boundary");
                if (attached == null) FrontierV3ActorCarryProjection.rememberConfirmed(runtime.decodedState().orElseThrow(), actor, worker);
            }
            return committed;
        } catch (IllegalArgumentException changed) {
            return conflict(runtime, shipment, container, changed.getMessage());
        }
    }

    private static boolean conflict(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
            Shipment shipment, io.farfrontier.palemirror.frontier.v3.api.SubjectId container, String reason) {
        LOGGER.warn("PMV3 shipment effect conflict: shipment={} revision={} execution={} container={} pending={} reason={}",
                shipment.id(), shipment.revision(), shipment.execution(), container, shipment.pendingPhysicalStep(), reason);
        return FrontierV3ContainerSurfaceExecutor.reportConflict(runtime, container);
    }

    private static boolean savedDeparture(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
            FrontierWorldState state, Shipment shipment) {
        if (shipment.mobileContainerId().isPresent() || shipment.status() != Shipment.Status.CARRYING || shipment.pendingPhysicalStep().isPresent()
                || state.inventory().fungibleResources().bindings().values().stream()
                    .noneMatch(binding -> binding.accountId().equals(shipment.carriedAccountId()))) return false;
        var actor = shipment.execution().actorId();
        var ledger = FrontierV3AmbientCarrierLedger.get(level, state.bootstrap().worldId());
        var departure = ledger.bodyDeparture(actor).orElse(null);
        if (departure == null || !departure.current(state) || !ledger.savedBodyDeparture(departure)
                || !departure.mainhand().equals(java.util.Optional.of(new FrontierV3ActorBodyDeparture.HandStack(shipment.itemKind(), shipment.quantity())))) return false;
        return accepted(level, runtime, shipment, "shipment-hand-saved-departure", new ShipmentHandCustodyObserved(shipment.id(),
                new ActorActuationId(new ActorBodyId(actor, departure.identity().epoch()), shipment.execution()),
                ShipmentHandCustodyObserved.Boundary.SAVED_DEPARTURE, hand(shipment, departure.identity().entityId())));
    }

    private static FungiblePhysicalObservation.Stack hand(Shipment shipment, java.util.UUID body) {
        return new FungiblePhysicalObservation.Stack(new PhysicalStackAddress.ActorHand(shipment.execution().actorId(), body,
                ActorContainerItemOrder.Hand.MAIN), shipment.itemKind(), shipment.quantity());
    }
    private static boolean accepted(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
            Shipment shipment, String operation, FrontierPayload payload) {
        CommandResult result = FrontierV3CommandSubmission.submit(runtime, operation, shipment.id().value(), payload);
        FrontierV3DiagnosticTrace.record(level.getServer(), "shipment:" + shipment.id().value(), operation, shipment.id(), result);
        return result instanceof CommandResult.Accepted;
    }
}
