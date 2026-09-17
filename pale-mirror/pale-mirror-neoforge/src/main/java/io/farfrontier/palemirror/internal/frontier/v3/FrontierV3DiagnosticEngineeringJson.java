package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.CheckpointImage;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.ActorLocation;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.RouteConstruction;
import io.farfrontier.palemirror.frontier.v3.model.RouteMaintenance;
import io.farfrontier.palemirror.frontier.v3.process.EngineeringEquipmentProcess;

/** Owns the bounded read-only diagnostic projection for retained engineering routes. */
final class FrontierV3DiagnosticEngineeringJson {
    private FrontierV3DiagnosticEngineeringJson() { }

    static String routeConstruction(String id, CheckpointImage checkpoint, FrontierWorldState state) {
        SubjectId settlement = FrontierV3DiagnosticJson.subject(id).orElse(null);
        RouteConstruction project = settlement == null ? null : state.routeConstructions().values().stream()
                .filter(value -> value.settlementId().equals(settlement)).sorted(java.util.Comparator.comparing(RouteConstruction::id)).findFirst().orElse(null);
        if (project == null) return FrontierV3DiagnosticJson.unavailable("route_construction", id, checkpoint, "not_found");
        java.util.List<io.farfrontier.palemirror.frontier.v3.model.BlockPosition> cells = io.farfrontier.palemirror.frontier.v3.model.FrontierGrayboxPlan.routeConstructionCells(state, project);
        String next = project.confirmedCells() == cells.size() ? "null" : FrontierV3DiagnosticJson.position(cells.get(project.confirmedCells()));
        String team = project.team().map(value -> team(state, value)).orElse(",\"teamPresent\":false,\"teamFullyEquipped\":false,\"teamMembers\":[]");
        String assembly = project.assembly().map(value -> assembly(state, value.members(), value.purpose(), value.complete())).orElse(",\"assemblyPresent\":false");
        long pendingProjectIntents = pendingIntents(state, project.id(), true);
        long pendingOtherIntents = pendingIntents(state, project.id(), false);
        return FrontierV3DiagnosticJson.base("route_construction", id, checkpoint) + ",\"status\":\"ok\",\"project\":\"" + FrontierV3DiagnosticJson.quote(project.id().value())
                + "\",\"phase\":\"" + project.status() + "\",\"confirmedCells\":" + project.confirmedCells() + ",\"requiredCells\":" + cells.size()
                + ",\"cargo\":\"" + FrontierV3DiagnosticJson.quote(project.cargoId().map(SubjectId::value).orElse("")) + "\",\"cargoPresent\":" + project.cargoId().isPresent()
                + ",\"nextCell\":" + next + ",\"pendingProjectIntents\":" + pendingProjectIntents + ",\"pendingOtherIntents\":" + pendingOtherIntents + team + assembly + "}";
    }

