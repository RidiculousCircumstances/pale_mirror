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
    void requireRoute(FrontierWorldState state, ActorMovement movement, List<SurfaceAnchor> route);
    List<SurfaceAnchor> coldSegment(FrontierWorldState state, ActorMovement movement, List<SurfaceAnchor> route);
    default List<SurfaceAnchor> coldSegment(FrontierWorldState state, ActorMovement movement, List<SurfaceAnchor> route, long tick) {
        return coldSegment(state, movement, route);
    }
    default void requireColdRoute(FrontierWorldState state, ActorMovement movement, List<SurfaceAnchor> route, long tick) {
        requireRoute(state, movement, route);
    }
    default long ticksPerEdge(FrontierWorldState state, ActorMovement movement) { return 20L; }
    /** Read-only caller constraint; the common navigator remains the only physical actuator. */
    default boolean mayAdvance(FrontierWorldState state, ActorMovement movement, SurfaceAnchor actual, long tick) {
        return movementPermission(state, movement, actual, tick, ActorPositionView.canonical(state, tick)).allowed();
    }
    /** HOT consumers supply current physical observations without becoming durable pose writers. */
    default boolean mayAdvance(FrontierWorldState state, ActorMovement movement, SurfaceAnchor actual, long tick,
                               ActorPositionView positions) {
        return movementPermission(state, movement, actual, tick, positions).allowed();
    }
    default MovementPermission movementPermission(FrontierWorldState state, ActorMovement movement, SurfaceAnchor actual,
                                                   long tick, ActorPositionView positions) { return MovementPermission.allow(); }
    ActorExecutionState arrivalAuthority(FrontierWorldState state, ActorMovement movement);
    java.util.Optional<BodyPosition> interruptionCheckpoint(FrontierWorldState state, ActorMovement movement, long atTick);
    ActorExecutionState interruptionAuthority(FrontierWorldState state, ActorMovement movement);
    boolean permitsReplacement(ResidentActivityChoice.Kind next);
    List<ProposedEvent> arrived(FrontierWorldState state, ActorMovement movement, long atTick);
    List<ProposedEvent> interrupted(FrontierWorldState state, ActorMovement movement, long atTick);
}
