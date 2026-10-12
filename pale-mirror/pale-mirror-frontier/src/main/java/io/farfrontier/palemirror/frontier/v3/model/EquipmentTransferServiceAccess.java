package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

/** Atomic transfers publish contact demand; this grants no journey or remote approach reservation. */
final class EquipmentTransferServiceAccess implements ServiceAccessCapability {
    private final ServiceAccessDemand.Kind kind;
    EquipmentTransferServiceAccess(ServiceAccessDemand.Kind kind) { this.kind = Objects.requireNonNull(kind); }
    @Override public ServiceAccessDemand.Kind kind() { return kind; }
    @Override public ServiceAccessDemand.Priority priority() { return ServiceAccessDemand.Priority.WORK; }
    private Stream<PhysicalIntent> intents(FrontierWorldState state) {
        return state.physicalIntents().values().stream()
                .filter(intent -> intent.kind() == PhysicalIntentKind.EQUIPMENT_ISSUE || intent.kind() == PhysicalIntentKind.EQUIPMENT_RETURN)
                .filter(intent -> EquipmentServiceBoundary.capability(intent).serviceKind() == kind)
                .filter(intent -> intent.status() == PhysicalIntentStatus.PREPARED || intent.status() == PhysicalIntentStatus.RUNNING
                        || intent.status() == PhysicalIntentStatus.UNKNOWN_AFTER_RESTART);
    }
    @Override public List<ServiceAccessDemand> demands(FrontierWorldState state, SubjectId pointId) {
        return intents(state).filter(intent -> EquipmentPhysicalAuthority.container(intent).equals(pointId)).flatMap(intent -> {
            var request = EquipmentServiceBoundary.capability(intent).serviceRequest(intent);
            if (!ReferenceContainerCustody.isReferenceContainer(state, pointId) || ReferenceContainerCustody.blocksCanonicalUse(state, pointId)) return Stream.empty();
            var access = ResourceStorageAccess.at(state, pointId);
            if (!ServiceAccessCoordinator.occupies(state, access.accessBoundary(), request.identity().actorId())) return Stream.empty();
            return Stream.of(new ServiceAccessDemand(request.identity(), priority(), ServiceAccessDemand.Presence.OCCUPIED,
                    request.requestedAtTick(), intent.status() == PhysicalIntentStatus.RUNNING || intent.status() == PhysicalIntentStatus.UNKNOWN_AFTER_RESTART));
        }).toList();
    }
    @Override public Set<SubjectId> wakePoints(FrontierWorldState previous, FrontierWorldState next) {
        if (previous.physicalIntents() == next.physicalIntents()) return Set.of();
        var points = new java.util.HashSet<SubjectId>();
        Stream.concat(intents(previous), intents(next)).forEach(intent -> points.add(EquipmentPhysicalAuthority.container(intent)));
        return Set.copyOf(points);
    }
}