    static String routeMaintenance(String id, CheckpointImage checkpoint, FrontierWorldState state) {
        SubjectId settlement = FrontierV3DiagnosticJson.subject(id).orElse(null);
        RouteMaintenance maintenance = settlement == null ? null : state.routeMaintenances().values().stream()
                .filter(value -> value.settlementId().equals(settlement)).sorted(java.util.Comparator.comparing(RouteMaintenance::id)).findFirst().orElse(null);
        if (maintenance == null) return FrontierV3DiagnosticJson.unavailable("route_maintenance", id, checkpoint, "not_found");
        String members = maintenance.team().memberIds().stream().sorted().map(actorId -> engineeringTeamMember(state, actorId)).reduce((left, right) -> left + "," + right).orElse("");
        boolean fullyEquipped = io.farfrontier.palemirror.frontier.v3.model.EngineeringToolCustody.ready(state, maintenance.team());
        String assembly = maintenance.assembly().map(value -> assembly(state, value.members(), value.purpose(), value.complete())).orElse(",\"assemblyPresent\":false");
        EngineeringAdmissionDiagnostic admission = maintenanceAssemblyAdmission(state, maintenance, fullyEquipped);
        var toolReturn = EngineeringEquipmentProcess.returnReadiness(state, maintenance);
        String toolReturnActor = toolReturn.actorId().map(SubjectId::value).orElse("");
        String toolReturnItem = toolReturn.itemId().map(SubjectId::value).orElse("");
        String toolReturnSlot = toolReturn.targetSlot().map(slot -> Integer.toString(slot.slot())).orElse("");
        return FrontierV3DiagnosticJson.base("route_maintenance", id, checkpoint) + ",\"status\":\"ok\",\"maintenance\":\"" + FrontierV3DiagnosticJson.quote(maintenance.id().value())
                + "\",\"phase\":\"" + maintenance.status() + "\",\"repairCell\":" + FrontierV3DiagnosticJson.position(maintenance.repairCell())
                + ",\"semanticPart\":\"" + maintenance.semanticPart() + "\",\"cargo\":\"" + FrontierV3DiagnosticJson.quote(maintenance.cargoId().map(SubjectId::value).orElse(""))
                + "\",\"cargoPresent\":" + maintenance.cargoId().isPresent() + ",\"teamPresent\":true,\"teamFullyEquipped\":" + fullyEquipped
                + ",\"assemblyAdmission\":\"" + FrontierV3DiagnosticJson.quote(admission.reason()) + "\",\"assemblyAdmissionDetail\":\"" + FrontierV3DiagnosticJson.quote(admission.detail()) + "\",\"teamMembers\":[" + members
                + "],\"pendingMaintenanceIntents\":" + pendingIntents(state, maintenance.id(), true) + ",\"toolReturnRequired\":" + !EngineeringEquipmentProcess.returnedOrLost(state, maintenance)
                + ",\"toolReturnReadiness\":\"" + FrontierV3DiagnosticJson.quote(toolReturn.reason()) + "\",\"toolReturnDepot\":\"" + FrontierV3DiagnosticJson.quote(toolReturn.depotId().value())
                + "\",\"toolReturnActor\":\"" + FrontierV3DiagnosticJson.quote(toolReturnActor) + "\",\"toolReturnItem\":\"" + FrontierV3DiagnosticJson.quote(toolReturnItem)
                + "\",\"toolReturnSlot\":\"" + FrontierV3DiagnosticJson.quote(toolReturnSlot) + "\"" + assembly + "}";
    }

    private static String team(FrontierWorldState state, io.farfrontier.palemirror.frontier.v3.model.EngineeringRecoveryTeam team) {
        String members = team.memberIds().stream().sorted().map(actorId -> engineeringTeamMember(state, actorId)).reduce((left, right) -> left + "," + right).orElse("");
        boolean fullyEquipped = io.farfrontier.palemirror.frontier.v3.model.EngineeringToolCustody.ready(state, team);
        return ",\"teamPresent\":true,\"teamFullyEquipped\":" + fullyEquipped + ",\"teamMembers\":[" + members + "]";
    }

    private static String assembly(FrontierWorldState state, java.util.Map<SubjectId, io.farfrontier.palemirror.frontier.v3.model.EngineeringWorkAssembly.Member> members, Object purpose, boolean complete) {
        String rendered = members.entrySet().stream().sorted(java.util.Map.Entry.comparingByKey()).map(entry -> engineeringAssemblyMember(state, entry.getKey(), entry.getValue())).reduce((left, right) -> left + "," + right).orElse("");
        int cursorTotal = members.values().stream().mapToInt(io.farfrontier.palemirror.frontier.v3.model.EngineeringWorkAssembly.Member::cursor).sum();
        return ",\"assemblyPresent\":true,\"assemblyPurpose\":\"" + purpose + "\",\"assemblyComplete\":" + complete + ",\"assemblyCursorTotal\":" + cursorTotal + ",\"assemblyMembers\":[" + rendered + "]";
    }

    private static long pendingIntents(FrontierWorldState state, SubjectId owner, boolean containsOwner) {
        return state.physicalIntents().values().stream().filter(intent -> intent.subjectIds().contains(owner) == containsOwner).filter(FrontierV3DiagnosticEngineeringJson::pending).count();
    }

