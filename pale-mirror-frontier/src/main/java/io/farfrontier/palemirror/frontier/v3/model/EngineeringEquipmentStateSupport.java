package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

/** Exact tool custody rules for a retained route-construction crew. */
public final class EngineeringEquipmentStateSupport {
    private EngineeringEquipmentStateSupport() { }

    public static RouteConstruction project(FrontierWorldState state, SubjectId ownerId) {
        RouteConstruction project = state.routeConstructions().get(ownerId);
        if (project == null || project.team().isEmpty()) throw new IllegalArgumentException("equipment owner is not an active engineering project");
        return project;
    }

    public static void validateIssue(FrontierWorldState state, PhysicalIntent intent) {
        if (intent.subjectIds().size() != 3) throw new IllegalArgumentException("engineering equipment issue needs exact owner, resident and item");
        SubjectId projectId = intent.subjectIds().getFirst(), residentId = intent.subjectIds().get(1), itemId = intent.subjectIds().get(2);
        RouteConstruction project = project(state, projectId);
        EngineeringRecoveryTeam team = project.team().orElseThrow();
        ExactItemStack item = state.inventory().items().get(itemId);
        if (project.status() != RouteConstructionStatus.BUILDING || !intent.causeSubjectId().equals(project.settlementId())
                || !team.memberIds().contains(residentId) || !living(state, residentId) || item == null
                || !(item.custody() instanceof InventoryCustody.ContainerSlot source)
                || !source.containerId().equals(FrontierWorldState.depotId(project.settlementId()))
                || !item.economicOwnerId().equals(project.settlementId()) || !EngineeringToolCustody.isTool(item.itemKind())
                || !activeSurface(state, source.containerId()) || EngineeringToolCustody.holdsTool(state, residentId)
                || hasCompeting(state, intent, PhysicalIntentKind.EQUIPMENT_ISSUE, projectId, residentId, itemId)) {
            throw new IllegalArgumentException("engineering equipment issue lacks an exact crew/depot/tool precondition");
        }
    }

    public static InventoryCustody.ContainerSlot targetSlot(FrontierWorldState state, PhysicalIntent intent) {
        RouteConstruction project = project(state, intent.subjectIds().getFirst());
        var target = intent.targetSlot().orElseThrow(() -> new IllegalArgumentException("engineering equipment return lacks typed target slot"));
        SubjectId depot = FrontierWorldState.depotId(project.settlementId());
        if (!target.containerId().equals(depot)) throw new IllegalArgumentException("engineering equipment return target is not the home depot");
        return new InventoryCustody.ContainerSlot(target.containerId(), target.slot());
    }

    public static void validateReturn(FrontierWorldState state, PhysicalIntent intent) {
        if (intent.subjectIds().size() != 3) throw new IllegalArgumentException("engineering equipment return needs exact owner, resident and item");
        SubjectId projectId = intent.subjectIds().getFirst(), residentId = intent.subjectIds().get(1), itemId = intent.subjectIds().get(2);
        RouteConstruction project = project(state, projectId);
        EngineeringRecoveryTeam team = project.team().orElseThrow();
        ExactItemStack item = state.inventory().items().get(itemId);
        InventoryCustody.ContainerSlot target = targetSlot(state, intent);
        if (project.status() != RouteConstructionStatus.READY || !intent.causeSubjectId().equals(project.settlementId())
                || !team.memberIds().contains(residentId) || !living(state, residentId) || item == null
                || !(item.custody() instanceof InventoryCustody.Actor actor) || !actor.actorId().equals(residentId)
                || !item.economicOwnerId().equals(project.settlementId()) || !EngineeringToolCustody.isTool(item.itemKind())
                || !activeSurface(state, target.containerId()) || target.slot() >= state.inventory().containers().get(target.containerId()).slotCount()
                || state.inventory().itemAt(target.containerId(), target.slot()).isPresent()
                || hasCompeting(state, intent, PhysicalIntentKind.EQUIPMENT_RETURN, projectId, residentId, itemId)) {
            throw new IllegalArgumentException("engineering equipment return lacks an exact crew/depot/tool precondition");
        }
    }

    private static boolean living(FrontierWorldState state, SubjectId residentId) {
        ActorLocation location = state.actorLocations().get(residentId);
        return location != null && location.condition().status() == ActorLifeStatus.ALIVE;
    }

    private static boolean activeSurface(FrontierWorldState state, SubjectId containerId) {
        ContainerSurface surface = state.inventory().surfaces().get(containerId);
        return surface != null && surface.status() == ContainerSurfaceStatus.ACTIVE;
    }

    private static boolean hasCompeting(FrontierWorldState state, PhysicalIntent candidate, PhysicalIntentKind kind,
                                        SubjectId projectId, SubjectId residentId, SubjectId itemId) {
        return state.physicalIntents().values().stream().anyMatch(existing -> !existing.id().equals(candidate.id())
                && existing.kind() == kind && existing.status() != io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus.CONFIRMED
                && existing.subjectIds().equals(java.util.List.of(projectId, residentId, itemId)));
    }
}
