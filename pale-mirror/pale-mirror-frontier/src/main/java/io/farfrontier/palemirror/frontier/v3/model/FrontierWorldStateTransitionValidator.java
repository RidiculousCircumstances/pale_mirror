package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.kernel.StateValidator;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction;
import io.farfrontier.palemirror.frontier.v3.api.FrontierEvent;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus;

import java.util.List;

/** Kernel-bound canonical validator: complete at ingress, dependency-aware before each WAL append. */
public final class FrontierWorldStateTransitionValidator implements StateValidator<FrontierWorldState> {
    public static final FrontierWorldStateTransitionValidator INSTANCE = new FrontierWorldStateTransitionValidator();

    private FrontierWorldStateTransitionValidator() { }

    @Override public void validateInitial(FrontierWorldState state) { state.validateComplete(); }

    @Override public void validateTransition(FrontierWorldState previous, FrontierWorldState next) {
        next.validateTransitionFrom(previous);
    }

    @Override public void validateTransaction(FrontierWorldState previous, FrontierWorldState next, List<FrontierEvent> events,
                                              List<ScheduledAction> schedulesBefore, List<ScheduledAction> schedulesAfter) {
        validateTransition(previous, next);
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
                if (schedulesBefore.stream().noneMatch(action -> action.id().equals(exact.value()))) {
                    throw new IllegalArgumentException("retirement proof schedule is absent from engine pre-state");
                }
                long dispositions = events.stream().map(FrontierEvent::payload).filter(ScheduleEffect.class::isInstance)
                        .map(ScheduleEffect.class::cast).filter(effect -> affects(effect, exact.value())).count();
                if (dispositions != 1L || schedulesAfter.stream().anyMatch(action -> action.id().equals(exact.value()))) {
                    throw new IllegalArgumentException("retirement proof schedule does not have one engine-owned disposition");
                }
            } else if (events.stream().map(FrontierEvent::payload).filter(ScheduleEffect.class::isInstance).map(ScheduleEffect.class::cast)
                    .anyMatch(effect -> !(effect instanceof ScheduleEffect.Created))) {
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
        };
    }
}
