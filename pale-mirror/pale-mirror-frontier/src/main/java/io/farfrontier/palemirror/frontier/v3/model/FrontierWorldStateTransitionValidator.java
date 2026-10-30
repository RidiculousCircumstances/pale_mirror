package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.kernel.StateValidator;
import io.farfrontier.palemirror.frontier.v3.kernel.FrontierExecutionMetrics;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduleReadView;
import io.farfrontier.palemirror.frontier.v3.api.FrontierEvent;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus;

import java.util.List;

/** Kernel-bound canonical validator: complete at ingress, dependency-aware before each WAL append. */
public final class FrontierWorldStateTransitionValidator implements StateValidator<FrontierWorldState> {
    public static final FrontierWorldStateTransitionValidator INSTANCE = new FrontierWorldStateTransitionValidator();

    private FrontierWorldStateTransitionValidator() { }

    @Override public void validateInitial(FrontierWorldState state) {
        ImmutableInvariantCheck.full(() -> { state.validateComplete(); FrontierReferenceClosure.validate(state, List.of()); return null; });
    }

    @Override public void validateRecoveryInitial(FrontierWorldState state, List<ScheduledAction> schedules) {
        ImmutableInvariantCheck.full(() -> { state.validateComplete(); FrontierReferenceClosure.validate(state, schedules); return null; });
    }

    @Override public void validateTransition(FrontierWorldState previous, FrontierWorldState next) {
        next.validateTransitionFrom(previous);
    }

    @Override public void validateScheduleChanges(FrontierWorldState state, List<ScheduledAction> retainedChanges) {
        FrontierReferenceClosure.validateScheduledSubjects(state, retainedChanges);
    }
    @Override public void validateScheduleEffect(FrontierWorldState state,
            io.farfrontier.palemirror.frontier.v3.api.SubjectId subject, ScheduleEffect effect) {
        StateValidator.super.validateScheduleEffect(state, subject, effect);
        if (effect instanceof ScheduleEffect.ReconsiderationRequested requested)
            io.farfrontier.palemirror.frontier.v3.process.FrontierWorldProcessCatalog.requireReconsideration(requested.action());
    }

