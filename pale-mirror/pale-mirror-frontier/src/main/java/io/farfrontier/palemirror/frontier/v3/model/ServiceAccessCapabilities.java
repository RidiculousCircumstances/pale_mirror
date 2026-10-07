package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Closed composition root; the coordinator never rediscovers a job's family from its state. */
final class ServiceAccessCapabilities {
    private static final ServiceAccessCapabilities CURRENT = new ServiceAccessCapabilities(List.of(
            new ResidentMealServiceAccess(), new ProductionServiceAccess(), new HarvestServiceAccess(), new ShipmentServiceAccess(),
            new ExpeditionSupplyServiceAccess()));
    private final Map<ServiceAccessDemand.Kind, ServiceAccessCapability> capabilities;
    private final ThreadLocal<PointIndex> pointIndexes = new ThreadLocal<>();
    private static final int MAX_RETAINED_POINTS = 128;

    /** Derived only; one immutable state and its queried points per owner thread, never history. */
    private record PointIndex(FrontierWorldState state, Map<SubjectId, List<ServiceAccessDemand>> points) { }

    ServiceAccessCapabilities(List<? extends ServiceAccessCapability> registrations) {
        var values = new EnumMap<ServiceAccessDemand.Kind, ServiceAccessCapability>(ServiceAccessDemand.Kind.class);
        for (var capability : registrations) {
            Objects.requireNonNull(capability, "service capability");
            Objects.requireNonNull(capability.priority(), "declared service priority");
            if (values.putIfAbsent(Objects.requireNonNull(capability.kind(), "declared service kind"), capability) != null)
                throw new IllegalArgumentException("duplicate service access capability");
        }
        if (!values.keySet().equals(Set.of(ServiceAccessDemand.Kind.values())))
            throw new IllegalArgumentException("missing service access capability");
        capabilities = Map.copyOf(values);
    }

    static List<ServiceAccessDemand> current(FrontierWorldState state, SubjectId pointId) {
        return CURRENT.indexed(state, pointId);
    }

    List<ServiceAccessDemand> indexed(FrontierWorldState state, SubjectId pointId) {
        PointIndex index = pointIndexes.get();
        if (index == null || index.state() != state) {
            index = new PointIndex(Objects.requireNonNull(state, "service state"), new java.util.HashMap<>());
            pointIndexes.set(index);
        }
        Objects.requireNonNull(pointId, "service point");
        if (!index.points().containsKey(pointId) && index.points().size() >= MAX_RETAINED_POINTS) index.points().clear();
        return index.points().computeIfAbsent(pointId, ignored -> evaluate(state, pointId));
    }

    List<ServiceAccessDemand> evaluate(FrontierWorldState state, SubjectId pointId) {
        var result = new ArrayList<ServiceAccessDemand>();
        var identities = new HashSet<ServiceAccessDemand.Identity>();
        for (var kind : ServiceAccessDemand.Kind.values()) {
            for (var demand : capabilities.get(kind).demands(state, pointId)) {
                if (demand.identity().kind() != kind || demand.priority() != capabilities.get(kind).priority()
                        || !demand.identity().pointId().equals(pointId)
                        || !identities.add(demand.identity()))
                    throw new IllegalArgumentException("foreign or duplicate service access declaration");
                result.add(demand);
            }
        }
        return List.copyOf(result);
    }

    static ServiceAccessDemand.Priority priority(ServiceAccessDemand.Identity identity) {
        return CURRENT.capabilities.get(identity.kind()).priority();
    }
}
