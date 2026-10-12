package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

/** Exact tool custody rules for a retained route-construction crew. */
public final class EngineeringEquipmentStateSupport {
    private EngineeringEquipmentStateSupport() { }

    /** Exact retained crew and stock, independent of a new visit's stage or current station. */
    public static void validateCommitment(FrontierWorldState state, PhysicalIntent intent) {
        var project = project(state, intent.roles().require(io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentSubjectRole.ENGINEERING_WORK_ORDER));
        var actor = intent.roles().require(io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentSubjectRole.ENGINEERING_WORKER);
        var item = state.inventory().items().get(intent.roles().require(io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentSubjectRole.EQUIPMENT));
        if (!intent.causeSubjectId().equals(project.settlementId())
                || !project.engineeringTeam().orElseThrow().memberIds().contains(actor) || item == null
                || !item.economicOwnerId().equals(project.settlementId()) || !EngineeringToolCustody.isTool(item.itemKind()))
            throw new IllegalArgumentException("engineering equipment lost its exact retained crew/tool commitment");
    }

    public static EngineeringWorkOrder project(FrontierWorldState state, SubjectId ownerId) {
        EngineeringWorkOrder project = EngineeringWorkOrderSupport.require(state, ownerId);
        if (project.engineeringTeam().isEmpty()) throw new IllegalArgumentException("equipment owner has no exact engineering team");
        return project;
    }

    public static void validateIssue(FrontierWorldState state, PhysicalIntent intent) {
        SubjectId projectId = intent.roles().require(io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentSubjectRole.ENGINEERING_WORK_ORDER), residentId =
                intent.roles().require(io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentSubjectRole.ENGINEERING_WORKER), itemId = intent.roles().require(io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentSubjectRole.EQUIPMENT);
        EngineeringWorkOrder project = project(state, projectId);
        EngineeringRecoveryTeam team = project.engineeringTeam().orElseThrow();
        ExactItemStack item = state.inventory().items().get(itemId);
        if (!project.building()
                || !intent.causeSubjectId().equals(project.settlementId())
                || !team.memberIds().contains(residentId) || item == null
                || !item.economicOwnerId().equals(project.settlementId()) || !EngineeringToolCustody.isTool(item.itemKind())
                || EngineeringToolCustody.holdsTool(state, residentId) || issueStation(state, intent).isEmpty()) {
            throw new IllegalArgumentException("engineering equipment issue lacks an exact crew/depot/tool precondition");
        }
    }

    public static InventoryCustody.ContainerSlot targetSlot(FrontierWorldState state, PhysicalIntent intent) {
        return EquipmentServiceBoundary.targetSlot(intent);
    }

    public static void validateReturn(FrontierWorldState state, PhysicalIntent intent) {
        SubjectId projectId = intent.roles().require(io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentSubjectRole.ENGINEERING_WORK_ORDER), residentId =
                intent.roles().require(io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentSubjectRole.ENGINEERING_WORKER), itemId = intent.roles().require(io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentSubjectRole.EQUIPMENT);
        EngineeringWorkOrder project = project(state, projectId);
        EngineeringRecoveryTeam team = project.engineeringTeam().orElseThrow();
        ExactItemStack item = state.inventory().items().get(itemId);
        if (!project.readyForToolReturn()
                || !intent.causeSubjectId().equals(project.settlementId())
                || !team.memberIds().contains(residentId) || item == null
                || !item.economicOwnerId().equals(project.settlementId()) || !EngineeringToolCustody.isTool(item.itemKind())
                || returnStation(state, intent).isEmpty()) {
            throw new IllegalArgumentException("engineering equipment return lacks an exact crew/depot/tool precondition");
        }
    }

    public static java.util.Optional<SurfaceAnchor> issueStation(FrontierWorldState state, PhysicalIntent intent) {
        return station(state, intent, EngineeringJourneyPurpose.MUSTER_DEPOT);
    }
    public static java.util.Optional<SurfaceAnchor> returnStation(FrontierWorldState state, PhysicalIntent intent) {
        return station(state, intent, EngineeringJourneyPurpose.RETURN_DEPOT);
    }
    private static java.util.Optional<SurfaceAnchor> station(FrontierWorldState state, PhysicalIntent intent, EngineeringJourneyPurpose purpose) {
        var project = project(state, intent.roles().require(io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentSubjectRole.ENGINEERING_WORK_ORDER));
        if (!EngineeringDepotService.atStations(state, project, purpose)) return java.util.Optional.empty();
        var visit = project.assembly().orElseThrow().equipmentVisit().orElseThrow();
        if (visit.clearing() || visit.operation() != intent.kind()
                || intent.equipmentServiceRequest().orElseThrow().requestedAtTick() != visit.requestedAtTick()
                || !intent.id().equals(visit.transferId(project.id(), EngineeringExecutionAuthority.assemblyCurrent(state, project)
                    .requireMember(visit.actorId()).generation()))
                || !visit.actorId().equals(intent.roles().require(io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentSubjectRole.ENGINEERING_WORKER))
                || !visit.itemId().equals(intent.roles().require(io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentSubjectRole.EQUIPMENT))
                || !visit.access().containerId().equals(EquipmentPhysicalAuthority.container(intent))
                || intent.kind() == PhysicalIntentKind.EQUIPMENT_RETURN && !visit.returnSlot().equals(intent.targetSlot()))
            return java.util.Optional.empty();
        var member = project.assembly().orElseThrow().members().get(intent.roles().require(
                io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentSubjectRole.ENGINEERING_WORKER));
        if (member == null || !member.arrived()) return java.util.Optional.empty();
        var station = new SurfaceAnchor(member.currentPosition());
        return visit.access().stations().contains(station)
                ? java.util.Optional.of(station) : java.util.Optional.empty();
    }

}
