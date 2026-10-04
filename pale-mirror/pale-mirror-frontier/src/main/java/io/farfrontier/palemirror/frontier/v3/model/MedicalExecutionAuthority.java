package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Medical care declares patient and team; the shared lifecycle knows neither clinical role. */
public final class MedicalExecutionAuthority {
    private MedicalExecutionAuthority() { }
    public static List<SubjectId> participants(MedicalEvacuationOperation operation) {
        return java.util.stream.Stream.concat(java.util.stream.Stream.of(operation.patientId()), operation.team().memberIds().stream())
                .sorted().toList();
    }
    public static ActorExecutionGroup admission(FrontierWorldState state, MedicalEvacuationOperation operation) {
        return new ActorExecutionGroup(participants(operation).stream().map(actor ->
                state.actorExecutions().next(actor, ActorActivityKind.MEDICAL_TREATMENT, operation.id())).toList());
    }
    public static ActorExecutionGroup current(FrontierWorldState state, MedicalEvacuationOperation operation) {
        var group = new ActorExecutionGroup(participants(operation).stream().map(actor -> {
            var id = state.actorExecutions().current(ActorActivityKind.MEDICAL_TREATMENT).get(actor);
            if (id == null) throw new IllegalArgumentException("medical care lost an exact participant execution");
            return id;
        }).toList());
        requireCurrent(state, operation, group);
        return group;
    }
    public static void requireOwner(ActorExecutionGroup group, SubjectId owner) {
        for (var id : group.members()) if (id.activityKind() != ActorActivityKind.MEDICAL_TREATMENT || !id.activityOwnerId().equals(owner))
            throw new IllegalArgumentException("medical payload has a foreign kind or owner");
    }
    public static void requireCurrent(FrontierWorldState state, MedicalEvacuationOperation operation, ActorExecutionGroup group) {
        if (!operation.active()) throw new IllegalArgumentException("medical execution has a terminal care owner");
        group.requireDeclaration(ActorActivityKind.MEDICAL_TREATMENT, operation.id(), participants(operation));
        group.requireCurrent(state.actorExecutions());
    }
    public static ActorExecutionState retired(FrontierWorldState state, MedicalEvacuationOperation operation) {
        return ActorExecutionComposition.LIFECYCLE.retireCurrentGroup(state.actorExecutions(), current(state, operation));
    }
    static void validateReferences(HumanPopulation population, ActorExecutionState executions) {
        var operations = population.medicalOperations();
        for (var id : executions.current(ActorActivityKind.MEDICAL_TREATMENT).values()) require(operations, id);
        for (var operation : operations.values()) if (operation.active()) for (var actor : participants(operation)) {
            var id = executions.current(ActorActivityKind.MEDICAL_TREATMENT).get(actor);
            if (id == null || !id.activityOwnerId().equals(operation.id()))
                throw new IllegalArgumentException("active medical care lacks a declared participant execution");
        }
    }
    private static MedicalEvacuationOperation require(Map<SubjectId, MedicalEvacuationOperation> operations, ActorExecutionId id) {
        var operation = operations.get(id.activityOwnerId());
        if (id.activityKind() != ActorActivityKind.MEDICAL_TREATMENT || operation == null || !operation.active()
                || !participants(operation).contains(id.actorId()))
            throw new IllegalArgumentException("medical execution has a foreign or terminal care owner");
        return operation;
    }
    static ActorActivityCapability capability() { return new ActorActivityCapability() {
        @Override public Optional<ActorActivityDeath> deathAcknowledgement() {
            return Optional.of((state, execution, tick) -> {
                var operation = require(state.humanPopulation().medicalOperations(), execution);
                var population = state.humanPopulation().transitionMedicalOperation(operation.id(), MedicalEvacuationStatus.BLOCKED, tick);
                return new ActorActivityDeath.Acknowledgement(state, execution,
                        FrontierWorldStateUpdate.begin().humanPopulation(population),
                        ActorActivityDeath.Disposition.RETIRE_DECLARED_GROUP, Optional.of(current(state, operation)));
            });
        }
        @Override public boolean permitsAmbientMotion(FrontierWorldState state, ActorExecutionId id, AmbientActorLease lease) {
            return false; // Patient/team motion enters the coordinated clinical consumer.
        }
        @Override public ActorActivityKind kind() { return ActorActivityKind.MEDICAL_TREATMENT; }
        @Override public void validateAmbientRelease(FrontierWorldState state, ActorExecutionId execution) { }
        @Override public ActorActivityBodyCheckpoint bodyCheckpoint() { return ActorActivityBodyCheckpoint.usesActorLocation(); }
        @Override public Interruption interruption() { return Interruption.TERMINAL_ONLY; }
        @Override public void validateReference(FrontierWorldState state, ActorExecutionId id) { require(state.humanPopulation().medicalOperations(), id); }
        @Override public ActorActivityCheckpoint checkpoint(FrontierWorldState state, ActorExecutionId id) {
            var operation = require(state.humanPopulation().medicalOperations(), id);
            return new ActorActivityCheckpoint(state, id, Optional.of(new ActorActivityCheckpoint.Wait(
                    ActorActivityCheckpoint.Reason.OWNER_TERMINAL_BOUNDARY, operation.id())));
        }
        @Override public FrontierWorldState pause(FrontierWorldState state, ActorExecutionId id, long tick) { throw new IllegalArgumentException("medical owner must settle the exact patient and supply"); }
        @Override public FrontierWorldState resume(FrontierWorldState state, ActorExecutionId id, long tick) { throw new IllegalArgumentException("medical care has no suspended continuation"); }
        @Override public FrontierWorldState release(FrontierWorldState state, ActorExecutionId id) { throw new IllegalArgumentException("medical care retires through its terminal outcome"); }
    }; }
}