    @Override public void validateTransaction(FrontierWorldState previous, FrontierWorldState next, List<FrontierEvent> events,
                                              ScheduleReadView schedulesBefore,
                                              ScheduleReadView schedulesAfter,
                                              List<ScheduledAction> retainedChanges) {
        validateTransaction(previous, next, events, schedulesBefore, schedulesAfter, retainedChanges, FrontierExecutionMetrics.noOp());
    }
    @Override public void validateTransaction(FrontierWorldState previous, FrontierWorldState next, List<FrontierEvent> events,
                                              ScheduleReadView schedulesBefore,
                                              ScheduleReadView schedulesAfter,
                                              List<ScheduledAction> retainedChanges, FrontierExecutionMetrics metrics) {
        String owner = next.bootstrap().worldId().value();
        try (var ignored = FrontierExecutionMetrics.safelyDetail(metrics, FrontierExecutionMetrics.Stage.VALIDATION, "owned-state", owner)) {
            ImmutableValidationScope.observed(metrics, owner, () -> { validateTransition(previous, next); return null; });
        }
        // One unchanged publication barrier; nested spans only explain its cost.
        try (var ignored = FrontierExecutionMetrics.safelyDetail(metrics, FrontierExecutionMetrics.Stage.VALIDATION, "reference-closure", owner)) {
            FrontierReferenceClosure.validateTransition(previous, next, schedulesAfter, retainedChanges, metrics);
        }
        try (var ignored = FrontierExecutionMetrics.safelyDetail(metrics, FrontierExecutionMetrics.Stage.VALIDATION, "retirement-proofs", owner)) {
            validateRetirements(previous, events, schedulesBefore, schedulesAfter);
        }
    }
    private void validateRetirements(FrontierWorldState previous, List<FrontierEvent> events,
                                     ScheduleReadView schedulesBefore,
                                     ScheduleReadView schedulesAfter) {
        for (FrontierEvent event : events) {
            if (!(event.payload() instanceof PhysicalIntentTransition transition) || !terminal(transition.status())) continue;
            PhysicalIntentRetirementProof proof = transition.retirementProof().orElseThrow(() ->
                    new IllegalArgumentException("terminal physical transition lacks durable retirement proof at transaction boundary"));
            if (!proof.intentId().equals(transition.intentId())) throw new IllegalArgumentException("retirement proof does not bind its terminal intent");
            var intent = previous.physicalIntents().get(transition.intentId());
            if (intent == null || intent.lifecycleOwner() != proof.owner()) throw new IllegalArgumentException("retirement proof owner does not match authoritative intent");
            if (proof.relations() instanceof PhysicalIntentRetirementProof.ExactRelations exact
                    && !FrontierDomainRelationships.view(previous).edges().containsAll(exact.value())) {
                throw new IllegalArgumentException("retirement proof relation is absent from authoritative pre-state");
            }
            validateSubject(proof.leaseOrCarrier(), proof.relations(), "lease/carrier");
            validateSubject(proof.commitment(), proof.relations(), "resource commitment");
            if (proof.continuation() instanceof PhysicalIntentRetirementProof.ExactSchedule exact) {
                if (schedulesBefore.action(exact.value()).isEmpty()) {
                    throw new IllegalArgumentException("retirement proof schedule is absent from engine pre-state");
                }
                long dispositions = events.stream().map(FrontierEvent::payload).filter(ScheduleEffect.class::isInstance)
                        .map(ScheduleEffect.class::cast).filter(effect -> affects(effect, exact.value())).count();
                if (dispositions != 1L || schedulesAfter.action(exact.value()).isPresent()) {
                    throw new IllegalArgumentException("retirement proof schedule does not have one engine-owned disposition");
                }
            } else if (events.stream().map(FrontierEvent::payload).filter(ScheduleEffect.class::isInstance).map(ScheduleEffect.class::cast)
                    .anyMatch(effect -> !(effect instanceof ScheduleEffect.Created)
                            && !(effect instanceof ScheduleEffect.ReconsiderationRequested))) {
                // A checked-none continuation means this terminal account owns no engine action
                // in this atomic transaction.  A transaction carrying a schedule disposition
                // must name that action exactly; it cannot hide it behind a none claim.
                throw new IllegalArgumentException("retirement proof falsely declares no engine continuation for a scheduled transaction");
            }
            long duplicates = events.stream().map(FrontierEvent::payload).filter(PhysicalIntentTransition.class::isInstance)
                    .map(PhysicalIntentTransition.class::cast).filter(other -> terminal(other.status()) && other.intentId().equals(transition.intentId())).count();
            if (duplicates != 1L) throw new IllegalArgumentException("transaction duplicates terminal retirement for one physical intent");
        }
    }

    private static boolean terminal(PhysicalIntentStatus status) { return status != PhysicalIntentStatus.RUNNING && status != PhysicalIntentStatus.PREPARED; }
    private static void validateSubject(PhysicalIntentRetirementProof.SubjectObligation subject,
                                        PhysicalIntentRetirementProof.RelationObligation relations, String dimension) {
        if (subject instanceof PhysicalIntentRetirementProof.ExactSubject exact
                && relations instanceof PhysicalIntentRetirementProof.ExactRelations edges
                && edges.value().stream().noneMatch(edge -> endpoint(edge.owner(), exact.value()) || endpoint(edge.source(), exact.value()) || endpoint(edge.target(), exact.value()))) {
            throw new IllegalArgumentException("retirement proof " + dimension + " lacks its exact relation account");
        }
    }
    private static boolean endpoint(FrontierDomainRelationships.Endpoint endpoint, io.farfrontier.palemirror.frontier.v3.api.SubjectId id) {
        return endpoint instanceof FrontierDomainRelationships.SubjectEndpoint subject && subject.id().equals(id);
    }
    private static boolean affects(ScheduleEffect effect, io.farfrontier.palemirror.frontier.v3.api.ScheduleId id) {
        return switch (effect) {
            case ScheduleEffect.Cancelled cancelled -> cancelled.scheduleId().equals(id);
            case ScheduleEffect.Consumed consumed -> consumed.scheduleId().equals(id);
            case ScheduleEffect.Rescheduled rescheduled -> rescheduled.scheduleId().equals(id);
            case ScheduleEffect.Created ignored -> false;
            case ScheduleEffect.ReconsiderationRequested ignored -> false;
        };
    }
}
