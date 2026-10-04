package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** The assault owns combat membership/outcomes; UAE owns exclusive participant authority. */
public final class SettlementAssaultExecutionAuthority {
    private SettlementAssaultExecutionAuthority() { }
    public static List<SubjectId> participants(SettlementAssault assault) {
        return java.util.stream.Stream.concat(assault.attackerIds().stream(), assault.defenderIds().stream()).sorted().toList();
    }
    public static ActorExecutionGroup admission(FrontierWorldState state, SettlementAssault assault) {
        return new ActorExecutionGroup(participants(assault).stream().map(actor ->
                state.actorExecutions().next(actor, ActorActivityKind.SETTLEMENT_ASSAULT, assault.id())).toList());
    }
    public static void requireOwner(ActorExecutionGroup group, SubjectId owner) {
        for (var id : group.members()) if (id.activityKind() != ActorActivityKind.SETTLEMENT_ASSAULT || !id.activityOwnerId().equals(owner))
            throw new IllegalArgumentException("assault payload has a foreign activity kind or owner");
    }
    public static ActorExecutionGroup current(FrontierWorldState state, SettlementAssault assault) {
        return new ActorExecutionGroup(participants(assault).stream().map(actor -> {
            var id = state.actorExecutions().current(ActorActivityKind.SETTLEMENT_ASSAULT).get(actor);
            if (id == null || !id.activityOwnerId().equals(assault.id()))
                throw new IllegalArgumentException("assault lost its exact participant execution");
            state.actorExecutions().requireCurrent(id);
            return id;
        }).toList());
    }
    public static void requireCurrent(FrontierWorldState state, SettlementAssault assault, ActorExecutionGroup group) {
        group.requireDeclaration(ActorActivityKind.SETTLEMENT_ASSAULT, assault.id(), participants(assault));
        group.requireCurrent(state.actorExecutions());
    }
    public static FrontierWorldState admit(FrontierWorldState state, SettlementAssault assault, ActorExecutionGroup group,
                                           Optional<ActorExecutionGroup> predecessor, FrontierWorldStateUpdate update) {
        group.requireDeclaration(ActorActivityKind.SETTLEMENT_ASSAULT, assault.id(), participants(assault));
        var transition = predecessor.isPresent()
                ? ActorExecutionComposition.LIFECYCLE.prepareAcknowledgedGroupHandoff(state, predecessor.orElseThrow(), group)
                : ActorExecutionComposition.LIFECYCLE.prepareVacantGroup(state, group);
        return transition.commit(state, update);
    }
    static void validateReferences(Map<SubjectId, SettlementAssault> assaults, ActorExecutionState executions) {
        for (var id : executions.current(ActorActivityKind.SETTLEMENT_ASSAULT).values()) require(assaults, id);
        for (var assault : assaults.values()) if (assault.status() != SettlementAssaultStatus.RESOLVED)
            for (var actor : participants(assault)) {
                var id = executions.current(ActorActivityKind.SETTLEMENT_ASSAULT).get(actor);
                if (id == null || !id.activityOwnerId().equals(assault.id()))
                    throw new IllegalArgumentException("active assault participant lacks its declared execution");
            }
    }
    private static SettlementAssault require(Map<SubjectId, SettlementAssault> assaults, ActorExecutionId id) {
        var assault = assaults.get(id.activityOwnerId());
        if (id.activityKind() != ActorActivityKind.SETTLEMENT_ASSAULT || assault == null
                || assault.status() == SettlementAssaultStatus.RESOLVED || !participants(assault).contains(id.actorId()))
            throw new IllegalArgumentException("assault execution has a foreign or terminal owner");
        return assault;
    }
    static ActorActivityCapability capability() { return new ActorActivityCapability() {
        @Override public Optional<ActorActivityDeath> deathAcknowledgement() {
            return Optional.of((state, execution, tick) -> {
                var assault = require(state.strategicPlans().settlementAssaults(), execution);
                var changes = FrontierWorldStateUpdate.begin();
                if (assault.status() == SettlementAssaultStatus.HOT && assault.overseerId().equals(execution.actorId())) {
                    // The command owner's loss invalidates contact, not the identity of the
                    // expedition or its surviving members. Its terminal owner settles them.
                    var next = assault;
                    if (assault.tacticalPlan().phase() == TacticalPlanPhase.TRAVEL && !assault.march().complete()) {
                        var issue = new ExpeditionMarchIssue(ExpeditionMarchIssueKind.CONTROLLER_LOST, execution.actorId(),
                                assault.march().memberTopologies().get(execution.actorId()).edgeAfterCursor(assault.march().cursor()).id(),
                                assault.march().cursor());
                        next = assault.recordMarchIssue(issue);
                    }
                    changes.strategicPlans(state.strategicPlans().replaceSettlementAssault(next.retreatAfterOverseerLoss(execution.actorId())));
                }
                return new ActorActivityDeath.Acknowledgement(state, execution, changes, ActorActivityDeath.Disposition.RETAIN_CAUSAL_OWNER);
            });
        }
        @Override public boolean permitsAmbientMotion(FrontierWorldState state, ActorExecutionId id, AmbientActorLease lease) {
            return false; // The assault owns formation/tactical goals, never ambient fallback motion.
        }
        @Override public ActorActivityKind kind() { return ActorActivityKind.SETTLEMENT_ASSAULT; }
        @Override public void validateAmbientRelease(FrontierWorldState state, ActorExecutionId execution) { }
        @Override public ActorActivityBodyCheckpoint bodyCheckpoint() {
            return request -> {
                var owner = require(request.expectedState().strategicPlans().settlementAssaults(), request.execution());
                return ExpeditionBodyCheckpoint.acknowledge(request, owner);
            };
        }
        @Override public Interruption interruption() { return Interruption.TERMINAL_ONLY; }
        @Override public void validateReference(FrontierWorldState state, ActorExecutionId id) { require(state.strategicPlans().settlementAssaults(), id); }
        @Override public ActorActivityCheckpoint checkpoint(FrontierWorldState state, ActorExecutionId id) {
            var assault = require(state.strategicPlans().settlementAssaults(), id);
            return new ActorActivityCheckpoint(state, id, Optional.of(new ActorActivityCheckpoint.Wait(
                    ActorActivityCheckpoint.Reason.OWNER_TERMINAL_BOUNDARY, assault.id())));
        }
        @Override public FrontierWorldState pause(FrontierWorldState state, ActorExecutionId id, long atTick) { throw new IllegalArgumentException("assault owner must settle its coordinated battle"); }
        @Override public FrontierWorldState resume(FrontierWorldState state, ActorExecutionId id, long atTick) { throw new IllegalArgumentException("assault has no suspended continuation"); }
        @Override public FrontierWorldState release(FrontierWorldState state, ActorExecutionId id) { throw new IllegalArgumentException("assault outcome retires its exact combat cohort"); }
    }; }
}
