package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Operation-owned crew transitions commit positions, job data and exclusive execution together. */
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
        Map<SubjectId, ActorLocation> nextActors = new LinkedHashMap<>(state.actorLocations());
        travel.formation().forEach((actor, position) -> nextActors.put(actor, state.actorLocations().get(actor).withBody(position)));
        var update = FrontierWorldStateUpdate.begin().actorLocations(nextActors).operations(nextOperations);
        if (operation.stage() == OperationStage.ASSEMBLING)
            return ActorExecutionComposition.LIFECYCLE.prepareTerminalGroupReplacement(state,
                    OperationExecutionAuthority.assemblyCurrent(state, operation), executions).commit(state, update);
        executions.requireCurrent(state.actorExecutions());
        return state.withChanges(update);
    }
    static FrontierWorldState advanceOperationTravel(FrontierWorldState state, SubjectId operationId, OperationTravel travel, ActorExecutionGroup executions) {
        RouteOperation operation = state.operations().get(Objects.requireNonNull(operationId, "operation travel operation id"));
        if (operation == null || operation.activeTravel().isEmpty()) throw new IllegalArgumentException("operation has no active exact travel");
        requireTactical(state, operation);
        executions.requireDeclaration(ActorActivityKind.LOGISTICS, operation.id(), operation.participantIds());
        executions.requireCurrent(state.actorExecutions());
        OperationTravel current = operation.activeTravel().orElseThrow();
        if (!current.corridor().equals(travel.corridor()) || travel.cursor() <= current.cursor() || travel.cursor() > current.nextColdCursor()) {
            throw new IllegalArgumentException("operation travel must advance its current bounded corridor");
        }
        RouteOperation advanced = operation.withTravel(travel); Map<SubjectId, RouteOperation> nextOperations = new LinkedHashMap<>(state.operations()); nextOperations.put(operation.id(), advanced);
        Map<SubjectId, ActorLocation> nextActors = new LinkedHashMap<>(state.actorLocations());
        travel.formation().forEach((actor, position) -> nextActors.put(actor, state.actorLocations().get(actor).withBody(position)));
        Map<SceneLeaseId, SceneLease> nextLeases = state.sceneLeases();
        SceneLease activeScene = state.sceneLeases().values().stream().filter(FrontierSceneBehaviors::isLogistics).filter(lease -> FrontierSceneBehaviors.logistics(lease).operationId().equals(operation.id()))
                .filter(lease -> lease.status() != SceneLeaseStatus.CLOSED).findFirst().orElse(null);
        if (activeScene != null) {
            if (!travel.isExactHotAdvanceFrom(current)) {
                throw new IllegalArgumentException("HOT operation travel may advance only one exact cursor");
            }
            SceneLease rebased = activeScene.rebaseHotOperationTravel(current, travel);
            nextLeases = new LinkedHashMap<>(state.sceneLeases()); nextLeases.put(rebased.id(), rebased);
        } else if (!ActorExecutionCoordinator.coldAvailable(state, operation.participantIds())) {
            throw new IllegalArgumentException("COLD operation travel cannot advance physically held participants");
        }
        return state.next(nextActors, state.structureConditions(), state.infection(), state.inventory(), state.productionJobs(), state.contracts(), nextOperations,
                state.physicalIntents(), state.physicalObservations(), nextLeases, state.hiveColony(), state.structureDamage(), state.physicalDeltas(), state.ambientLeases());
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
        advanced.positions().forEach((actor, position) -> nextActors.put(actor, state.actorLocations().get(actor).withBody(position.standingBody())));
        Map<SubjectId, AmbientActorLease> nextAmbient = new LinkedHashMap<>(state.ambientLeases());
        advanced.members().forEach((actor, member) -> {
            AmbientActorLease lease = nextAmbient.get(actor);
            if (lease != null && lease.status() == AmbientLeaseStatus.HOT && lease.goal() == AmbientGoalKind.OPERATION_ASSEMBLY) {
                SurfaceAnchor target = member.arrived() ? member.currentSurface() : member.nextSurface();
                nextAmbient.put(actor, lease.withGoal(AmbientGoalKind.OPERATION_ASSEMBLY, target.standingBody()));
            }
        });
        return state.next(nextActors, state.structureConditions(), state.infection(), state.inventory(), state.productionJobs(), state.contracts(), nextOperations, state.physicalIntents(), state.physicalObservations(), state.sceneLeases(), state.hiveColony(), state.structureDamage(), state.physicalDeltas(), nextAmbient);
    }
    static FrontierWorldState deferOperationAssembly(FrontierWorldState state, SubjectId operationId, OperationAssemblyDeferral deferral, ActorExecutionGroup executions) {
        RouteOperation operation = state.operations().get(Objects.requireNonNull(operationId, "operation assembly operation id"));
        if (operation == null || operation.activeAssembly().isEmpty()) throw new IllegalArgumentException("operation has no active assembly");
        requireTactical(state, operation);
        executions.requireDeclaration(ActorActivityKind.OPERATION_ASSEMBLY, operation.id(), operation.participantIds());
        executions.requireCurrent(state.actorExecutions());
        RouteOperation deferred = operation.withAssembly(operation.activeAssembly().orElseThrow().defer(Objects.requireNonNull(deferral, "assembly deferral")));
        Map<SubjectId, RouteOperation> nextOperations = new LinkedHashMap<>(state.operations()); nextOperations.put(operation.id(), deferred);
        return state.next(state.actorLocations(), state.structureConditions(), state.infection(), state.inventory(), state.productionJobs(), state.contracts(), nextOperations, state.physicalIntents(), state.physicalObservations(), state.sceneLeases(), state.hiveColony(), state.structureDamage(), state.physicalDeltas(), state.ambientLeases());
    }
    static FrontierWorldState completeOperationTravelSegment(FrontierWorldState state, SubjectId operationId, ActorExecutionGroup executions) {
        RouteOperation operation = state.operations().get(Objects.requireNonNull(operationId, "operation travel operation id")); if (operation == null) throw new IllegalArgumentException("unknown operation travel");
        requireTactical(state, operation);
        executions.requireDeclaration(ActorActivityKind.LOGISTICS, operation.id(), operation.participantIds());
        executions.requireCurrent(state.actorExecutions());
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
