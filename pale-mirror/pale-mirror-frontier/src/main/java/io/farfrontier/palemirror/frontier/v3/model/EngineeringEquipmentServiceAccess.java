package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/** Engineering supplies one exact visitor; the shared coordinator alone arbitrates the access turn. */
public final class EngineeringEquipmentServiceAccess implements ServiceAccessCapability {
    @Override public ServiceAccessDemand.Kind kind() { return ServiceAccessDemand.Kind.ENGINEERING_EQUIPMENT; }
    @Override public ServiceAccessDemand.Priority priority() { return ServiceAccessDemand.Priority.WORK; }
    public static ServiceAccessDemand.Identity identity(EngineeringWorkOrder owner, EngineeringEquipmentVisit visit) {
        return new ServiceAccessDemand.Identity(ServiceAccessDemand.Kind.ENGINEERING_EQUIPMENT, owner.id(), visit.actorId(), visit.access().containerId());
    }
    private static Stream<EngineeringWorkOrder> owners(FrontierWorldState state) {
        return Stream.concat(state.routeConstructions().values().stream(), state.routeMaintenances().values().stream());
    }
    @Override public List<ServiceAccessDemand> demands(FrontierWorldState state, SubjectId point) {
        return owners(state).filter(owner -> owner.building() || owner.readyForToolReturn())
                .filter(owner -> owner.assembly().flatMap(EngineeringWorkAssembly::equipmentVisit).isPresent())
                .filter(owner -> owner.assembly().orElseThrow().equipmentVisit().orElseThrow().access().containerId().equals(point))
                .flatMap(owner -> {
                    var visit = owner.assembly().orElseThrow().equipmentVisit().orElseThrow();
                    boolean occupied = ServiceAccessCoordinator.occupies(state, visit.access().accessBoundary(), visit.actorId());
                    if (visit.clearing() && !occupied) return Stream.empty();
                    return Stream.of(new ServiceAccessDemand(identity(owner, visit), priority(), occupied
                            ? ServiceAccessDemand.Presence.OCCUPIED : ServiceAccessDemand.Presence.APPROACH, visit.requestedAtTick(),
                            state.physicalIntents().values().stream().anyMatch(intent ->
                                intent.lifecycleOwner() == io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentLifecycleOwner.ENGINEERING_WORKSITE
                                && (intent.status() == io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus.RUNNING
                                    || intent.status() == io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus.UNKNOWN_AFTER_RESTART)
                                && (intent.kind() == io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind.EQUIPMENT_ISSUE
                                    || intent.kind() == io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind.EQUIPMENT_RETURN)
                                && intent.roles().require(io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentSubjectRole.ENGINEERING_WORK_ORDER).equals(owner.id())
                                && intent.roles().require(io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentSubjectRole.ENGINEERING_WORKER).equals(visit.actorId()))));
                }).toList();
    }
    @Override public Set<SubjectId> wakePoints(FrontierWorldState previous, FrontierWorldState next) {
        var points = new java.util.HashSet<SubjectId>();
        if (previous.routeConstructions() != next.routeConstructions() || previous.routeMaintenances() != next.routeMaintenances()
                || previous.physicalIntents() != next.physicalIntents()) {
            owners(previous).flatMap(owner -> owner.assembly().flatMap(EngineeringWorkAssembly::equipmentVisit).stream()).forEach(visit -> points.add(visit.access().containerId()));
            owners(next).flatMap(owner -> owner.assembly().flatMap(EngineeringWorkAssembly::equipmentVisit).stream()).forEach(visit -> points.add(visit.access().containerId()));
        }
        return Set.copyOf(points);
    }
    public static boolean mayAdvance(FrontierWorldState state, EngineeringWorkOrder owner, SubjectId actor, SurfaceAnchor destination) {
        var visit = owner.assembly().flatMap(EngineeringWorkAssembly::equipmentVisit).orElse(null);
        return visit == null || !visit.actorId().equals(actor) || visit.clearing()
                || ServiceAccessCoordinator.mayAdvance(state, identity(owner, visit), destination);
    }
}
