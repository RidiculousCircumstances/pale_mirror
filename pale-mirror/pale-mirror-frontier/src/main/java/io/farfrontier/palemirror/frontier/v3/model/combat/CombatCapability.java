package io.farfrontier.palemirror.frontier.v3.model.combat;
import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import java.util.*;
/** Role adapter supplies permission/reference and lifecycle feedback; it never applies a hit. */
public interface CombatCapability {
    ActorActivityKind kind();
    CombatProfile profile(FrontierWorldState state,ActorExecutionId execution);
    void requireReference(FrontierWorldState state,ActorExecutionId execution);
    void requirePending(FrontierWorldState state,MeleeStrike strike);
    List<CombatOrder> orders(FrontierWorldState state,long tick);
    Optional<CombatOrder> current(FrontierWorldState state,ActorExecutionId execution,long tick);
    boolean permitsApproach(FrontierWorldState state,io.farfrontier.palemirror.frontier.v3.model.navigation.ActorMovement movement);
    /** A role may yield reversible routine travel to self-defence before its normal intercept is selected. */
    boolean permitsManeuverInterruption(FrontierWorldState state,io.farfrontier.palemirror.frontier.v3.model.navigation.ActorMovement movement,CombatOrder order);
    KnownPedestrianRouteKnowledge maneuverKnowledge(FrontierWorldState state,ActorExecutionId execution);
    io.farfrontier.palemirror.frontier.v3.model.navigation.TravelPace walkingPace(FrontierWorldState state,ActorExecutionId execution);
    OptionalLong harmedAt(FrontierWorldState state,CombatOrder order);
    Set<MeleeManeuver.Kind> maneuvers();
    List<ProposedEvent> wake(FrontierWorldState state,ActorExecutionId execution,long tick);
    Set<CombatTarget> areaTargets(FrontierWorldState state,CombatOrder order,long tick);
    Optional<Fact> targetEnded(FrontierWorldState state,ActorActuationId actor,CombatTarget target);
    record Fact(SubjectId subject,FrontierPayload payload) {
        public Fact {Objects.requireNonNull(subject);Objects.requireNonNull(payload);}
    }
}
