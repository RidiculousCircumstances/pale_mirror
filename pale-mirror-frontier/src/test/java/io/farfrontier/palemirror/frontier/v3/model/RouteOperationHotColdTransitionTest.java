package io.farfrontier.palemirror.frontier.v3.model;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** State-machine checks for the transient COLD segment boundary between two HOT scenes. */
class RouteOperationHotColdTransitionTest {
    @Test
    void arrivedColdSegmentCannotBeClaimedByHotBeforeItsAtomicNextSegmentTransition() {
        RouteOperation operation = FrontierV3OperationFixture.routeSceneReturnOperation();
        assertTrue(operation.hasInProgressTravel(), "the unfinished initial corridor is HOT-eligible");

        OperationTravel initial = operation.activeTravel().orElseThrow();
        OperationTravel travel = initial;
        while (!travel.arrived()) travel = FrontierV3OperationFixture.advanceCold(travel);
        RouteOperation awaitingNextSegment = operation.withTravel(travel);

        assertTrue(travel.arrived());
        assertFalse(awaitingNextSegment.hasInProgressTravel(),
                "COLD owns an arrived segment until it atomically opens the next corridor");
        assertEquals(Set.copyOf(operation.participantIds()), travel.formation().keySet(),
                "the hand-off retains the exact formation identities");
        BlockPosition initialCarrier = initial.formation().get(operation.cargoCarrierId());
        BlockPosition arrivedCarrier = travel.formation().get(operation.cargoCarrierId());
        assertEquals(initial.cargoAnchor().x() - initialCarrier.x(), travel.cargoAnchor().x() - arrivedCarrier.x());
        assertEquals(initial.cargoAnchor().z() - initialCarrier.z(), travel.cargoAnchor().z() - arrivedCarrier.z(),
                "the exact cargo retains its carrier-relative anchor through COLD movement");
    }

    @Test
    void everyBoundedColdAdvanceTranslatesAllExactMembersAndCargoTogether() {
        RouteOperation operation = FrontierV3OperationFixture.routeSceneReturnOperation();
        OperationTravel travel = operation.activeTravel().orElseThrow();
        while (!travel.arrived()) {
            OperationTravel next = FrontierV3OperationFixture.advanceCold(travel);
            int deltaX = next.currentPosition().x() - travel.currentPosition().x();
            int deltaZ = next.currentPosition().z() - travel.currentPosition().z();
            assertTrue(next.cursor() > travel.cursor() && next.cursor() <= travel.nextColdCursor());
            for (var member : travel.formation().entrySet()) {
                assertEquals(member.getValue().offset(deltaX, 0, deltaZ), next.formation().get(member.getKey()));
            }
            assertEquals(travel.cargoAnchor().offset(deltaX, 0, deltaZ), next.cargoAnchor());
            travel = next;
        }
    }
}
