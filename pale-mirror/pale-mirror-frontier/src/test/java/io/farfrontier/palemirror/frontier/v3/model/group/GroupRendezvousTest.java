package io.farfrontier.palemirror.frontier.v3.model.group;

import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class GroupRendezvousTest {
    @Test void optimisticSeparationIncludesAlternativeEntrancesAndNeverChargesVerticalDistance() {
        var left = SurfaceAnchor.at(-20_000, 120, 0);
        var right = SurfaceAnchor.at(20_000, -40, 0);
        long bound = GroupRendezvous.minimumHorizontalSeparation(java.util.List.of(left), java.util.List.of(right));
        assertTrue(bound > 0 && bound < 40_000, "assembly may move toward the other endpoint before travel");
        assertEquals(bound, GroupRendezvous.minimumHorizontalSeparation(
                java.util.List.of(SurfaceAnchor.at(left.x(), -100, left.z())), java.util.List.of(right)));
        assertEquals(0, GroupRendezvous.minimumHorizontalSeparation(java.util.List.of(left, right), java.util.List.of(right)),
                "a closer alternative must keep a short feasible journey available");
        assertThrows(IllegalArgumentException.class, () -> GroupRendezvous.minimumHorizontalSeparation(java.util.List.of(), java.util.List.of(right)));
    }
    @Test void reusedAssemblySelectionCannotSurviveChangedGeometryOrServiceBoundary() {
        var state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:rendezvous-cache"), 41,
                FrontierRulesets.installed("frontier-v3-expedition-candidate-r1")));
        var knowledge = KnownPedestrianRouteKnowledge.forFrontier(state);
        var entrance = state.actorLocations().values().stream().map(ActorLocation::supportingSurface)
                .filter(knowledge::clearStation).findFirst().orElseThrow();
        ImmutablePlanningScope.within(() -> {
            var selected = GroupRendezvous.select(knowledge, entrance, Set.of(entrance), 1);
            assertEquals(selected, GroupRendezvous.select(knowledge, entrance, Set.of(entrance), 1));
            assertNotEquals(selected, GroupRendezvous.select(knowledge.withTaskExclusions(Set.of(selected)),
                    entrance, Set.of(entrance), 1));
            assertNotEquals(selected, GroupRendezvous.select(knowledge, entrance, Set.of(entrance, selected), 1));
            assertEquals(selected, GroupRendezvous.select(knowledge, entrance, Set.of(entrance), 1));
            return true;
        });
    }
}
