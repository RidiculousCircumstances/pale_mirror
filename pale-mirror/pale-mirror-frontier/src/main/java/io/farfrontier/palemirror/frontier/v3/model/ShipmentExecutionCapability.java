package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import java.util.Optional;

/** Courier owns a transport obligation, never the commercial contract or an independent body. */
final class ShipmentExecutionCapability implements ActorActivityCapability {
    @Override public ActorActivityKind kind() { return ActorActivityKind.COURIER; }
    @Override public Interruption interruption() { return Interruption.RETAIN_CONTINUATION; }
    @Override public ActorActivityResumption resumptionReference() {
        return request -> {
            validateReference(request.expectedState(), request.suspended());
            var shipment = request.expectedState().shipments().shipments().get(request.suspended().activityOwnerId());
            return new ActorActivityResumption.Acknowledgement(request, FrontierWorldStateUpdate.begin()
                    .shipments(request.expectedState().shipments().resume(shipment, request.successor())));
        };
    }
    @Override public Optional<ActorActivityDeath> deathAcknowledgement() {
        return Optional.of((state, execution, tick) -> {
            validateReference(state, execution);
            // The corpse/claim/effect remain an exact resource obligation until its owner settles
            // the physical outcome. A body fatality alone cannot invent cargo loss or payment.
            return new ActorActivityDeath.Acknowledgement(state, execution, FrontierWorldStateUpdate.begin(),
                    ActorActivityDeath.Disposition.RETAIN_CAUSAL_OWNER);
        });
    }
    @Override public ActorActivityBodyCheckpoint bodyCheckpoint() { return new ShipmentBodyCheckpoint(); }
    @Override public void validateAmbientRelease(FrontierWorldState state, ActorExecutionId execution) {
        validateReference(state, execution);
        var shipment = state.shipments().shipments().get(execution.activityOwnerId());
        if (shipment.pendingPhysicalStep().isPresent()
                || state.inventory().fungibleResources().bindings().values().stream()
                    .anyMatch(binding -> binding.accountId().equals(shipment.carriedAccountId())))
            throw new IllegalArgumentException("shipment presentation retains an unsettled hand/effect");
    }
    static void validateReferences(ShipmentState shipments, ActorExecutionState executions) {
        for (Shipment shipment : shipments.shipments().values()) if (!shipment.terminal()) executions.requireRetained(shipment.execution());
        for (ActorExecutionId execution : executions.current(ActorActivityKind.COURIER).values()) {
            Shipment shipment = shipments.shipments().get(execution.activityOwnerId());
            if (shipment == null || shipment.terminal() || !shipment.execution().equals(execution))
                throw new IllegalArgumentException("courier execution lost its exact shipment");
        }
        for (ActorExecutionId execution : executions.suspended()) if (execution.activityKind() == ActorActivityKind.COURIER) {
            Shipment shipment = shipments.shipments().get(execution.activityOwnerId());
            if (shipment == null || shipment.terminal() || !shipment.execution().equals(execution))
                throw new IllegalArgumentException("suspended courier lost its exact shipment");
        }
    }
    @Override public void validateReference(FrontierWorldState state, ActorExecutionId execution) {
        Shipment shipment = state.shipments().shipments().get(execution.activityOwnerId());
        if (execution.activityKind() != kind() || shipment == null || shipment.terminal() || !shipment.execution().equals(execution))
            throw new IllegalArgumentException("courier capability has no exact live shipment");
    }
    @Override public ActorActivityCheckpoint checkpoint(FrontierWorldState state, ActorExecutionId execution) {
        validateReference(state, execution);
        Shipment shipment = state.shipments().shipments().get(execution.activityOwnerId());
        var reason = shipment.pendingPhysicalStep().isPresent() ? Optional.of(ActorActivityCheckpoint.Reason.PHYSICAL_OPERATION)
                : state.actorMovements().containsKey(execution.actorId()) ? Optional.of(ActorActivityCheckpoint.Reason.OWNER_TERMINAL_BOUNDARY)
                : Optional.<ActorActivityCheckpoint.Reason>empty();
        return new ActorActivityCheckpoint(state, execution, reason.map(value -> new ActorActivityCheckpoint.Wait(value, execution.activityOwnerId())));
    }
    @Override public boolean permitsAmbientMotion(FrontierWorldState state, ActorExecutionId execution, AmbientActorLease lease) {
        validateReference(state, execution);
        var movement = state.actorMovements().get(execution.actorId());
        return lease.goal() == AmbientGoalKind.ACTOR_MOVEMENT && movement != null
                && movement.executionId().equals(execution)
                && movement.order().legalStations().contains(lease.goalBody().supportingSurface());
    }
    @Override public FrontierWorldState pause(FrontierWorldState state, ActorExecutionId execution, long atTick) {
        var checkpoint = checkpoint(state, execution);
        if (checkpoint.waiting().isPresent()) throw new IllegalArgumentException("courier pause has no safe resource boundary");
        return state; // Cargo stays in the common actor account; a meal uses its separate personal slot.
    }
    @Override public FrontierWorldState resume(FrontierWorldState state, ActorExecutionId execution, long atTick) {
        validateReference(state, execution);
        return state; // The shipment replans its retained leg from the actor's actual post-meal position.
    }
    @Override public FrontierWorldState release(FrontierWorldState state, ActorExecutionId execution) {
        throw new IllegalArgumentException("shipment must explicitly settle its transport obligation");
    }
}
