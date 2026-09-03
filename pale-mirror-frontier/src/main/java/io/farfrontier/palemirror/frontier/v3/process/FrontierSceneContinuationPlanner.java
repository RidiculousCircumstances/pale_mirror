package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect;
import io.farfrontier.palemirror.frontier.v3.model.FrontierSceneBehaviors;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.RouteEngagement;
import io.farfrontier.palemirror.frontier.v3.model.RouteOperation;
import io.farfrontier.palemirror.frontier.v3.model.SceneContinuation;
import io.farfrontier.palemirror.frontier.v3.model.SceneLease;
import io.farfrontier.palemirror.frontier.v3.model.SceneLeaseRecoveryUnresolved;
import io.farfrontier.palemirror.frontier.v3.model.SceneLeaseReleased;
import io.farfrontier.palemirror.frontier.v3.model.SceneRecoveryPlan;
import io.farfrontier.palemirror.frontier.v3.model.SceneReleasePlan;
import io.farfrontier.palemirror.frontier.v3.model.SettlementAssault;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Closed process-side companion to {@link FrontierSceneBehaviors}.
 *
 * <p>Scene behaviors select a typed continuation in the pure model.  This
 * registry is the only boundary that turns that decision into process-owned
 * scheduling or failure events, so model policy never imports process code.</p>
 */
public final class FrontierSceneContinuationPlanner {
    private static final Map<SceneContinuation.Kind, ContinuationHandler> HANDLERS = handlers();

    private FrontierSceneContinuationPlanner() { }

    public static List<ProposedEvent> releaseEvents(FrontierWorldState state, SceneLease lease, long submittedAt,
                                                    SceneLeaseReleased released) {
        SceneReleasePlan plan = FrontierSceneBehaviors.releasePlan(state, lease, submittedAt, released);
        return events(state, plan.owner(), plan.released(), plan.continuation());
    }

    public static List<ProposedEvent> recoveryUnresolvedEvents(FrontierWorldState state, SceneLease lease,
                                                               SceneLeaseRecoveryUnresolved unresolved) {
        SceneRecoveryPlan plan = FrontierSceneBehaviors.recoveryUnresolvedPlan(state, lease, unresolved);
        return events(state, plan.owner(), plan.unresolved(), plan.continuation());
    }

    private static List<ProposedEvent> events(FrontierWorldState state, io.farfrontier.palemirror.frontier.v3.api.SubjectId owner,
                                              FrontierPayload lifecycleFact, SceneContinuation continuation) {
        Objects.requireNonNull(state, "scene continuation state");
        Objects.requireNonNull(owner, "scene continuation owner");
        Objects.requireNonNull(lifecycleFact, "scene lifecycle fact");
        ContinuationHandler handler = HANDLERS.get(Objects.requireNonNull(continuation, "scene continuation").kind());
        if (handler == null) throw new IllegalStateException("unregistered scene continuation: " + continuation.kind());
        List<ProposedEvent> tail = handler.events(state, continuation);
        if (tail.isEmpty()) return List.of(new ProposedEvent(owner, lifecycleFact));
        java.util.ArrayList<ProposedEvent> events = new java.util.ArrayList<>(tail.size() + 1);
        events.add(new ProposedEvent(owner, lifecycleFact));
        events.addAll(tail);
        return List.copyOf(events);
    }

    private static Map<SceneContinuation.Kind, ContinuationHandler> handlers() {
        EnumMap<SceneContinuation.Kind, ContinuationHandler> handlers = new EnumMap<>(SceneContinuation.Kind.class);
        register(handlers, new NoneHandler());
        register(handlers, new ResumeOperationHandler());
        register(handlers, new FailOperationHandler());
        register(handlers, new ResumeEngagementHandler());
        register(handlers, new ResumeSettlementAssaultHandler());
        if (handlers.size() != SceneContinuation.Kind.values().length) {
            throw new IllegalStateException("missing scene continuation handler");
        }
        return Map.copyOf(handlers);
    }

