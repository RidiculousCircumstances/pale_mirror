package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.CheckpointImage;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;

import java.util.Map;

/** One bounded read-only view of a resident's need, policy and retained activity. */
final class FrontierV3ResidentLifeDiagnostic {
    private FrontierV3ResidentLifeDiagnostic() { }

    static String render(String id, CheckpointImage checkpoint, FrontierWorldState state) {
        SubjectId subject = FrontierV3DiagnosticJson.subject(id).orElse(null);
        ResidentProfile resident = subject == null ? null : state.humanPopulation().resident(subject);
        if (resident == null) return FrontierV3DiagnosticJson.unavailable("resident_life", id, checkpoint, "not_found");
        long now = checkpoint.instant().ticks();
        ResidentNutrition stored = state.humanPopulation().nutrition(subject);
        ResidentNutrition effective = stored.accrueThrough(now, state.bootstrap().ruleset().residentLife());
        SettlementDailySchedule schedule = state.humanPopulation().schedule(resident.settlementId());
        HumanAssignment assignment = HumanAssignmentProjection.compile(state).assignment(subject);
        ResidentMeal meal = state.humanPopulation().meals().get(subject);
        String activity, pending = "", activityError = "", workYield = "";
        try {
            workYield = ResidentWorkYield.assess(state, assignment).status().name();
            ResidentActivityChoice choice = ResidentActivityCoordinator.assess(state, subject, now);
            activity = choice.kind().name();
            pending = choice.pending().map(Enum::name).orElse("");
        } catch (IllegalArgumentException invalid) {
            activity = "UNAVAILABLE";
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
        ActorLocation body = state.actorLocations().get(subject);
        return FrontierV3DiagnosticJson.base("resident_life", id, checkpoint)
                + ",\"status\":\"ok\",\"settlement\":\"" + quote(resident.settlementId().value())
                + "\",\"life\":\"" + (body == null ? "MISSING" : body.condition().status().name())
                + "\",\"nutrition\":\"" + effective.status().name()
                + "\",\"hungerDeficit\":" + effective.hungerDeficit()
                + ",\"storedHungerDeficit\":" + stored.hungerDeficit()
                + ",\"lastIntegratedDay\":" + stored.lastIntegratedDay()
                + ",\"scheduleWindow\":\"" + schedule.windowAt(now).name()
                + "\",\"nextScheduleBoundary\":" + schedule.nextWindowBoundaryAfter(now)
                + ",\"assignment\":\"" + assignment.kind().name()
                + "\",\"assignmentOwner\":\"" + quote(assignment.ownerId().map(SubjectId::value).orElse(""))
                + "\",\"activity\":\"" + activity + "\",\"pending\":\"" + pending
                + "\",\"workYield\":\"" + workYield + "\",\"activityError\":\"" + quote(activityError)
                + "\",\"mealPhase\":\"" + (meal == null ? "NONE" : meal.phase().name())
                + "\",\"mealWait\":\"" + (meal == null ? "" : meal.waitReason().map(Enum::name).orElse(""))
                + "\",\"mealClaim\":\"" + (meal == null ? "" : quote(meal.claimId().value()))
                + "\",\"depotBread\":" + bread + ",\"depotBreadClaimed\":" + claimed + "}";
    }

    private static String quote(String value) { return FrontierV3DiagnosticJson.quote(value); }
}
