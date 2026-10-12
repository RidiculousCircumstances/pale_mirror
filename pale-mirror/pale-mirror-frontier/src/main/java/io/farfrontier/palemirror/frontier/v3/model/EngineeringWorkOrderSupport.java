package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

/** Resolves one engineering owner without allowing callers to invent an owner class. */
public final class EngineeringWorkOrderSupport {
    private EngineeringWorkOrderSupport() { }

    public static EngineeringWorkOrder require(FrontierWorldState state, SubjectId id) {
        return find(state, id).orElseThrow(() -> new IllegalArgumentException("equipment owner is not a registered engineering work order"));
    }
    public static java.util.Optional<EngineeringWorkOrder> find(FrontierWorldState state, SubjectId declaredWorkOrderId) {
        return java.util.Optional.ofNullable(registry(state.routeConstructions(), state.routeMaintenances()).get(declaredWorkOrderId));
    }
    /** Registers already nominally declared owners; lookup never guesses a missing owner type. */
    static java.util.Map<SubjectId, EngineeringWorkOrder> registry(java.util.Map<SubjectId, RouteConstruction> constructions,
                                                               java.util.Map<SubjectId, RouteMaintenance> maintenances) {
        var owners = new java.util.LinkedHashMap<SubjectId, EngineeringWorkOrder>();
        constructions.forEach((id, owner) -> register(owners, id, owner));
        maintenances.forEach((id, owner) -> register(owners, id, owner));
        return java.util.Map.copyOf(owners);
    }
    private static void register(java.util.Map<SubjectId, EngineeringWorkOrder> owners, SubjectId id, EngineeringWorkOrder owner) {
        if (!id.equals(owner.id()) || owners.putIfAbsent(id, owner) != null)
            throw new IllegalArgumentException("engineering owner registration has an ambiguous or foreign identity");
    }
}
