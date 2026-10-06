package io.farfrontier.palemirror.frontier.v3.model.navigation;

import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionState;
import java.util.List;

/** Purpose owns admission/arrival; common movement owns the route clock and actual pose. */
public interface ActorMovementProvider {
    ActorMovementContext.Provider key();
    void validate(FrontierWorldState state, ActorMovement movement);
    FrontierWorldState start(FrontierWorldState state, ActorMovement movement, FrontierWorldStateUpdate movementUpdate);
    List<SurfaceAnchor> route(FrontierWorldState state, ActorMovement movement, SurfaceAnchor start);
    List<SurfaceAnchor> coldSegment(FrontierWorldState state, ActorMovement movement, List<SurfaceAnchor> route);
    ActorExecutionState arrivalAuthority(FrontierWorldState state, ActorMovement movement);
    java.util.Optional<BodyPosition> interruptionCheckpoint(FrontierWorldState state, ActorMovement movement, long atTick);
    ActorExecutionState interruptionAuthority(FrontierWorldState state, ActorMovement movement);
    boolean permitsReplacement(ResidentActivityChoice.Kind next);
    List<ProposedEvent> arrived(FrontierWorldState state, ActorMovement movement, long atTick);
    List<ProposedEvent> interrupted(FrontierWorldState state, ActorMovement movement, long atTick);
}
