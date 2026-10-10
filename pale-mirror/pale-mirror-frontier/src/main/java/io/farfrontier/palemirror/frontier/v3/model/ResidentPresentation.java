package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.group.UnitGroup;
import java.util.Locale;

/** Read-only inspection projection. Labels never grant work, capability or execution authority. */
public record ResidentPresentation(String name, String settlement, String task, String role, String activity) {
    public static ResidentPresentation from(FrontierWorldState state, SubjectId actor) {
        var resident = FrontierWorldStateSupport.resident(state, actor);
        var home = FrontierWorldStateSupport.settlement(state.bootstrap(), resident.settlementId());
        var assignment = HumanAssignmentProjection.compile(state).assignment(actor);
        String task = switch (assignment.kind()) {
            case IDLE -> "None";
            case FIELD_HARVEST -> "Harvest and deliver crops";
            case EXTRACTION -> "Extract and store resources";
            case PRODUCTION -> "Production";
            case COURIER -> "Transport resources";
            case GROUP_MEMBER -> "Expedition";
            case ROUTE_PATROL -> "Route patrol";
            case SETTLEMENT_DEFENCE -> "Settlement defence";
            case ENGINEERING_RECOVERY -> "Repair infrastructure";
            case SETTLEMENT_SERVICE -> "Facility service";
            case MEDICAL_EVACUATION -> "Treat and evacuate patient";
            case TRANSIT -> "Relocation";
        };
        String role = switch (assignment.kind()) {
            case IDLE -> "Unassigned";
            case FIELD_HARVEST -> "Farmer";
            case EXTRACTION -> "Miner";
            case PRODUCTION -> assignment.ownerId().map(state.productionJobs()::get)
                    .filter(job -> job.bakeryWork().isPresent()).isPresent() ? "Baker" : "Production worker";
            case COURIER -> "Carrier";
            case GROUP_MEMBER -> "Companion";
            case ROUTE_PATROL -> "Guard";
            case SETTLEMENT_DEFENCE -> words(HumanTacticalFunctionProjection.derive(state, actor).name());
            case ENGINEERING_RECOVERY -> "Engineer";
            case SETTLEMENT_SERVICE -> "Service worker";
            case MEDICAL_EVACUATION -> "Medical worker";
            case TRANSIT -> "Traveller";
        };
        if (assignment.kind() == HumanAssignmentKind.MEDICAL_EVACUATION) {
            var operation = state.humanPopulation().medicalOperations().get(assignment.ownerId().orElseThrow());
            if (operation.patientId().equals(actor)) {
                task = "Receive medical care";
                role = "Patient";
            }
        }
        var groups = state.unitGroups().groups().values().stream()
                .filter(group -> group.phase() != UnitGroup.Phase.CLOSED)
                .filter(group -> group.members().stream().anyMatch(member -> member.actorId().equals(actor))).toList();
        if (groups.size() > 1) throw new IllegalStateException("resident inspection has competing group memberships");
        if (!groups.isEmpty()) {
            var group = groups.getFirst();
            task = words(group.mission().kind().name()) + " · " + words(group.phase().name());
            role = switch (group.member(actor).role()) {
                case CARRIER -> "Carrier";
                case ESCORT -> "Escort";
                case GUIDE -> "Companion";
            };
        }
        var meal = state.humanPopulation().meals().get(actor);
        String activity;
        if (meal != null) activity = switch (meal.phase()) {
            case MOVE -> "Going for food";
            case TAKE -> "Taking food";
            case CLEAR_ACCESS -> "Leaving service point";
            case CONSUME -> "Eating";
        };
        else if (state.actorMovements().containsKey(actor)) activity = "Moving";
        else {
            var execution = state.actorExecutions().actors().get(actor);
            activity = execution == null || execution.current().isEmpty() ? "Idle"
                    : words(execution.current().orElseThrow().activityKind().name());
        }
        return new ResidentPresentation(resident.name(), home.displayName(), task, role, activity);
    }

    private static String words(String value) { return value.toLowerCase(Locale.ROOT).replace('_', ' '); }
}
