package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import java.util.Map;
import java.util.Optional;

/** Service owns its input, work and effect; UAE retains only the exact worker authority. */
public final class SettlementServiceExecutionAuthority {
    private SettlementServiceExecutionAuthority() { }
    public static ActorExecutionId admission(FrontierWorldState state, SettlementServiceWork work) {
        return state.actorExecutions().next(work.workerId(), ActorActivityKind.SETTLEMENT_SERVICE, work.id());
    }
    public static ActorExecutionId current(FrontierWorldState state, SettlementServiceWork work) {
        var id = state.actorExecutions().current(ActorActivityKind.SETTLEMENT_SERVICE).get(work.workerId());
        if (id == null) throw new IllegalArgumentException("service lost its exact worker execution");
        requireCurrent(state, work, id);
        return id;
    }
    public static void requireDeclaration(ActorExecutionId id, SubjectId workId) {
        if (id.activityKind() != ActorActivityKind.SETTLEMENT_SERVICE || !id.activityOwnerId().equals(workId))
            throw new IllegalArgumentException("service payload has a foreign kind or owner");
    }
    public static void requireCurrent(FrontierWorldState state, SettlementServiceWork work, ActorExecutionId id) {
        requireDeclaration(id, work.id());
        if (!id.actorId().equals(work.workerId()) || !work.phase().active())
            throw new IllegalArgumentException("service payload has a foreign worker or terminal owner");
        state.actorExecutions().requireCurrent(id);
    }
    public static ActorExecutionState retired(FrontierWorldState state, SettlementServiceWork work) {
        return ActorExecutionComposition.LIFECYCLE.retire(state, work.workerId(), ActorActivityKind.SETTLEMENT_SERVICE, work.id());
    }
    static void validateReferences(Map<SubjectId, SettlementServiceWork> works, ActorExecutionState executions) {
        for (var id : executions.current(ActorActivityKind.SETTLEMENT_SERVICE).values()) require(works, id);
        for (var work : works.values()) if (work.phase().active()) {
            var id = executions.current(ActorActivityKind.SETTLEMENT_SERVICE).get(work.workerId());
            if (id == null || !id.activityOwnerId().equals(work.id()))
                throw new IllegalArgumentException("active service lacks its declared worker execution");
        }
    }
    private static SettlementServiceWork require(Map<SubjectId, SettlementServiceWork> works, ActorExecutionId id) {
        requireDeclaration(id, id.activityOwnerId());
        var work = works.get(id.activityOwnerId());
        if (work == null || !work.phase().active() || !work.workerId().equals(id.actorId()))
            throw new IllegalArgumentException("service execution lost its active exact work owner");
        return work;
    }
    static ActorActivityCapability capability() { return new ActorActivityCapability() {
        @Override public boolean permitsAmbientMotion(FrontierWorldState state, ActorExecutionId id, AmbientActorLease lease) {
            return false; // Service traversal is issued only by its exact service-scene consumer.
        }
        @Override public ActorActivityKind kind() { return ActorActivityKind.SETTLEMENT_SERVICE; }
        @Override public void validateAmbientRelease(FrontierWorldState state, ActorExecutionId execution) { }
        @Override public ActorActivityBodyCheckpoint bodyCheckpoint() { return SettlementServiceJourneyKnowledge::acknowledge; }
        @Override public Interruption interruption() { return Interruption.TERMINAL_ONLY; }
        @Override public void validateReference(FrontierWorldState state, ActorExecutionId id) { require(state.serviceWorks(), id); }
        @Override public ActorActivityCheckpoint checkpoint(FrontierWorldState state, ActorExecutionId id) {
            var work = require(state.serviceWorks(), id);
            boolean pending = java.util.List.of(work.inputIssueIntentId(), work.endpointIntentId()).stream()
                    .map(state.physicalIntents()::get).anyMatch(intent -> intent.status() == PhysicalIntentStatus.RUNNING
                            || intent.status() == PhysicalIntentStatus.UNKNOWN_AFTER_RESTART);
            return new ActorActivityCheckpoint(state, id, Optional.of(new ActorActivityCheckpoint.Wait(
                    pending ? ActorActivityCheckpoint.Reason.PHYSICAL_OPERATION : ActorActivityCheckpoint.Reason.OWNER_TERMINAL_BOUNDARY,
                    work.id())));
        }
        @Override public FrontierWorldState pause(FrontierWorldState state, ActorExecutionId id, long tick) { throw new IllegalArgumentException("service must settle its input and endpoint"); }
        @Override public FrontierWorldState resume(FrontierWorldState state, ActorExecutionId id, long tick) { throw new IllegalArgumentException("service has no suspended continuation"); }
        @Override public FrontierWorldState release(FrontierWorldState state, ActorExecutionId id) { throw new IllegalArgumentException("service retires through its exact terminal outcome"); }
    }; }
}
