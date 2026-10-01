package io.farfrontier.palemirror.frontier.v3.model;

import java.util.Objects;
import java.util.Optional;

/** Pure first-cut arbitration. An owner adapter, not this selector, proves the safe checkpoint. */
public final class ResidentActivityCoordinator {
    private ResidentActivityCoordinator() { }

    /** Admission policy for an as-yet-unassigned worker; WORK cannot be selected before the job exists. */
    public static boolean mayStartOrdinaryWork(FrontierWorldState state,
                                               io.farfrontier.palemirror.frontier.v3.api.SubjectId residentId,
                                               long dueAt) {
        ResidentProfile resident = state.humanPopulation().resident(residentId);
        if (resident == null || state.humanPopulation().meals().containsKey(residentId)
                || state.actorMovements().containsKey(residentId)) return false;
        long assessedAt = Math.max(dueAt, state.humanPopulation().nutrition(residentId).lastEvaluatedTick());
        return state.humanPopulation().schedule(resident.settlementId()).windowAt(assessedAt)
                == SettlementDailySchedule.Window.WORK
                && (state.humanPopulation().nutrition(residentId).accrueThrough(assessedAt,
                        state.bootstrap().ruleset().residentLife(),
                        resident.characteristics().effectiveMetabolismPermille(assessedAt)).hungerDeficit()
                    < state.bootstrap().ruleset().residentLife().hungryThreshold()
                    || ResidentMealOpportunity.find(state, residentId).isEmpty());
    }

    public static long nextOrdinaryWorkAdmission(FrontierWorldState state,
                                                 io.farfrontier.palemirror.frontier.v3.api.SubjectId residentId,
                                                 long dueAt) {
        ResidentProfile resident = state.humanPopulation().resident(residentId);
        long assessedAt = Math.max(dueAt, state.humanPopulation().nutrition(residentId).lastEvaluatedTick());
        return state.humanPopulation().schedule(resident.settlementId()).windowAt(assessedAt)
                == SettlementDailySchedule.Window.FREE
                ? state.humanPopulation().schedule(resident.settlementId()).nextWindowBoundaryAfter(assessedAt)
                : Math.addExact(assessedAt, 20L);
    }

    /** The scene owner checks this request at its own physical safe point, before new work. */
    public static boolean requestsYield(FrontierWorldState state,
                                        io.farfrontier.palemirror.frontier.v3.api.SubjectId residentId,
                                        long canonicalTick) {
        ResidentProfile resident = state.humanPopulation().resident(residentId);
        if (resident == null) return false;
        long assessedAt = Math.max(canonicalTick, state.humanPopulation().nutrition(residentId).lastEvaluatedTick());
        return state.humanPopulation().meals().containsKey(residentId)
                || state.actorMovements().containsKey(residentId)
                || state.humanPopulation().schedule(resident.settlementId()).windowAt(assessedAt)
                    == SettlementDailySchedule.Window.FREE
                || assess(state, residentId, assessedAt).kind() == ResidentActivityChoice.Kind.EAT;
    }

    /** A work owner asks the same arbiter used by self-care before admitting its next safe step. */
    public static boolean shouldYieldAtOwnerCheckpoint(FrontierWorldState state,
            io.farfrontier.palemirror.frontier.v3.api.SubjectId residentId, long atTick) {
        return requestsYield(state, residentId, atTick)
                && ActivityExecutionCapabilities.assess(state,
                    HumanAssignmentProjection.compile(state).assignment(residentId)).ready();
    }

    /** A work owner asks the same arbiter used by self-care before admitting its next safe step. */
    public static boolean ordinaryWorkPermitted(FrontierWorldState state,
                                                io.farfrontier.palemirror.frontier.v3.api.SubjectId residentId,
                                                long dueAt) {
        if (state.actorMovements().containsKey(residentId)) return false;
        long assessedAt = Math.max(dueAt, state.humanPopulation().nutrition(residentId).lastEvaluatedTick());
        return assess(state, residentId, assessedAt).kind() == ResidentActivityChoice.Kind.WORK;
    }

    /** Preserve the retained job while FREE or EAT owns the person; never spin on a held due action. */
    public static long nextOrdinaryWorkCheck(FrontierWorldState state,
                                             io.farfrontier.palemirror.frontier.v3.api.SubjectId residentId,
                                             long dueAt) {
        long assessedAt = Math.max(dueAt, state.humanPopulation().nutrition(residentId).lastEvaluatedTick());
        ResidentProfile resident = state.humanPopulation().resident(residentId);
        return assess(state, residentId, assessedAt).kind() == ResidentActivityChoice.Kind.IDLE
                ? state.humanPopulation().schedule(resident.settlementId()).nextWindowBoundaryAfter(assessedAt)
                : Math.addExact(assessedAt, 20L);
    }

