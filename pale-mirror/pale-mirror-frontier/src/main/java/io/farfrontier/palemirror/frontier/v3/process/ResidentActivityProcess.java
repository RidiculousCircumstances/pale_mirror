package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.api.ScheduleId;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction;
import io.farfrontier.palemirror.frontier.v3.model.*;

import java.util.ArrayList;
import java.util.List;

/** Exact-resident wake for schedule, need and safe-checkpoint arbitration. */
public final class ResidentActivityProcess {
    public static final String REVIEW = "frontier.resident.activity.review";
    private static final long PENDING_RETRY_TICKS = 200L;
    private ResidentActivityProcess() { }

    public static ScheduledAction review(SubjectId residentId, long dueAt) {
        if (!residentId.value().startsWith("resident:") || dueAt < 1)
            throw new IllegalArgumentException("activity review needs one exact resident and due instant");
        return new ScheduledAction(new ScheduleId("schedule:resident-activity-"
                + residentId.value().substring("resident:".length())), new SimInstant(dueAt),
                11, residentId, REVIEW, 1);
    }

    /** Activity ownership changes here; the meal owner only changes meal and bread state. */
    public static FrontierWorldState reduceMealStarted(FrontierWorldState state, SubjectId subject,
                                                       ResidentMealStarted started) {
        FrontierWorldState next = ResidentMealProcess.reduceStarted(state, subject, started);
        return retargetHotResident(next, subject, started.meal().startedAtTick());
    }

    public static FrontierWorldState reduceMealReturned(FrontierWorldState state, SubjectId subject,
                                                        ResidentMealHotReturned returned, long atTick) {
        FrontierWorldState next = ResidentMealProcess.reduceHotReturned(state, subject, returned);
        return retargetHotResident(next, subject, atTick);
    }

    /** A completed meal changes activity eligibility immediately, not at the next day boundary. */
    public static ProposedEvent wakeAfterMeal(SubjectId residentId, long atTick) {
        ScheduledAction next = review(residentId, Math.addExact(atTick, 1L));
        return new ProposedEvent(residentId, new ScheduleEffect.Rescheduled(next.id(), next));
    }

    static FrontierWorldState retargetHotResident(FrontierWorldState state, SubjectId residentId, long atTick) {
        AmbientActorLease lease = state.ambientLeases().get(residentId);
        if (lease == null || lease.status() != AmbientLeaseStatus.HOT) return state;
        AmbientActorProcess.AmbientGoal goal = AmbientActorProcess.goalFor(state, residentId, atTick);
        return AmbientLeaseStateProcess.retarget(state, residentId, goal.kind(),
                BodyPosition.above(new SurfaceAnchor(goal.position())));
    }

    public static List<ProposedEvent> plan(FrontierWorldState state, ScheduledAction action) {
        if (!action.kind().equals(REVIEW) || !action.equals(review(action.subject(), action.dueAt().ticks())))
            throw new IllegalArgumentException("activity review has a foreign scheduled identity");
        ResidentProfile resident = state.humanPopulation().resident(action.subject());
        ActorLocation body = state.actorLocations().get(action.subject());
        if (resident == null || body == null || body.condition().status() != ActorLifeStatus.ALIVE)
            return List.of(new ProposedEvent(action.subject(), new ScheduleEffect.Consumed(action.id())));
        long now = action.dueAt().ticks();
        ResidentActivityChoice choice = ResidentActivityCoordinator.assess(state, action.subject(), now);
        List<ProposedEvent> events = new ArrayList<>();
        if (choice.kind() == ResidentActivityChoice.Kind.EAT
                && !state.humanPopulation().meals().containsKey(action.subject())) {
            ResidentMealProcess.selectSourceAtYield(state, action.subject(), now).ifPresent(started -> {
                events.add(new ProposedEvent(action.subject(), started));
                events.add(new ProposedEvent(action.subject(), new ScheduleEffect.Created(
                        ResidentMealProcess.progress(started.meal(), Math.addExact(now, 1L)))));
            });
        }
        long next = nextReview(state, resident, now, choice, !events.isEmpty(),
                state.humanPopulation().meals().containsKey(action.subject()));
        events.add(new ProposedEvent(action.subject(), new ScheduleEffect.Rescheduled(action.id(),
                review(action.subject(), next))));
        return List.copyOf(events);
    }

    private static long nextReview(FrontierWorldState state, ResidentProfile resident, long now,
                                   ResidentActivityChoice choice, boolean mealStarted,
                                   boolean mealAlreadyRetained) {
        FrontierRuleset.ResidentLife rules = state.bootstrap().ruleset().residentLife();
        long window = state.humanPopulation().schedule(resident.settlementId()).nextWindowBoundaryAfter(now);
        ResidentNutrition effective = state.humanPopulation().nutrition(resident.id()).accrueThrough(now, rules,
                resident.characteristics().effectiveMetabolismPermille(now));
        long hunger = effective.nextThresholdTick(rules,
                resident.characteristics().effectiveMetabolismPermille(now));
        long next = Math.min(window, hunger);
        if (!mealStarted && !mealAlreadyRetained
                && (choice.pending().isPresent() || choice.kind() == ResidentActivityChoice.Kind.EAT))
            next = Math.min(next, Math.addExact(now, PENDING_RETRY_TICKS));
        return next;
    }
}
