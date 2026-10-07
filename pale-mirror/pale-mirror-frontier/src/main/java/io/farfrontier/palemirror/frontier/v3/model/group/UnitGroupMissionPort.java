package io.farfrontier.palemirror.frontier.v3.model.group;

import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.group.UnitGroup;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionId;
import java.util.Optional;

/** Mission-owned policy supplied to generic coordination; no concrete jobs inside the coordinator. */
public interface UnitGroupMissionPort {
    boolean permitsHomeFood(io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState state, UnitGroup group);
    UnitGroup.MissionKind kind();
    void validate(FrontierWorldState state, UnitGroup group);
    KnownPedestrianRouteKnowledge knowledge(FrontierWorldState state, UnitGroup group);
    GroupTravelPolicy travelPolicy(FrontierWorldState state, UnitGroup group);
    SurfaceAnchor destination(FrontierWorldState state, UnitGroup group, long ordinal);
    Optional<ActorExecutionId> execution(FrontierWorldState state, UnitGroup group, UnitGroup.Member member);
    boolean mayTravel(FrontierWorldState state, UnitGroup group, long ordinal);
    io.farfrontier.palemirror.frontier.v3.model.navigation.MovementPermission movementPermission(FrontierWorldState state, UnitGroup group);
    default boolean mayContinueMovement(FrontierWorldState state, UnitGroup group) {
        return movementPermission(state, group).allowed();
    }
    /** Purpose requests cancellation of a journey, never fictional goal arrival. */
    boolean requestsJourneyStop(FrontierWorldState state, UnitGroup group);
    boolean mayClose(FrontierWorldState state, UnitGroup group);
    java.util.List<io.farfrontier.palemirror.frontier.v3.api.ProposedEvent> reconsider(FrontierWorldState state, UnitGroup group, long tick);
}