    /** Derived assessment only; a work owner must acknowledge its yield before EAT starts. */
    public static ResidentActivityChoice assess(FrontierWorldState state, io.farfrontier.palemirror.frontier.v3.api.SubjectId residentId,
                                                long canonicalTick) {
        Objects.requireNonNull(state, "activity state");
        ResidentProfile resident = state.humanPopulation().resident(residentId);
        if (resident == null) throw new IllegalArgumentException("activity has no exact resident");
        long assessedAt = Math.max(canonicalTick, state.humanPopulation().nutrition(residentId).lastEvaluatedTick());
        HumanAssignment assignment = HumanAssignmentProjection.compile(state).assignment(residentId);
        ResidentWorkYield checkpoint = ResidentWorkYield.assess(state, assignment);
        ResidentActivityChoice choice = choose(state.humanPopulation().schedule(resident.settlementId()), assessedAt,
                state.humanPopulation().nutrition(residentId), assignment,
                Optional.ofNullable(state.humanPopulation().meals().get(residentId)),
                state.bootstrap().ruleset().residentLife(), checkpoint.ready(),
                resident.characteristics().effectiveMetabolismPermille(assessedAt));
        if (state.humanPopulation().meals().containsKey(residentId)
                || state.humanPopulation().nutrition(residentId).accrueThrough(assessedAt,
                        state.bootstrap().ruleset().residentLife(),
                        resident.characteristics().effectiveMetabolismPermille(assessedAt)).hungerDeficit()
                    < state.bootstrap().ruleset().residentLife().hungryThreshold()
                || ResidentMealOpportunity.find(state, residentId).isPresent()) return choice;
        // No executable meal exists. Keep working in WORK, or obey FREE at a safe point;
        // the activity wake still retries food independently without losing the assignment.
        if (state.humanPopulation().schedule(resident.settlementId()).windowAt(assessedAt)
                == SettlementDailySchedule.Window.WORK && assignment.active())
            return new ResidentActivityChoice(residentId, ResidentActivityChoice.Kind.WORK,
                    assignment.ownerId(), Optional.empty());
        if (choice.kind() == ResidentActivityChoice.Kind.WORK) return choice;
        return new ResidentActivityChoice(residentId, ResidentActivityChoice.Kind.IDLE,
                assignment.ownerId(), Optional.empty());
    }

    public static ResidentActivityChoice choose(SettlementDailySchedule schedule, long canonicalTick,
                                                ResidentNutrition nutrition,
                                                HumanAssignment assignment,
                                                boolean safeToYield) {
        return choose(schedule, canonicalTick, nutrition, assignment, Optional.empty(), safeToYield);
    }

    public static ResidentActivityChoice choose(SettlementDailySchedule schedule, long canonicalTick,
                                                ResidentNutrition nutrition,
                                                HumanAssignment assignment,
                                                Optional<ResidentMeal> retainedMeal,
                                                boolean safeToYield) {
        return choose(schedule, canonicalTick, nutrition, assignment, retainedMeal,
                FrontierRuleset.ResidentLife.initial(), safeToYield,
                ResidentCharacteristics.DEFAULT_METABOLISM_PERMILLE);
    }

    public static ResidentActivityChoice choose(SettlementDailySchedule schedule, long canonicalTick,
                                                ResidentNutrition nutrition,
                                                HumanAssignment assignment,
                                                Optional<ResidentMeal> retainedMeal,
                                                FrontierRuleset.ResidentLife rules,
                                                boolean safeToYield) {
        return choose(schedule, canonicalTick, nutrition, assignment, retainedMeal, rules,
                safeToYield, ResidentCharacteristics.DEFAULT_METABOLISM_PERMILLE);
    }

    public static ResidentActivityChoice choose(SettlementDailySchedule schedule, long canonicalTick,
                                                ResidentNutrition nutrition,
                                                HumanAssignment assignment,
                                                Optional<ResidentMeal> retainedMeal,
                                                FrontierRuleset.ResidentLife rules,
                                                boolean safeToYield, int metabolismPermille) {
        Objects.requireNonNull(schedule, "settlement schedule");
        Objects.requireNonNull(nutrition, "resident need");
        Objects.requireNonNull(assignment, "resident assignment");
        Objects.requireNonNull(rules, "resident life rules");
        retainedMeal = Objects.requireNonNull(retainedMeal, "retained resident meal");
        if (retainedMeal.isPresent()) {
            ResidentMeal meal = retainedMeal.orElseThrow();
            if (!meal.residentId().equals(assignment.residentId()))
                throw new IllegalArgumentException("retained meal cannot replace a foreign resident activity");
            if (!meal.retainedWorkOwner().equals(assignment.ownerId()))
                throw new IllegalArgumentException("retained meal lost or replaced its exact work assignment");
            // An unconsumed retained meal owns execution; consumption retires it.
            // Optional later movement is a separate, safely interruptible activity.
            return new ResidentActivityChoice(assignment.residentId(),
                    ResidentActivityChoice.Kind.EAT, meal.retainedWorkOwner(), Optional.empty());
        }
        var need = nutrition.accrueThrough(canonicalTick, rules, metabolismPermille);
        boolean activeWork = assignment.active();
        if (need.hungerDeficit() >= rules.hungryThreshold()) {
            if (!activeWork || safeToYield) return new ResidentActivityChoice(assignment.residentId(),
                    ResidentActivityChoice.Kind.EAT, assignment.ownerId(), Optional.empty());
            return new ResidentActivityChoice(assignment.residentId(), ResidentActivityChoice.Kind.WORK,
                    assignment.ownerId(), Optional.of(ResidentActivityChoice.Wait.SAFE_CHECKPOINT));
        }
        if (schedule.windowAt(canonicalTick) == SettlementDailySchedule.Window.FREE) {
            if (!activeWork || safeToYield) return new ResidentActivityChoice(assignment.residentId(),
                    ResidentActivityChoice.Kind.IDLE, assignment.ownerId(), Optional.empty());
            return new ResidentActivityChoice(assignment.residentId(), ResidentActivityChoice.Kind.WORK,
                    assignment.ownerId(), Optional.of(ResidentActivityChoice.Wait.SAFE_CHECKPOINT));
        }
        return new ResidentActivityChoice(assignment.residentId(),
                activeWork ? ResidentActivityChoice.Kind.WORK : ResidentActivityChoice.Kind.IDLE,
                assignment.ownerId(), Optional.empty());
    }
}
