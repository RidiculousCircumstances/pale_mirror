package io.farfrontier.palemirror.frontier.v3.model.group;

import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.group.UnitGroup;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionId;
import java.util.Optional;

/** Mission-owned policy supplied to generic coordination; no concrete jobs inside the coordinator. */
public interface UnitGroupMissionPort {
    UnitGroup.MissionKind kind();
    void validate(FrontierWorldState state, UnitGroup group);
    KnownPedestrianRouteKnowledge knowledge(FrontierWorldState state, UnitGroup group);
    SurfaceAnchor destination(FrontierWorldState state, UnitGroup group, long ordinal);
    Optional<ActorExecutionId> execution(FrontierWorldState state, UnitGroup group, UnitGroup.Member member);
    boolean mayTravel(FrontierWorldState state, UnitGroup group, long ordinal);
    boolean mayClose(FrontierWorldState state, UnitGroup group);
    java.util.List<io.farfrontier.palemirror.frontier.v3.api.ProposedEvent> reconsider(FrontierWorldState state, UnitGroup group, long tick);
}
