package io.farfrontier.palemirror.frontier.v3.model;

import java.util.Objects;
import java.util.Optional;

/** Pure first-cut arbitration. An owner adapter, not this selector, proves the safe checkpoint. */
public final class ResidentActivityCoordinator {
    private ResidentActivityCoordinator() { }

    /** Derived assessment only; a work owner must acknowledge its yield before EAT starts. */
    public static ResidentActivityChoice assess(FrontierWorldState state, io.farfrontier.palemirror.frontier.v3.api.SubjectId residentId,
                                                long canonicalTick) {
        Objects.requireNonNull(state, "activity state");
        ResidentProfile resident = state.humanPopulation().resident(residentId);
        if (resident == null) throw new IllegalArgumentException("activity has no exact resident");
        HumanAssignment assignment = HumanAssignmentProjection.compile(state).assignment(residentId);
        ResidentWorkYield checkpoint = ResidentWorkYield.assess(state, assignment);
        return choose(state.humanPopulation().schedule(resident.settlementId()), canonicalTick,
                state.humanPopulation().nutrition(residentId), assignment,
                Optional.ofNullable(state.humanPopulation().meals().get(residentId)),
                state.bootstrap().ruleset().residentLife(), checkpoint.ready());
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
                FrontierRuleset.ResidentLife.initial(), safeToYield);
    }

    public static ResidentActivityChoice choose(SettlementDailySchedule schedule, long canonicalTick,
                                                ResidentNutrition nutrition,
                                                HumanAssignment assignment,
                                                Optional<ResidentMeal> retainedMeal,
                                                FrontierRuleset.ResidentLife rules,
                                                boolean safeToYield) {
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
            // After confirmed consumption the deficit may be zero, but RETURN is still the
            // same retained activity; work must not retake the body before it completes.
            return new ResidentActivityChoice(assignment.residentId(),
                    ResidentActivityChoice.Kind.EAT, meal.retainedWorkOwner(), Optional.empty());
        }
        var need = nutrition.accrueThrough(canonicalTick, rules);
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
