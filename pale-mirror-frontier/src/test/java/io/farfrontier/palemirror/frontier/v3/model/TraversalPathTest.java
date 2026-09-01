package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TraversalPathTest {
    @Test void declaredOneBlockGradePreservesDifferentSupportAndFeetValues() {
        SurfaceAnchor street = SurfaceAnchor.at(10, 63, 10);
        SurfaceAnchor raisedFoundation = SurfaceAnchor.at(11, 64, 10);

        TraversalPath path = new TraversalPath(List.of(street, raisedFoundation));

        assertEquals(street, path.first());
        assertEquals(64, street.standingBody().y());
        assertEquals(65, raisedFoundation.standingBody().y());
    }

    @Test void pathRejectsAHiddenJumpOrDiagonalAsAWalkableGrade() {
        SurfaceAnchor street = SurfaceAnchor.at(10, 63, 10);
        assertThrows(IllegalArgumentException.class, () -> new TraversalPath(List.of(street, SurfaceAnchor.at(11, 65, 10))));
        assertThrows(IllegalArgumentException.class, () -> new TraversalPath(List.of(street, SurfaceAnchor.at(11, 63, 11))));
    }

    @Test void grayboxInfirmaryDeclaresItsNaturalStreetApproachBelowRaisedFoundation() {
        SettlementStructure infirmary = new SettlementStructure(new SubjectId("structure:infirmary"), new SubjectId("settlement:test"),
                StructureKind.INFIRMARY, new BlockPosition(20, 64, 20));

        SettlementInfirmaryTreatmentPort port = SettlementInfirmaryTreatmentPort.forInfirmary(infirmary);

        assertEquals(FacilityFacing.WEST, port.facing());
        assertEquals(63, port.exteriorApproachSurface().y());
        assertEquals(64, port.exteriorApproachSurface().standingBody().y());
        assertEquals(64, port.ownedAccessSurfaces().getFirst().y());
        assertEquals(65, port.ownedAccessSurfaces().getFirst().standingBody().y());
    }

    @Test void observedArrivalAdvancesFromRaisedFoundationInsteadOfReturningToStreetApproach() {
        List<BodyPosition> route = List.of(new BodyPosition(0, 64, 0), new BodyPosition(1, 65, 0),
                new BodyPosition(1, 65, 1), new BodyPosition(2, 65, 1));

        assertEquals(2, ObservedTraversalCursor.nextTargetIndex(route.get(1), route, 0));
        assertEquals(0, ObservedTraversalCursor.nextTargetIndex(new BodyPosition(20, 70, 20), route, 0));
        assertThrows(IllegalArgumentException.class, () -> ObservedTraversalCursor.nextTargetIndex(new BodyPosition(0, 0, 0), List.of(), 0));
    }
}
