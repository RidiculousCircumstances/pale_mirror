package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRouteReceipt;
import java.util.*;

/** Shared function-requirement assessment. Integrity percentage never substitutes for physical access.
 * Content/work owners declare requirements; navigation alone validates actual access receipts. */
public final class FacilityFunctionAvailability {
    public enum Status { ELIGIBLE, BLOCKED, WAITING_GEOMETRY }
    public record Requirements(SubjectId facility, Set<BlockPosition> retainedCells, Set<SurfaceAnchor> workContacts) {
        public Requirements {
            Objects.requireNonNull(facility); retainedCells = Set.copyOf(retainedCells); workContacts = Set.copyOf(workContacts);
            if (retainedCells.size() + workContacts.size() > 512) throw new IllegalArgumentException("unbounded function requirements");
        }
    }
    public record Assessment(Status status, Set<BlockPosition> missingCells) {
        public Assessment { Objects.requireNonNull(status); missingCells = Set.copyOf(missingCells); }
        public boolean mayPlan() { return status != Status.BLOCKED; }
    }
    private FacilityFunctionAvailability() { }
    public static Assessment assess(FrontierWorldState state, Requirements requirements, KnownPedestrianRouteKnowledge knowledge) {
        var facility = FrontierWorldStateSupport.structureById(state.bootstrap(), requirements.facility());
        var damage = state.structureDamage().getOrDefault(facility.id(), StructureDamage.empty(facility.id()));
        var missing = new HashSet<BlockPosition>();
        for (var cell : requirements.retainedCells()) {
            if (FrontierGrayboxPlan.intactStructureCell(state.bootstrap().terrain(), facility, cell) == null)
                throw new IllegalArgumentException("function requires a foreign building cell");
            if (damage.cells().containsKey(cell)) missing.add(cell);
        }
        if (!missing.isEmpty()) return new Assessment(Status.BLOCKED, missing);
        boolean unknown = false;
        for (var contact : requirements.workContacts()) {
            if (!facility.placement().volume(facility.definition().parcel()).contains(contact.support()))
                throw new IllegalArgumentException("function contact escaped its declared facility");
            if (!knowledge.geometry().clearanceKnown(contact)) unknown = true;
            else if (!knowledge.clearStation(contact)) return new Assessment(Status.BLOCKED, Set.of(contact.support()));
        }
        return new Assessment(unknown ? Status.WAITING_GEOMETRY : Status.ELIGIBLE, Set.of());
    }
    /** An exact access proof may use any physical permitted route, including an alternate entrance. */
    public static void requireAccess(Requirements requirements, KnownPedestrianRouteKnowledge knowledge, PedestrianRouteReceipt access) {
        if (!requirements.workContacts().contains(access.route().getLast()))
            throw new IllegalArgumentException("function access ends at a foreign work contact");
        knowledge.requireRoute(access.route());
    }
    /** Advisory structural eligibility is route-free; machine ownership is checked separately by its owner. */
    public static boolean mayUse(FrontierWorldState state, SubjectId facility) {
        var building = FrontierWorldStateSupport.structureById(state.bootstrap(), facility);
        if (building.definition().representation() != io.farfrontier.palemirror.frontier.v3.model.buildings.BuildingDefinition.Representation.AUTHORED)
            return state.structureConditions().get(facility) != StructureCondition.DESTROYED;
        if (building.definition().workplaces().isEmpty()) return true;
        return building.definition().workplaces().values().stream().anyMatch(work ->
                supportMayExist(state, facility, building.placement().world(work.worker())));
    }
    /** Cheap advisory support evidence, not path, station ownership or mutation permission. */
    public static boolean supportMayExist(FrontierWorldState state, SubjectId facility, BlockPosition point) {
        var damage = state.structureDamage().get(facility);
        if (damage == null || !damage.cells().containsKey(point)) return true;
        var actual = state.navigationGeometry().cells().get(point);
        return actual == null || actual.cell().isEmpty()
                || !actual.cell().orElseThrow().shape().equals(io.farfrontier.palemirror.frontier.v3.model.geometry.NavigationShape.EMPTY);
    }
}
