package io.farfrontier.palemirror.frontier.v3.model.security;
import io.farfrontier.palemirror.frontier.v3.model.combat.*;
import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import java.util.*;
/** Security owns hostility and witnessed threat knowledge; it does not prepare or complete attacks. */
public final class SecurityTransitions {
    public static FrontierWorldState apply(FrontierWorldState state,SubjectId subject,SecurityEvent event,long tick){
        var security=state.security();var update=FrontierWorldStateUpdate.begin();
        if(event instanceof SecurityEvent.Incident incident){
            ActorBodyAuthority.requireObservation(state,incident.victim());
            var resident=state.humanPopulation().resident(incident.victim().actorId());
            if(!subject.equals(incident.home()) || resident==null || !resident.settlementId().equals(incident.home()))
                throw new IllegalArgumentException("harm report lost its exact victim/home");
            security=security.harmed(incident,tick);
        }else if(event instanceof SecurityEvent.Seen seen){
            ActorBodyAuthority.requireActuation(state,seen.observer());
            var guard=GuardAuthority.require(state,seen.observer().execution());var contact=seen.contact();
            if(!subject.equals(guard.home()) || !contact.home().equals(guard.home()) || guard.phase()!=GuardDuty.Phase.ON_DUTY
                    || contact.atTick()!=tick || !GuardEquipment.held(state,guard)
                    || contact.target().kind()==CombatTarget.Kind.PLAYER && !security.hostile(guard.home(),contact.target().entity()))
                throw new IllegalArgumentException("unwitnessed or nonhostile security contact");
            // Range and LOS are native perception facts, checked against the live observer
            // by the physical executor. Canonical movement may still retain an older pose;
            // it cannot independently re-measure that native observation. Domain pursuit
            // bounds remain with GuardResponse and do not grant an unseen melee effect.
            security=security.seen(contact,seen.observer().body().actorId(),seen.observerPosition());
        }else if(event instanceof SecurityEvent.ContactEnded ended){
            // This is an already observed fact, not a new actuation permission. Its
            // exact body may have left HOT before the durable observation is delivered.
            ActorBodyAuthority.requireObservation(state,ended.observer().body());
            var resident=state.humanPopulation().resident(ended.observer().body().actorId());
            if(!subject.equals(ended.home()) || resident==null || !resident.settlementId().equals(ended.home())
                    || ended.observer().execution().activityKind()!=io.farfrontier.palemirror.frontier.v3.model.execution.ActorActivityKind.GUARD)
                throw new IllegalArgumentException("ended contact lost its exact guard observer/home");
            var contact=security.contact(ended.home(),ended.target());
            // A newer sighting/respawn supersedes this fact. Acknowledge history without
            // erasing that successor, just as a repeated end cannot recreate a contact.
            if(contact!=null && contact.target().equals(ended.target()) && contact.revision()==ended.expectedRevision())
                security=security.contactEnded(new SecurityState.ContactKey(ended.home(),ended.target()));
        }
        return state.withChanges(update.security(security));
    }
    private SecurityTransitions(){}
}
