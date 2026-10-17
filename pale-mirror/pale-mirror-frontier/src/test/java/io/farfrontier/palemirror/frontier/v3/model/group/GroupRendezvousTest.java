package io.farfrontier.palemirror.frontier.v3.model.group;

import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class GroupRendezvousTest {
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
