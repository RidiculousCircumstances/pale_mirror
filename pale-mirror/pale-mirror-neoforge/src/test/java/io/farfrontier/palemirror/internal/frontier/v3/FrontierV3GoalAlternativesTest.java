package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FrontierV3GoalAlternativesTest {
    @Test void ninthExitKeepsItsPathAcrossRefreshesAfterTheFirstBatchWasRejected() {
        var candidates = IntStream.range(0, 9).mapToObj(x -> SurfaceAnchor.at(x, 63, 0)).toList();
        var pass = new FrontierV3GoalAlternatives(candidates);
        pass = pass.reject(pass.batch());
        assertEquals(List.of(candidates.getLast()), pass.batch());
        var reordered = new ArrayList<>(candidates); java.util.Collections.reverse(reordered);
        assertTrue(pass.matches(reordered), "walking must not reset the pass merely by changing nearest-first ranking");
        assertSame(pass, pass.nextPassIfExhausted());
        assertEquals(List.of(candidates.getLast()), pass.batch(), "refresh must retain the later accepted leg");
        assertFalse(pass.matches(candidates.subList(0, 8)), "removed targets invalidate their selection");
        assertEquals(candidates.subList(0, 8), pass.reject(pass.batch()).nextPassIfExhausted().batch(),
                "a completed failed pass may retry after the environment changes");
    }

    @Test void stalledAcceptedTargetDoesNotDiscardTheOtherLegalExits() {
        var first = SurfaceAnchor.at(0, 63, 0); var alternative = SurfaceAnchor.at(1, 63, 0);
        var pass = new FrontierV3GoalAlternatives(List.of(first, alternative)).reject(List.of(first));
        assertEquals(List.of(alternative), pass.batch());
        assertTrue(FrontierV3GoalAlternatives.reconsider(blocked(FrontierV3GoalNavigation.BlockReason.PATH_STALLED)));
        assertTrue(FrontierV3GoalAlternatives.reconsider(blocked(FrontierV3GoalNavigation.BlockReason.PATH_UNAVAILABLE)));
        assertFalse(FrontierV3GoalAlternatives.reconsider(blocked(FrontierV3GoalNavigation.BlockReason.TRAFFIC_BLOCKED)),
                "a pedestrian must still reach the courtesy protocol");
        assertFalse(FrontierV3GoalAlternatives.reconsider(blocked(FrontierV3GoalNavigation.BlockReason.SEARCH_BUDGET_EXHAUSTED)),
                "unfinished search is not evidence of a rejected destination");
    }
    private static FrontierV3GoalNavigation.Result blocked(FrontierV3GoalNavigation.BlockReason reason) {
        return new FrontierV3GoalNavigation.Result(FrontierV3GoalNavigation.Status.BLOCKED, reason.name(), Optional.of(reason), Optional.empty());
    }
}
