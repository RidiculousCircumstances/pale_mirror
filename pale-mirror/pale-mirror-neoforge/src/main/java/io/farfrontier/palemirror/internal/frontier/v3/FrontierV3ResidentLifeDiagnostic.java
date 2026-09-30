package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.CheckpointImage;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.process.ResidentMealProcess;

import java.util.Map;

/** One bounded read-only view of a resident's need, policy and retained activity. */
final class FrontierV3ResidentLifeDiagnostic {
    private FrontierV3ResidentLifeDiagnostic() { }

    static String render(String id, CheckpointImage checkpoint, FrontierWorldState state) {
        SubjectId subject = FrontierV3DiagnosticJson.subject(id).orElse(null);
        ResidentProfile resident = subject == null ? null : state.humanPopulation().resident(subject);
        if (resident == null) return FrontierV3DiagnosticJson.unavailable("resident_life", id, checkpoint, "not_found");
        long now = checkpoint.instant().ticks();
        ActorLocation body = state.actorLocations().get(subject);
        boolean living = body != null && body.condition().status() == ActorLifeStatus.ALIVE;
        ResidentNutrition stored = state.humanPopulation().nutrition(subject);
        int metabolism = resident.characteristics().effectiveMetabolismPermille(now);
        ResidentNutrition effective = living ? stored.accrueThrough(now, state.bootstrap().ruleset().residentLife(), metabolism) : stored;
        SettlementDailySchedule schedule = state.humanPopulation().schedule(resident.settlementId());
        HumanAssignment assignment = HumanAssignmentProjection.compile(state).assignment(subject);
        ResidentMeal meal = state.humanPopulation().meals().get(subject);
        String activity, pending = "", activityError = "", workYield = "";
        try {
            if (!living) throw new IllegalArgumentException("resident is dead; need and activity timers are retired");
            workYield = ResidentWorkYield.assess(state, assignment).status().name();
            ResidentActivityChoice choice = ResidentActivityCoordinator.assess(state, subject, now);
            activity = choice.kind().name();
            pending = choice.pending().map(Enum::name).orElse("");
        } catch (IllegalArgumentException invalid) {
            activity = living ? "UNAVAILABLE" : "DEAD";
            activityError = String.valueOf(invalid.getMessage());
        }
        SubjectId depot = FrontierWorldState.depotId(resident.settlementId());
        CustodyAccount account = state.inventory().fungibleResources().accounts()
                .get(ReferenceContainerCustody.scopeId(depot));
        var resources = state.inventory().fungibleResources();
        int bread = account == null ? 0 : account.lotQuantities().entrySet().stream()
                .filter(entry -> ResidentMeal.BREAD_KIND.equals(resources.lots().get(entry.getKey()).itemKind()))
                .mapToInt(Map.Entry::getValue).sum();
        int claimed = account == null ? 0 : account.claimQuantities().entrySet().stream()
                .filter(entry -> ResidentMeal.BREAD_KIND.equals(resources.claims().get(entry.getKey()).itemKind()))
                .mapToInt(Map.Entry::getValue).sum();
        String modifiers = resident.characteristics().metabolismModifiers().values().stream()
                .sorted(java.util.Comparator.comparing(ResidentCharacteristics.MetabolismModifier::sourceId))
                .map(modifier -> "{\"source\":\"" + quote(modifier.sourceId().value())
                        + "\",\"deltaPermille\":" + modifier.deltaPermille() + "}")
                .collect(java.util.stream.Collectors.joining(",", "[", "]"));
        var actualNeedDue = checkpoint.schedules().stream()
                .filter(action -> action.subject().equals(subject) && action.kind().equals("frontier.resident.need.review"))
                .mapToLong(action -> action.dueAt().ticks()).min();
        var mealAction = checkpoint.schedules().stream()
                .filter(action -> action.subject().equals(subject) && action.kind().equals(ResidentMealProcess.PROGRESS))
                .findFirst();
        return FrontierV3DiagnosticJson.base("resident_life", id, checkpoint)
                + ",\"status\":\"ok\",\"settlement\":\"" + quote(resident.settlementId().value())
                + "\",\"life\":\"" + (body == null ? "MISSING" : body.condition().status().name())
                + "\",\"nutrition\":\"" + effective.status().name()
                + "\",\"hungerDeficit\":" + effective.hungerDeficit()
                + ",\"storedHungerDeficit\":" + stored.hungerDeficit()
                + ",\"lastEvaluatedTick\":" + stored.lastEvaluatedTick()
                + ",\"fractionalHungerProgress\":" + stored.fractionalProgress()
                + ",\"metabolismBasePermille\":" + resident.characteristics().baseMetabolismPermille()
                + ",\"metabolismModifiers\":" + modifiers
                + ",\"metabolismEffectivePermille\":" + metabolism
                + ",\"nextHungerThresholdTick\":" + (living
                    ? Long.toString(effective.nextThresholdTick(state.bootstrap().ruleset().residentLife(), metabolism)) : "null")
                + ",\"nextNeedActionAt\":" + (actualNeedDue.isPresent() ? Long.toString(actualNeedDue.getAsLong()) : "null")
                + ",\"scheduleWindow\":\"" + schedule.windowAt(now).name()
                + "\",\"nextScheduleBoundary\":" + schedule.nextWindowBoundaryAfter(now)
                + ",\"assignment\":\"" + assignment.kind().name()
                + "\",\"assignmentOwner\":\"" + quote(assignment.ownerId().map(SubjectId::value).orElse(""))
                + "\",\"activity\":\"" + activity + "\",\"pending\":\"" + pending
                + "\",\"workYield\":\"" + workYield + "\",\"activityError\":\"" + quote(activityError)
                + "\",\"mealPhase\":\"" + (meal == null ? "NONE" : meal.phase().name())
                + "\",\"mealWait\":\"" + (meal == null ? "" : meal.waitReason().map(Enum::name).orElse(""))
                + "\",\"mealClaim\":\"" + (meal == null ? "" : quote(meal.claimId().value()))
                + "\",\"mealActionDueAt\":" + (mealAction.isPresent() ? mealAction.orElseThrow().dueAt().ticks() : "null")
                + ",\"mealActionHeld\":" + mealAction.map(action -> ResidentMealProcess.held(state, action)).orElse(false)
                + ",\"mealTravelArrivalAt\":" + (meal == null ? "null" : meal.coldTravel()
                    .map(route -> Long.toString(route.arrivalTick())).orElse("null"))
                + ",\"mealAtWaitingPocket\":" + (meal != null && ResidentMealKnownNavigation.atWaitingPocket(state, meal))
                + ",\"mealServiceAvailable\":" + (meal != null && ServiceAccessCoordinator.depotAvailableForMeal(
                    state, meal.depotId(), meal.residentId()))
                + ",\"depotBread\":" + bread + ",\"depotBreadClaimed\":" + claimed + "}";
    }

    private static String quote(String value) { return FrontierV3DiagnosticJson.quote(value); }
}
