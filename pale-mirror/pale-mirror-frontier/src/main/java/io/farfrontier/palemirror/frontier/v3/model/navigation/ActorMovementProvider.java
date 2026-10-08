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
    /** The owner declares passages; shared placement alone selects a connected local birth surface. */
    KnownPedestrianRouteKnowledge placementKnowledge(FrontierWorldState state, ActorMovement movement);
    List<SurfaceAnchor> route(FrontierWorldState state, ActorMovement movement, SurfaceAnchor start);
    void requireRoute(FrontierWorldState state, ActorMovement movement, List<SurfaceAnchor> route);
    List<SurfaceAnchor> coldSegment(FrontierWorldState state, ActorMovement movement, List<SurfaceAnchor> route);
    default List<SurfaceAnchor> coldSegment(FrontierWorldState state, ActorMovement movement, List<SurfaceAnchor> route, long tick) {
        return coldSegment(state, movement, route);
    }
    default List<SurfaceAnchor> coldSegment(FrontierWorldState state, ActorMovement movement, List<SurfaceAnchor> route,
                                           long tick, ActorPositionView positions) {
        return coldSegment(state, movement, route, tick);
    }
    /** Explicit position dependencies; the physical adapter does not discover family rosters. */
    default List<io.farfrontier.palemirror.frontier.v3.api.SubjectId> positionSubjects(FrontierWorldState state, ActorMovement movement) {
        return List.of(movement.order().actorId());
    }
    default void requireColdRoute(FrontierWorldState state, ActorMovement movement, List<SurfaceAnchor> route, long tick) {
        requireRoute(state, movement, route);
    }
    default void requireColdRoute(FrontierWorldState state, ActorMovement movement, List<SurfaceAnchor> route,
                                  long tick, ActorPositionView positions) {
        requireColdRoute(state, movement, route, tick);
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
    /** Previous decision is local steering memory under the same exact command, not domain progress. */
    default MovementPermission movementPermission(FrontierWorldState state, ActorMovement movement, SurfaceAnchor actual,
                                                   long tick, ActorPositionView positions, MovementPermission previous) {
        return movementPermission(state, movement, actual, tick, positions);
    }
    /** Registered owner declares a service approach; navigation owns waiting/clearance, never its transaction. */
    default java.util.Optional<ServiceAccessDemand.Identity> serviceApproach(FrontierWorldState state, ActorMovement movement) {
        return java.util.Optional.empty();
    }
    ActorExecutionState arrivalAuthority(FrontierWorldState state, ActorMovement movement);
    java.util.Optional<BodyPosition> interruptionCheckpoint(FrontierWorldState state, ActorMovement movement, long atTick);
    ActorExecutionState interruptionAuthority(FrontierWorldState state, ActorMovement movement);
    boolean permitsReplacement(ResidentActivityChoice.Kind next);
    List<ProposedEvent> arrived(FrontierWorldState state, ActorMovement movement, long atTick);
    List<ProposedEvent> interrupted(FrontierWorldState state, ActorMovement movement, long atTick);
}
