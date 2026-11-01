package io.farfrontier.palemirror.frontier.v3.model.security;
import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorActuationId;
import java.util.*;
/** Confirmed native ingress. No proposal can grant its own body or resource authority. */
public sealed interface SecurityEvent extends FrontierPayload permits SecurityEvent.Incident,SecurityEvent.Seen,SecurityEvent.Prepared,SecurityEvent.Completed {
    record Incident(SubjectId home,io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyId victim,UUID attacker,double observedDamage) implements SecurityEvent {
        public static final String TYPE="frontier.security.incident";
        public Incident {Objects.requireNonNull(home);Objects.requireNonNull(victim);Objects.requireNonNull(attacker);
            if(!Double.isFinite(observedDamage)||observedDamage<=0)throw new IllegalArgumentException("unconfirmed harm cannot create hostility");}
        public String type(){return TYPE;}
        public boolean requiresDurableBeforeEffect(){return true;}
    }
    record Seen(ActorActuationId observer,SecurityState.Contact contact) implements SecurityEvent {
        public static final String TYPE="frontier.security.seen";
        public Seen {Objects.requireNonNull(observer);Objects.requireNonNull(contact);}
        public String type(){return TYPE;}
    }
    record Prepared(SecurityState.Strike strike) implements SecurityEvent {
        public static final String TYPE="frontier.combat.prepared";
        public Prepared {Objects.requireNonNull(strike);}
        public String type(){return TYPE;}
        public boolean requiresDurableBeforeEffect(){return true;}
    }
    record Completed(SubjectId duty,long attemptOrdinal,boolean applied,ItemCondition after) implements SecurityEvent {
        public static final String TYPE="frontier.combat.completed";
        public Completed {Objects.requireNonNull(duty);Objects.requireNonNull(after);if(attemptOrdinal<1)throw new IllegalArgumentException("invalid strike outcome");}
        public String type(){return TYPE;}
        public boolean requiresDurableBeforeEffect(){return true;}
    }
}
