package io.farfrontier.palemirror.frontier.v3.model.security;
import io.farfrontier.palemirror.frontier.v3.model.combat.*;
import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorActuationId;
import java.util.*;
/** Confirmed native ingress. No proposal can grant its own body or resource authority. */
public sealed interface SecurityEvent extends FrontierPayload permits SecurityEvent.Incident,SecurityEvent.Seen,SecurityEvent.ContactEnded {
    record Incident(SubjectId home,io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyId victim,
                    CombatTarget attacker,BlockPosition victimPosition,Optional<BlockPosition> visibleAttackerPosition,double observedDamage) implements SecurityEvent {
        public static final String TYPE="frontier.security.incident";
        public Incident {Objects.requireNonNull(home);Objects.requireNonNull(victim);Objects.requireNonNull(attacker);Objects.requireNonNull(victimPosition);Objects.requireNonNull(visibleAttackerPosition);
            if(!Double.isFinite(observedDamage)||observedDamage<=0)throw new IllegalArgumentException("unconfirmed harm cannot create hostility");}
        public String type(){return TYPE;}
        public boolean requiresDurableBeforeEffect(){return true;}
    }
    record Seen(ActorActuationId observer,SecurityState.Contact contact,BlockPosition observerPosition) implements SecurityEvent {
        public static final String TYPE="frontier.security.seen";
        public Seen {Objects.requireNonNull(observer);Objects.requireNonNull(contact);Objects.requireNonNull(observerPosition);}
        public String type(){return TYPE;}
    }
    /** Confirmed end of this exact physical opportunity; persistent relations are unaffected. */
    record ContactEnded(ActorActuationId observer,SubjectId home,CombatTarget target,long expectedRevision) implements SecurityEvent {
        public static final String TYPE="frontier.security.contact_ended";
        public ContactEnded {Objects.requireNonNull(observer);Objects.requireNonNull(home);Objects.requireNonNull(target);
            if(expectedRevision<1)throw new IllegalArgumentException("invalid ended contact predecessor");}
        public String type(){return TYPE;}
        public boolean requiresDurableBeforeEffect(){return true;}
    }
}
