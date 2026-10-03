package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

/** Typed event construction at admission/observation; codecs never discover missing authority. */
public final class EngineeringExecutionEvents {
    private EngineeringExecutionEvents() { }
    public static RouteConstructionStarted constructionStarted(FrontierWorldState state, RouteConstruction owner) {
        return new RouteConstructionStarted(owner, EngineeringExecutionAuthority.admission(state, owner));
    }
    public static RouteMaintenanceStarted maintenanceStarted(FrontierWorldState state, RouteMaintenance owner) {
        return new RouteMaintenanceStarted(owner, EngineeringExecutionAuthority.admission(state, owner).orElseThrow());
    }
    public static RouteConstructionAssemblyStarted constructionAssemblyStarted(FrontierWorldState state, SubjectId id, EngineeringWorkAssembly assembly) {
        var owner = construction(state, id);
        return new RouteConstructionAssemblyStarted(id, assembly, EngineeringExecutionAuthority.assemblyCurrent(state, owner),
                EngineeringExecutionAuthority.workAdmission(state, owner, assembly));
    }
    public static RouteConstructionAssemblyAdvanced constructionAssemblyAdvanced(FrontierWorldState state, SubjectId id, EngineeringWorkAssembly assembly) {
        var owner = construction(state, id);
        return new RouteConstructionAssemblyAdvanced(id, assembly, EngineeringExecutionAuthority.assemblyCurrent(state, owner),
                EngineeringExecutionAuthority.workAdmission(state, owner, assembly));
    }
    public static RouteMaintenanceAssemblyStarted maintenanceAssemblyStarted(FrontierWorldState state, SubjectId id, EngineeringWorkAssembly assembly) {
        var owner = maintenance(state, id);
        return new RouteMaintenanceAssemblyStarted(id, assembly, EngineeringExecutionAuthority.assemblyCurrent(state, owner),
                EngineeringExecutionAuthority.workAdmission(state, owner, assembly));
    }
    public static RouteMaintenanceAssemblyAdvanced maintenanceAssemblyAdvanced(FrontierWorldState state, SubjectId id, EngineeringWorkAssembly assembly) {
        var owner = maintenance(state, id);
        return new RouteMaintenanceAssemblyAdvanced(id, assembly, EngineeringExecutionAuthority.assemblyCurrent(state, owner),
                EngineeringExecutionAuthority.workAdmission(state, owner, assembly));
    }
    public static RouteTopologyCutover cutover(FrontierWorldState state, SubjectId id) {
        return new RouteTopologyCutover(id, EngineeringExecutionAuthority.terminalDeclaration(state, construction(state, id)));
    }
    public static RouteMaintenanceClosed maintenanceClosed(FrontierWorldState state, SubjectId id) {
        return new RouteMaintenanceClosed(id, EngineeringExecutionAuthority.current(state, maintenance(state, id)));
    }
    private static RouteConstruction construction(FrontierWorldState state, SubjectId id) {
        var owner = state.routeConstructions().get(id);
        if (owner == null) throw new IllegalArgumentException("construction event has no exact construction owner");
        return owner;
    }
    private static RouteMaintenance maintenance(FrontierWorldState state, SubjectId id) {
        var owner = state.routeMaintenances().get(id);
        if (owner == null) throw new IllegalArgumentException("maintenance event has no exact maintenance owner");
        return owner;
    }
}
