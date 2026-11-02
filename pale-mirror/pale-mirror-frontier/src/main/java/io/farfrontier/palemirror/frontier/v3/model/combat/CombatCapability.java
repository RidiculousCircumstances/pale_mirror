package io.farfrontier.palemirror.frontier.v3.model.combat;
import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import java.util.*;
/** Role adapter supplies permission/reference and lifecycle feedback; it never applies a hit. */
public interface CombatCapability {
    ActorActivityKind kind();
    void requireReference(FrontierWorldState state,ActorExecutionId execution);
    void requirePending(FrontierWorldState state,MeleeStrike strike);
    List<CombatOrder> orders(FrontierWorldState state,long tick);
    Optional<CombatOrder> current(FrontierWorldState state,ActorExecutionId execution,long tick);
    boolean permitsApproach(FrontierWorldState state,io.farfrontier.palemirror.frontier.v3.model.navigation.ActorMovement movement);
    List<ProposedEvent> wake(FrontierWorldState state,ActorExecutionId execution,long tick);
    Optional<Fact> targetEnded(FrontierWorldState state,MeleeStrike strike);
    record Fact(SubjectId subject,FrontierPayload payload) {
        public Fact {Objects.requireNonNull(subject);Objects.requireNonNull(payload);}
    }
}