    private static void register(Map<SceneContinuation.Kind, ContinuationHandler> handlers, ContinuationHandler handler) {
        if (handlers.putIfAbsent(handler.kind(), handler) != null) {
            throw new IllegalStateException("duplicate scene continuation handler: " + handler.kind());
        }
    }

    private interface ContinuationHandler {
        SceneContinuation.Kind kind();
        List<ProposedEvent> events(FrontierWorldState state, SceneContinuation continuation);
    }

    private static final class NoneHandler implements ContinuationHandler {
        @Override public SceneContinuation.Kind kind() { return SceneContinuation.Kind.NONE; }
        @Override public List<ProposedEvent> events(FrontierWorldState state, SceneContinuation continuation) {
            if (!(continuation instanceof SceneContinuation.None)) throw invalid(continuation, kind());
            return List.of();
        }
    }

    private static final class ResumeOperationHandler implements ContinuationHandler {
        @Override public SceneContinuation.Kind kind() { return SceneContinuation.Kind.RESUME_OPERATION; }
        @Override public List<ProposedEvent> events(FrontierWorldState state, SceneContinuation continuation) {
            if (!(continuation instanceof SceneContinuation.ResumeOperation resume)) throw invalid(continuation, kind());
            RouteOperation operation = requireOperation(state, resume.operationId());
            return List.of(new ProposedEvent(operation.settlementId(), new ScheduleEffect.Created(
                    SupplyOperationProcess.operationProgress(operation, resume.dueAt()))));
        }
    }

    private static final class FailOperationHandler implements ContinuationHandler {
        @Override public SceneContinuation.Kind kind() { return SceneContinuation.Kind.FAIL_OPERATION; }
        @Override public List<ProposedEvent> events(FrontierWorldState state, SceneContinuation continuation) {
            if (!(continuation instanceof SceneContinuation.FailOperation failure)) throw invalid(continuation, kind());
            return SupplyOperationProcess.failed(state, requireOperation(state, failure.operationId()), failure.reason());
        }
    }

    private static final class ResumeEngagementHandler implements ContinuationHandler {
        @Override public SceneContinuation.Kind kind() { return SceneContinuation.Kind.RESUME_ENGAGEMENT; }
        @Override public List<ProposedEvent> events(FrontierWorldState state, SceneContinuation continuation) {
            if (!(continuation instanceof SceneContinuation.ResumeEngagement resume)) throw invalid(continuation, kind());
            RouteEngagement engagement = state.strategicPlans().routeEngagements().get(resume.engagementId());
            if (engagement == null) throw new IllegalArgumentException("scene continuation has no route engagement");
            return List.of(new ProposedEvent(engagement.hiveId(), new ScheduleEffect.Created(
                    HiveRouteEngagementProcess.combat(engagement, resume.dueAt()))));
        }
    }

    private static final class ResumeSettlementAssaultHandler implements ContinuationHandler {
        @Override public SceneContinuation.Kind kind() { return SceneContinuation.Kind.RESUME_SETTLEMENT_ASSAULT; }
        @Override public List<ProposedEvent> events(FrontierWorldState state, SceneContinuation continuation) {
            if (!(continuation instanceof SceneContinuation.ResumeSettlementAssault resume)) throw invalid(continuation, kind());
            SettlementAssault assault = state.strategicPlans().settlementAssaults().get(resume.assaultId());
            if (assault == null) throw new IllegalArgumentException("scene continuation has no settlement assault");
            return List.of(new ProposedEvent(assault.hiveId(), new ScheduleEffect.Created(
                    HiveSettlementAssaultProcess.combat(assault, resume.dueAt()))));
        }
    }

    private static RouteOperation requireOperation(FrontierWorldState state, io.farfrontier.palemirror.frontier.v3.api.SubjectId operationId) {
        RouteOperation operation = state.operations().get(operationId);
        if (operation == null) throw new IllegalArgumentException("scene continuation has no route operation");
        return operation;
    }

    private static IllegalArgumentException invalid(SceneContinuation continuation, SceneContinuation.Kind expected) {
        return new IllegalArgumentException("scene continuation kind/type mismatch: expected " + expected + ", got " + continuation.getClass().getSimpleName());
    }
}
