package io.farfrontier.palemirror.frontier.v3.model;

/** Pure fixture evidence for an exact absent authored cell; never a native outcome. */
public final class StructureObservationFixtures {
    private StructureObservationFixtures() { }
    public static StructureCellObserved missingCell(FrontierWorldState state, SettlementStructure building,
            BlockPosition position, String cause) {
        var cell = FrontierGrayboxPlan.intactStructureCell(state.bootstrap().terrain(), building, position);
        var air = GeometryObservationFixtures.air();
        return new StructureCellObserved(building.id(), position, cell.semanticPart(), air, false, cause);
    }
}
