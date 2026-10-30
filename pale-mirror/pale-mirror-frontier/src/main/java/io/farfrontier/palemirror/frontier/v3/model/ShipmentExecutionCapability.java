package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import java.util.Optional;

/** Courier owns a transport obligation, never the commercial contract or an independent body. */
final class ShipmentExecutionCapability implements ActorActivityCapability {
    private record References(ShipmentState shipments, ActorExecutionState executions) { }
    private static final ImmutableReferenceIndex.Source<References, io.farfrontier.palemirror.frontier.v3.api.SubjectId, ActorExecution> EXECUTIONS =
            new ImmutableReferenceIndex.Source<>(c -> c.executions().actors());
    private static final ImmutableReferenceIndex.Source<References, io.farfrontier.palemirror.frontier.v3.api.SubjectId, TransportMission> MISSIONS =
            new ImmutableReferenceIndex.Source<>(c -> c.shipments().missions());
    private static final ImmutableReferenceIndex.Source<References, io.farfrontier.palemirror.frontier.v3.api.SubjectId, Shipment> SHIPMENTS =
            new ImmutableReferenceIndex.Source<>(c -> c.shipments().shipments());
    private static final ImmutableReferenceIndex<References, io.farfrontier.palemirror.frontier.v3.api.SubjectId, Shipment> FORWARD =
            new ImmutableReferenceIndex<>(ShipmentState.MAX_SHIPMENTS, 2, java.util.List.of(EXECUTIONS, MISSIONS), (c, shipment) -> {
                if (shipment.terminal()) return java.util.List.of();
                var reads = new java.util.ArrayList<ImmutableReferenceIndex.Read<References>>();
                reads.add(EXECUTIONS.at(shipment.execution().actorId()));
                if (TransportCargoExecution.shared(shipment)) reads.add(MISSIONS.at(shipment.transportMissionId().orElseThrow()));
                return reads;
            }, (c, id, shipment) -> {
                if (shipment.terminal()) return;
                if (TransportCargoExecution.shared(shipment)) TransportCargoExecution.require(c.shipments(), shipment);
                c.executions().requireRetained(shipment.execution());
            });
    private static final ImmutableReferenceIndex<References, io.farfrontier.palemirror.frontier.v3.api.SubjectId, ActorExecution> BACKWARD =
            new ImmutableReferenceIndex<>(SimulationCapacity.MAX_ACTORS, 2, java.util.List.of(SHIPMENTS), (c, execution) -> {
                var reads = new java.util.ArrayList<ImmutableReferenceIndex.Read<References>>();
                execution.current().filter(id -> id.activityKind() == ActorActivityKind.COURIER)
                        .ifPresent(id -> reads.add(SHIPMENTS.at(id.activityOwnerId())));
                execution.suspended().filter(id -> id.activityKind() == ActorActivityKind.COURIER)
                        .ifPresent(id -> reads.add(SHIPMENTS.at(id.activityOwnerId())));
                return reads;
            }, (c, actor, execution) -> {
                execution.current().filter(id -> id.activityKind() == ActorActivityKind.COURIER).ifPresent(id -> validateCourier(c, id));
                execution.suspended().filter(id -> id.activityKind() == ActorActivityKind.COURIER).ifPresent(id -> validateCourier(c, id));
            });
    @Override public ActorActivityKind kind() { return ActorActivityKind.COURIER; }
    @Override public boolean permitsHomeFood(FrontierWorldState state, ActorExecutionId execution) {
        validateReference(state, execution);
        var shipment = state.shipments().shipments().get(execution.activityOwnerId());
        if (shipment.transportMissionId().isEmpty()) return true;
        var mission = state.shipments().missions().get(shipment.transportMissionId().orElseThrow());
        return mission.preparingSupplies() || mission.stage() == TransportMission.Stage.COMPLETE;
    }
    @Override public Interruption interruption() { return Interruption.RETAIN_CONTINUATION; }
    @Override public ActorActivityResumption resumptionReference() {
        return request -> {
            validateReference(request.expectedState(), request.suspended());
            var shipment = request.expectedState().shipments().shipments().get(request.suspended().activityOwnerId());
            var shipments = request.expectedState().shipments().resume(shipment, request.successor());
            if (shipment.transportMissionId().isPresent()) shipments =
                    io.farfrontier.palemirror.frontier.v3.model.expedition.ExpeditionReplenishmentAuthority.resume(
                            shipments, shipment.transportMissionId().orElseThrow(), request.suspended(), request.successor());
            return new ActorActivityResumption.Acknowledgement(request, FrontierWorldStateUpdate.begin().shipments(shipments));
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
                || shipment.cargo() instanceof ShipmentCargo.Fungible && state.inventory().fungibleResources().bindings().values().stream()
                    .anyMatch(binding -> binding.accountId().equals(shipment.carriedAccountId())))
            throw new IllegalArgumentException("shipment presentation retains an unsettled hand/effect");
    }
    static void validateReferences(ShipmentState shipments, ActorExecutionState executions) {
        var references = new References(shipments, executions);
        FORWARD.validate(ShipmentExecutionCapability.class, shipments.shipments(), references);
        BACKWARD.validate(ShipmentExecutionCapability.class, executions.actors(), references);
    }
    private static void validateCourier(References references, ActorExecutionId execution) {
        var shipment = references.shipments().shipments().get(execution.activityOwnerId());
        if (shipment == null || shipment.terminal() || !shipment.execution().equals(execution))
            throw new IllegalArgumentException("courier execution lost its exact shipment");
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
    @Override public ActorActivityCheckpoint spatialYieldCheckpoint(FrontierWorldState state, ActorExecutionId execution) {
        validateReference(state, execution);
        var shipment = state.shipments().shipments().get(execution.activityOwnerId());
        // Local avoidance retains the same execution, movement goal and resource custody.
        // Waiting for that movement to finish would deadlock mutually blocked couriers.
        return new ActorActivityCheckpoint(state, execution, shipment.pendingPhysicalStep().map(step ->
                new ActorActivityCheckpoint.Wait(ActorActivityCheckpoint.Reason.PHYSICAL_OPERATION, shipment.id())));
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
    @Override public java.util.List<io.farfrontier.palemirror.frontier.v3.api.ProposedEvent> continuationAfterResume(
            FrontierWorldState state, ActorExecutionId successor, long tick) {
        validateReference(state, successor);
        state.actorExecutions().requireCurrent(successor);
        var shipment = state.shipments().shipments().get(successor.activityOwnerId());
        var events = new java.util.ArrayList<io.farfrontier.palemirror.frontier.v3.api.ProposedEvent>();
        events.add(ShipmentContinuation.wake(shipment.id(), tick));
        shipment.transportMissionId().ifPresent(id -> {
            var mission = state.shipments().missions().get(id);
            if (mission == null) throw new IllegalArgumentException("resumed courier lost its retained mission");
            events.add(UnitGroupContinuation.wake(mission.groupId(), tick));
        });
        return java.util.List.copyOf(events);
    }
    @Override public FrontierWorldState release(FrontierWorldState state, ActorExecutionId execution) {
        throw new IllegalArgumentException("shipment must explicitly settle its transport obligation");
    }
}
