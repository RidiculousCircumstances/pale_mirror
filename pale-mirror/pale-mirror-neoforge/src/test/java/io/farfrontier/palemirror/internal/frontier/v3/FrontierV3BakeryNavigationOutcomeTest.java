package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.BakeryWorkBlock;
import io.farfrontier.palemirror.frontier.v3.model.BakeryWorkState;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static io.farfrontier.palemirror.internal.frontier.v3.FrontierV3BakeryWorkSceneExecutor.NavigationBlockChange.*;

class FrontierV3BakeryNavigationOutcomeTest {
    @Test void blockedExitCannotChangeConfirmedDeliveryButActiveWorkStillReportsRouteFailure() {
        var stalled = blocked(FrontierV3GoalNavigation.BlockReason.PATH_STALLED);
        assertEquals(NONE, FrontierV3BakeryWorkSceneExecutor.navigationBlockChange(
                BakeryWorkState.Phase.DELIVERED, Optional.empty(), stalled));
        for (var phase : BakeryWorkState.Phase.values()) {
            if (phase == BakeryWorkState.Phase.DELIVERED) continue;
            assertEquals(BLOCK, FrontierV3BakeryWorkSceneExecutor.navigationBlockChange(phase, Optional.empty(), stalled));
            assertEquals(NONE, FrontierV3BakeryWorkSceneExecutor.navigationBlockChange(phase, Optional.empty(),
                    blocked(FrontierV3GoalNavigation.BlockReason.TARGET_CHUNK_UNLOADED)));
        }
        var routeBlock = new BakeryWorkBlock(BakeryWorkBlock.Reason.ROUTE_BLOCKED,
                new SubjectId("structure:test-bakery"), -1, "minecraft:air", 0);
        var progressing = new FrontierV3GoalNavigation.Result(FrontierV3GoalNavigation.Status.IN_PROGRESS,
                "moving", Optional.empty(), Optional.empty());
        assertEquals(CLEAR, FrontierV3BakeryWorkSceneExecutor.navigationBlockChange(
                BakeryWorkState.Phase.DEPOT_DELIVERY, Optional.of(routeBlock), progressing));
        assertEquals(NONE, FrontierV3BakeryWorkSceneExecutor.navigationBlockChange(
                BakeryWorkState.Phase.DELIVERED, Optional.empty(), progressing));
    }

    private static FrontierV3GoalNavigation.Result blocked(FrontierV3GoalNavigation.BlockReason reason) {
        return new FrontierV3GoalNavigation.Result(FrontierV3GoalNavigation.Status.BLOCKED,
                reason.name(), Optional.of(reason), Optional.empty());
    }
}
