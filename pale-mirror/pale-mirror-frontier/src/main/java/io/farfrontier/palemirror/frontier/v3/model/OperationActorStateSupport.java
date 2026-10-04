package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Operation owns crew progress; only COLD traversal may commit background positions. */
final class OperationActorStateSupport {
    private OperationActorStateSupport() { }
    static FrontierWorldState createOperation(FrontierWorldState state, RouteOperation operation, ActorExecutionGroup executions) {
        Objects.requireNonNull(operation, "route operation");
        requireTactical(state, operation);
        if (state.operations().containsKey(operation.id())) throw new IllegalArgumentException("route operation identity already exists: " + operation.id().value());
        Map<SubjectId, RouteOperation> next = new LinkedHashMap<>(state.operations()); next.put(operation.id(), operation);
        // Creation is a claim, never a movement.  Each assembly/travel cursor owns later positions.
        executions.requireDeclaration(io.farfrontier.palemirror.frontier.v3.model.execution.ActorActivityKind.OPERATION_ASSEMBLY,
                operation.id(), operation.participantIds());
        return ActorExecutionComposition.LIFECYCLE.prepareVacantGroup(state, executions)
                .commit(state, FrontierWorldStateUpdate.begin().operations(next));
    }
    static FrontierWorldState startOperationTravel(FrontierWorldState state, SubjectId operationId, OperationTravel travel, ActorExecutionGroup executions) {
        RouteOperation operation = state.operations().get(Objects.requireNonNull(operationId, "operation travel operation id"));
        if (operation == null || (operation.activeTravel().isPresent() && !operation.activeTravel().orElseThrow().arrived())) {
            throw new IllegalArgumentException("operation already owns an in-progress exact travel or does not exist");
        }
        requireTactical(state, operation);
        executions.requireDeclaration(ActorActivityKind.LOGISTICS, operation.id(), operation.participantIds());
        RouteOperation started = operation.startTravel(Objects.requireNonNull(travel, "operation travel"));
        Map<SubjectId, RouteOperation> nextOperations = new LinkedHashMap<>(state.operations()); nextOperations.put(operation.id(), started);
        if (!travel.approaches().isEmpty() || travel.formation().entrySet().stream().anyMatch(entry ->
                !entry.getValue().equals(state.actorLocations().get(entry.getKey()).body())))
            throw new IllegalArgumentException("starting travel must retain independently established assembly/arrival positions");
        var update = FrontierWorldStateUpdate.begin().operations(nextOperations);
        if (operation.stage() == OperationStage.ASSEMBLING)
            return ActorExecutionComposition.LIFECYCLE.prepareTerminalGroupReplacement(state,
                    OperationExecutionAuthority.assemblyCurrent(state, operation), executions).commit(state, update);
        executions.requireCurrent(state.actorExecutions());
        return state.withChanges(update);
    }
    static FrontierWorldState advanceOperationTravel(FrontierWorldState state, SubjectId operationId, OperationTravel travel, OperationTravelObservation observation) {
        RouteOperation operation = state.operations().get(Objects.requireNonNull(operationId, "operation travel operation id"));
        if (operation == null || operation.activeTravel().isEmpty()) throw new IllegalArgumentException("operation has no active exact travel");
        requireTactical(state, operation);
        var executions = observation.executions();
        executions.requireDeclaration(ActorActivityKind.LOGISTICS, operation.id(), operation.participantIds());
        executions.requireCurrent(state.actorExecutions());
        OperationTravel current = operation.activeTravel().orElseThrow();
        if (!current.equals(observation.predecessor())) throw new IllegalArgumentException("operation receipt has a stale spatial/route predecessor");
        var update = FrontierWorldStateUpdate.begin();
        if (observation instanceof OperationTravelObservation.HotSegment hot) {
            if (!current.canAdvanceNextEdge() || !travel.isExactHotAdvanceFrom(current))
                throw new IllegalArgumentException("HOT travel must advance one exact OPEN semantic edge");
            SceneLease activeScene = hot.require(state, operation, travel);
            var leases = new LinkedHashMap<>(state.sceneLeases());
            leases.put(activeScene.id(), activeScene.rebaseHotOperationTravel(current, travel));
            update.sceneLeases(leases); // All HOT positions were independently inspected; never install them here.
        } else {
            observation.requireColdEpochs(state);
            if (!ActorExecutionCoordinator.coldAvailable(state, operation.participantIds())
                    || FrontierSceneAdmission.hasActiveSceneLease(state, operation.id()))
                throw new IllegalArgumentException("COLD travel cannot advance physically held or scene-scoped crew");
            Map<SubjectId, ActorLocation> actors = new LinkedHashMap<>(state.actorLocations());
            if (observation instanceof OperationTravelObservation.ColdApproach) {
                if (travel.equals(current) || !travel.equals(OperationTravelContinuation.coldApproached(state, operation)))
                    throw new IllegalArgumentException("COLD approach needs its exact bounded known-geometry successor");
                travel.approaches().keySet().forEach(actor -> actors.put(actor,
                        state.actorLocations().get(actor).withBody(travel.memberCheckpoint(actor))));
            } else if (observation instanceof OperationTravelObservation.ColdSegment) {
                if (!current.advance(travel.cursor(), travel.formation(), travel.cargoAnchor()).equals(travel)
                        || !OperationTravelContinuation.coldSegmentAvailable(state, current, travel))
                    throw new IllegalArgumentException("COLD travel must preserve formation through every known legal edge");
                travel.formation().forEach((actor, position) -> actors.put(actor, state.actorLocations().get(actor).withBody(position)));
            } else throw new IllegalArgumentException("unsupported travel observation provider");
            update.actorLocations(actors);
        }
        var operations = new LinkedHashMap<>(state.operations()); operations.put(operation.id(), operation.withTravel(travel));
        return state.withChanges(update.operations(operations));
    }
    static FrontierWorldState advanceOperationAssembly(FrontierWorldState state, SubjectId operationId, OperationAssembly assembly, ActorExecutionGroup executions) {
        RouteOperation operation = state.operations().get(Objects.requireNonNull(operationId, "operation assembly operation id"));
        if (operation == null || operation.activeAssembly().isEmpty()) throw new IllegalArgumentException("operation has no active assembly");
        requireTactical(state, operation);
        executions.requireDeclaration(ActorActivityKind.OPERATION_ASSEMBLY, operation.id(), operation.participantIds());
        executions.requireCurrent(state.actorExecutions());
        OperationAssembly advanced = operation.activeAssembly().orElseThrow().advance(Objects.requireNonNull(assembly, "operation assembly").members());
        Map<SubjectId, RouteOperation> nextOperations = new LinkedHashMap<>(state.operations()); nextOperations.put(operation.id(), operation.withAssembly(advanced));
        Map<SubjectId, ActorLocation> nextActors = new LinkedHashMap<>(state.actorLocations());
        advanced.members().forEach((actor, member) -> {
            if (member.equals(operation.activeAssembly().orElseThrow().members().get(actor))) return;
            if (ActorExecutionCoordinator.coldAvailable(state, actor)) {
                if (!OperationExecutionAuthority.coldAssemblyTraversalAvailable(state, operation,
                        operation.activeAssembly().orElseThrow().members().get(actor), member))
                    throw new IllegalArgumentException("COLD assembly traversal is blocked by known geometry");
                nextActors.put(actor, state.actorLocations().get(actor).withBody(member.currentSurface().standingBody()));
            } else if (!state.actorLocations().get(actor).body().equals(member.currentSurface().standingBody()))
                throw new IllegalArgumentException("HOT assembly progress needs independent physical position evidence");
        });
        Map<SubjectId, AmbientActorLease> nextAmbient = new LinkedHashMap<>(state.ambientLeases());
        advanced.members().forEach((actor, member) -> {
            AmbientActorLease lease = nextAmbient.get(actor);
            if (lease != null && lease.status() == AmbientLeaseStatus.HOT && lease.goal() == AmbientGoalKind.OPERATION_ASSEMBLY) {
                SurfaceAnchor target = member.arrived() ? member.currentSurface() : member.nextSurface();
                nextAmbient.put(actor, lease.withGoal(AmbientGoalKind.OPERATION_ASSEMBLY, target.standingBody()));
            }
        });
        return state.next(nextActors, state.structureConditions(), state.infection(), state.inventory(), state.productionJobs(), state.contracts(), nextOperations, state.physicalIntents(), state.physicalObservations(),
                state.sceneLeases(), state.hiveColony(), state.structureDamage(), state.physicalDeltas(), nextAmbient);
    }
    static FrontierWorldState deferOperationAssembly(FrontierWorldState state, SubjectId operationId, OperationAssemblyDeferral deferral, ActorExecutionGroup executions) {
        RouteOperation operation = state.operations().get(Objects.requireNonNull(operationId, "operation assembly operation id"));
        if (operation == null || operation.activeAssembly().isEmpty()) throw new IllegalArgumentException("operation has no active assembly");
        requireTactical(state, operation);
        executions.requireDeclaration(ActorActivityKind.OPERATION_ASSEMBLY, operation.id(), operation.participantIds());
        executions.requireCurrent(state.actorExecutions());
        RouteOperation deferred = operation.withAssembly(operation.activeAssembly().orElseThrow().defer(Objects.requireNonNull(deferral, "assembly deferral")));
        Map<SubjectId, RouteOperation> nextOperations = new LinkedHashMap<>(state.operations()); nextOperations.put(operation.id(), deferred);
        return state.next(state.actorLocations(), state.structureConditions(), state.infection(), state.inventory(), state.productionJobs(), state.contracts(), nextOperations, state.physicalIntents(),
                state.physicalObservations(), state.sceneLeases(), state.hiveColony(), state.structureDamage(), state.physicalDeltas(), state.ambientLeases());
    }
    static FrontierWorldState completeOperationTravelSegment(FrontierWorldState state, SubjectId operationId, ActorExecutionGroup executions) {
        RouteOperation operation = state.operations().get(Objects.requireNonNull(operationId, "operation travel operation id")); if (operation == null) throw new IllegalArgumentException("unknown operation travel");
        requireTactical(state, operation);
        executions.requireDeclaration(ActorActivityKind.LOGISTICS, operation.id(), operation.participantIds());
        executions.requireCurrent(state.actorExecutions());
        var travel = operation.activeTravel().orElseThrow();
        if (!OperationTravelContinuation.approachesReady(travel) || travel.formation().entrySet().stream().anyMatch(entry ->
                !entry.getValue().equals(state.actorLocations().get(entry.getKey()).body())))
            throw new IllegalArgumentException("segment completion needs every exact member at its independently established formation goal");
        RouteOperation completed = operation.completeTravelSegment();
        Map<SubjectId, RouteOperation> nextOperations = new LinkedHashMap<>(state.operations()); nextOperations.put(operation.id(), completed);
        var update = FrontierWorldStateUpdate.begin().operations(nextOperations);
        if (completed.stage() == OperationStage.COMPLETED)
            update.actorExecutions(OperationExecutionAuthority.retired(state, executions));
        return state.withChanges(update);
    }
    private static void requireTactical(FrontierWorldState state, RouteOperation operation) {
        if (!operation.tacticalPlan().currentFor(state.strategicPlans()))
            throw new IllegalArgumentException("route operation tactical plan has stale decision authority");
    }
}
