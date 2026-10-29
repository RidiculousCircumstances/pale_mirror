package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.*;

/** Current declared container positions, independent of stock, permission and task admission. */
final class ContainerPositionFacts {
    private static final ImmutableReferenceIndex.Source<FrontierWorldState, SubjectId, ActorLocation> ACTORS =
            new ImmutableReferenceIndex.Source<>(FrontierWorldState::actorLocations,
                    (before, after) -> before == after || before != null && after != null
                            && before.supportingSurface().equals(after.supportingSurface()));
    private static final ImmutableReadProjection<FrontierWorldState, SubjectId, ContainerSurface, BlockPosition> POSITIONS =
            new ImmutableReadProjection<>(Integer.MAX_VALUE, 1, List.of(ACTORS),
                    (state, surface) -> surface.location() instanceof ContainerLocation.Mobile mobile
                            ? List.of(ACTORS.at(mobile.actorId())) : List.of(),
                    (state, surface) -> surface.position(state));
    private ContainerPositionFacts() { }
    static Map<SubjectId, BlockPosition> all(FrontierWorldState state) {
        return POSITIONS.project(state.bootstrap(), state.inventory().surfaces(), state);
    }
}