    private static boolean pending(io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent intent) {
        return intent.status() == io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus.PREPARED || intent.status() == io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus.RUNNING;
    }

    private static EngineeringAdmissionDiagnostic maintenanceAssemblyAdmission(FrontierWorldState state, RouteMaintenance maintenance, boolean fullyEquipped) {
        if (!maintenance.building()) return new EngineeringAdmissionDiagnostic("NOT_BUILDING", "");
        if (maintenance.assembly().isPresent()) return new EngineeringAdmissionDiagnostic("ASSEMBLY_RETAINED", "");
        if (!fullyEquipped) return new EngineeringAdmissionDiagnostic("WAITING_FOR_TOOL", "");
        boolean predecessorLease = maintenance.team().memberIds().stream().map(state.ambientLeases()::get).anyMatch(lease -> lease != null && lease.status() != io.farfrontier.palemirror.frontier.v3.model.AmbientLeaseStatus.CLOSED);
        if (predecessorLease) return new EngineeringAdmissionDiagnostic("WAITING_FOR_AMBIENT_LEASE", "");
        boolean retainedWorksite = state.sceneLeases().values().stream()
                .filter(io.farfrontier.palemirror.frontier.v3.model.FrontierSceneBehaviors::isEngineeringWorksite)
                .anyMatch(lease -> io.farfrontier.palemirror.frontier.v3.model.FrontierSceneBehaviors.engineeringWorksite(lease)
                        .projectId().equals(maintenance.id())
                        && lease.status() != io.farfrontier.palemirror.frontier.v3.model.SceneLeaseStatus.CLOSED);
        if (retainedWorksite) return new EngineeringAdmissionDiagnostic("WAITING_FOR_WORKSITE", "");
        var readiness = io.farfrontier.palemirror.frontier.v3.model.EngineeringWorksite.admission(state, maintenance);
        return readiness.admissible() ? new EngineeringAdmissionDiagnostic("READY_TO_ASSEMBLE", "") : new EngineeringAdmissionDiagnostic(readiness.reason(), readiness.detail());
    }

    private static String engineeringTeamMember(FrontierWorldState state, SubjectId actorId) {
        var lease = state.ambientLeases().get(actorId);
        ActorLocation location = state.actorLocations().get(actorId);
        return "{\"actor\":\"" + FrontierV3DiagnosticJson.quote(actorId.value()) + "\",\"toolReady\":" + io.farfrontier.palemirror.frontier.v3.model.EngineeringToolCustody.holdsTool(state, actorId)
                + ",\"position\":" + (location == null ? "null" : FrontierV3DiagnosticJson.position(location.body())) + ",\"ambientLease\":\"" + FrontierV3DiagnosticJson.quote(lease == null ? "NONE" : lease.status().name())
                + "\",\"ambientGoal\":\"" + FrontierV3DiagnosticJson.quote(lease == null ? "NONE" : lease.goal().name()) + "\"}";
    }

    private static String engineeringAssemblyMember(FrontierWorldState state, SubjectId actorId, io.farfrontier.palemirror.frontier.v3.model.EngineeringWorkAssembly.Member member) {
        var lease = state.ambientLeases().get(actorId);
        String next = member.arrived() ? "null" : FrontierV3DiagnosticJson.position(member.corridor().get(member.cursor() + 1));
        return "{\"actor\":\"" + FrontierV3DiagnosticJson.quote(actorId.value()) + "\",\"cursor\":" + member.cursor() + ",\"length\":" + member.corridor().size() + ",\"arrived\":" + member.arrived()
                + ",\"next\":" + next + ",\"ambientLease\":\"" + FrontierV3DiagnosticJson.quote(lease == null ? "NONE" : lease.status().name()) + "\",\"ambientGoal\":\"" + FrontierV3DiagnosticJson.quote(lease == null ? "NONE" : lease.goal().name())
                + "\",\"leaseTarget\":" + (lease == null ? "null" : FrontierV3DiagnosticJson.position(lease.goalBody().supportingSurface().support())) + "}";
    }

    private record EngineeringAdmissionDiagnostic(String reason, String detail) { }
}
