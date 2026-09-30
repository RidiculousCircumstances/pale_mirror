package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.ResidentMeal;
import io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A reached waiting pocket must not cache a completed route as the whole meal trip. */
class FrontierV3ResidentMealNavigationTest {
    @Test
    void admittedResidentReplansFromReachedPocketWithoutReconnect() {
        SurfaceAnchor start = SurfaceAnchor.at(150, 64, 4);
        SurfaceAnchor pocket = SurfaceAnchor.at(138, 63, 12);
        SurfaceAnchor service = SurfaceAnchor.at(135, 63, 14);
        List<SurfaceAnchor> approach = List.of(start, pocket);

        assertFalse(FrontierV3ResidentMealNavigation.completedWaitingLegRequiresReplan(
                approach, start, service, true, ResidentMeal.Phase.MOVE));
        assertTrue(FrontierV3ResidentMealNavigation.completedWaitingLegRequiresReplan(
                approach, pocket, service, true, ResidentMeal.Phase.MOVE));
        assertFalse(FrontierV3ResidentMealNavigation.completedWaitingLegRequiresReplan(
                approach, pocket, service, false, ResidentMeal.Phase.MOVE));
        assertFalse(FrontierV3ResidentMealNavigation.completedWaitingLegRequiresReplan(
                List.of(pocket, service), service, service, true, ResidentMeal.Phase.MOVE));
        assertFalse(FrontierV3ResidentMealNavigation.completedWaitingLegRequiresReplan(
                approach, pocket, service, true, ResidentMeal.Phase.RETURN));
    }
}
