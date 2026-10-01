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
    // Unavailable owners are held without a WAL retry. A one-tick due fence
    // lets the exact waiter run promptly after source/work state changes.
    private static final long PENDING_RETRY_TICKS = 1L;
    private ResidentActivityProcess() { }

    public static ScheduledAction review(SubjectId residentId, long dueAt) {
        if (!residentId.value().startsWith("resident:") || dueAt < 1)
            throw new IllegalArgumentException("activity review needs one exact resident and due instant");
        return new ScheduledAction(new ScheduleId("schedule:resident-activity-"
                + residentId.value().substring("resident:".length())), new SimInstant(dueAt),
                11, residentId, REVIEW, 1);
    }

    /**
     * A resident without an executable source has no activity transition to
     * commit. Keep the exact due action in the engine queue until bread,
     * service access or the work checkpoint changes canonical state. The
     * independent need clock still integrates each hunger threshold.
     */
    public static boolean held(FrontierWorldState state, ScheduledAction action) {
        if (!REVIEW.equals(action.kind())) return false;
        SubjectId residentId = action.subject();
        ResidentProfile resident = state.humanPopulation().resident(residentId);
        ActorLocation actor = state.actorLocations().get(residentId);
        if (resident == null || actor == null || actor.condition().status() != ActorLifeStatus.ALIVE)
            return false;
        long now = Math.max(action.dueAt().ticks(), state.humanPopulation().nutrition(residentId).lastEvaluatedTick());
        if (state.humanPopulation().meals().containsKey(residentId)) return true;
        if (state.actorMovements().containsKey(residentId))
            return !(interruption(state, residentId, now,
                    ResidentActivityExecutionComposition.INTERRUPTION, true) instanceof ActivityInterruptionPlanner.Ready);
        ResidentNutrition nutrition = state.humanPopulation().nutrition(residentId).accrueThrough(now,
                state.bootstrap().ruleset().residentLife(), resident.characteristics().effectiveMetabolismPermille(now));
        if (!nutrition.wantsFood(state.bootstrap().ruleset().residentLife())) return false;
        if (ResidentMealOpportunity.candidate(state, residentId, now).isEmpty()) return true;
        return ResidentActivityCoordinator.assessEligibility(state, residentId, now).pending()
                .filter(wait -> wait == ResidentActivityChoice.Wait.SAFE_CHECKPOINT).isPresent();
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

    /** Meal receipts own food effects; activity orchestration alone retargets the retained HOT executor. */
    public static FrontierWorldState reduceMealEffectObserved(FrontierWorldState state, SubjectId subject,
            ResidentMealHotEffectObserved observed, long atTick) {
        return retargetHotResident(ResidentMealProcess.reduceHotObserved(state, subject, observed, atTick), subject, atTick);
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
        return plan(state, action, action.dueAt().ticks());
    }

    public static List<ProposedEvent> plan(FrontierWorldState state, ScheduledAction action, long currentTick) {
        return plan(state, action, currentTick, ResidentActivityExecutionComposition.INTERRUPTION);
    }

    /** Composition supplies the owner interruption port; selection never examines a service route. */
    static List<ProposedEvent> plan(FrontierWorldState state, ScheduledAction action, long currentTick,
                                   ActivityInterruptionPlanner interruptions) {
        if (!action.kind().equals(REVIEW) || !action.equals(review(action.subject(), action.dueAt().ticks())))
            throw new IllegalArgumentException("activity review has a foreign scheduled identity");
        ResidentProfile resident = state.humanPopulation().resident(action.subject());
        ActorLocation body = state.actorLocations().get(action.subject());
        if (resident == null || body == null || body.condition().status() != ActorLifeStatus.ALIVE)
            return List.of(new ProposedEvent(action.subject(), new ScheduleEffect.Consumed(action.id())));
        // A HOT consumption can advance this resident's need clock after an older
        // activity review was queued. The review is still due, but assessing hunger
        // at its historical instant would run time backwards and quarantine the world.
        long now = Math.max(Math.max(action.dueAt().ticks(), currentTick),
                state.humanPopulation().nutrition(action.subject()).lastEvaluatedTick());
        List<ProposedEvent> events = new ArrayList<>();
        if (state.actorMovements().containsKey(action.subject())) {
            var assessment = interruption(state, action.subject(), now, interruptions);
            if (assessment instanceof ActivityInterruptionPlanner.Ready ready) {
                events.addAll(ready.events());
                state = ready.following();
            }
        }
        FrontierWorldState selectedState = state;
        ResidentActivityChoice choice = ResidentActivityCoordinator.assess(selectedState, action.subject(), now);
        if (choice.kind() == ResidentActivityChoice.Kind.EAT
                && !state.humanPopulation().meals().containsKey(action.subject())
                && !state.actorMovements().containsKey(action.subject())) {
            ResidentMealProcess.selectSourceAtYield(selectedState, action.subject(), now).ifPresent(started -> {
                events.add(new ProposedEvent(action.subject(), started));
                events.add(new ProposedEvent(action.subject(), new ScheduleEffect.Created(
                        ResidentMealProcess.progress(started.meal(), Math.addExact(now, 1L)))));
            });
        }
        boolean mealStarted = events.stream().anyMatch(event -> event.payload() instanceof ResidentMealStarted);
        long next = nextReview(state, resident, now, choice, mealStarted,
                state.humanPopulation().meals().containsKey(action.subject()));
        events.add(new ProposedEvent(action.subject(), new ScheduleEffect.Rescheduled(action.id(),
                review(action.subject(), next))));
        return List.copyOf(events);
    }

    private static ActivityInterruptionPlanner.Assessment interruption(FrontierWorldState state,
            SubjectId residentId, long now, ActivityInterruptionPlanner planner) {
        return interruption(state, residentId, now, planner, false);
    }

    private static ActivityInterruptionPlanner.Assessment interruption(FrontierWorldState state,
            SubjectId residentId, long now, ActivityInterruptionPlanner planner, boolean eligibilityOnly) {
        var assessment = planner.assess(state, residentId, now);
        if (!(assessment instanceof ActivityInterruptionPlanner.Ready ready)) return assessment;
        ready.validate(state, residentId);
        ResidentActivityChoice next = eligibilityOnly
                ? ResidentActivityCoordinator.assessEligibility(ready.following(), residentId, now)
                : ResidentActivityCoordinator.assess(ready.following(), residentId, now);
        if (next.kind() == ResidentActivityChoice.Kind.WORK
                || next.kind() == ResidentActivityChoice.Kind.EAT
                    && (eligibilityOnly ? ResidentMealOpportunity.candidate(ready.following(), residentId, now).isPresent()
                        : ResidentMealOpportunity.find(ready.following(), residentId, now).isPresent())) return ready;
        // An optional idle journey continues unless a real higher-priority activity can replace it.
        return new ActivityInterruptionPlanner.Waiting(ActivityInterruptionPlanner.Reason.AUTHORITY_HANDOFF);
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
                && (choice.pending().isPresent() || effective.wantsFood(rules)))
            next = Math.min(next, Math.addExact(now, PENDING_RETRY_TICKS));
        return next;
    }
}
