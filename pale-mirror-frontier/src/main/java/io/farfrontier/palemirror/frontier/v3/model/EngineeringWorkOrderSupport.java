package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

/** Resolves one engineering owner without allowing callers to invent an owner class. */
public final class EngineeringWorkOrderSupport {
    private EngineeringWorkOrderSupport() { }

    public static EngineeringWorkOrder require(FrontierWorldState state, SubjectId id) {
        RouteConstruction construction = state.routeConstructions().get(id);
        RouteMaintenance maintenance = state.routeMaintenances().get(id);
        if (construction != null && maintenance != null) throw new IllegalArgumentException("engineering owner identity is ambiguous");
        if (construction != null) return construction;
        if (maintenance != null) return maintenance;
        throw new IllegalArgumentException("equipment owner is not an active engineering work order");
    }
}
