package io.farfrontier.palemirror.frontier.v3.model.security;
import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import java.util.*;
/** Combat owns only relations/contacts/attack lifecycle. It consumes existing body, role and inventory authority. */
public final class SecurityTransitions {
    public static FrontierWorldState apply(FrontierWorldState state,SubjectId subject,SecurityEvent event,long tick){
        var security=state.security();var update=FrontierWorldStateUpdate.begin();
        if(event instanceof SecurityEvent.Incident incident){
            ActorBodyAuthority.requireObservation(state,incident.victim());
            var resident=state.humanPopulation().resident(incident.victim().actorId());
            if(!subject.equals(incident.home()) || resident==null || !resident.settlementId().equals(incident.home()))
                throw new IllegalArgumentException("harm report lost its exact victim/home");
            security=security.offence(incident.home(),incident.attacker(),tick);
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
            security=security.observed(contact);
        }else if(event instanceof SecurityEvent.ContactEnded ended){
            ActorBodyAuthority.requireActuation(state,ended.observer());
            var guard=GuardAuthority.require(state,ended.observer().execution());
            var contact=security.contacts().get(ended.home());
            if(!subject.equals(ended.home()) || !guard.home().equals(ended.home()) || guard.phase()!=GuardDuty.Phase.ON_DUTY
                    || contact==null || !contact.target().equals(ended.target()) || contact.revision()!=ended.expectedRevision())
                throw new IllegalArgumentException("ended contact lost its exact observer or predecessor");
            security=security.contactEnded(ended.home());
        }else if(event instanceof SecurityEvent.Prepared prepared){
            var strike=prepared.strike();ActorBodyAuthority.requireActuation(state,strike.attacker());
            var guard=GuardAuthority.require(state,strike.attacker().execution());var contact=security.contacts().get(guard.home());
            var item=state.inventory().items().get(strike.weapon());
            if(!subject.equals(strike.duty()) || !guard.id().equals(strike.duty()) || guard.revision()!=strike.dutyRevision()
                    || guard.phase()!=GuardDuty.Phase.ON_DUTY || !GuardEquipment.held(state,guard) || !guard.weapon().equals(strike.weapon())
                    || item==null || !item.condition().equals(strike.before()) || !item.itemKind().equals(strike.profile().itemKind())
                    || contact==null || GuardResponse.contact(state,guard,tick).isEmpty() || !contact.target().equals(strike.target()) || strike.preparedAt()!=tick
                    || tick<security.nextAttack().getOrDefault(guard.id(),0L) || state.actorMovements().containsKey(guard.execution().actorId()))
                throw new IllegalArgumentException("combat strike lacks current role/contact/weapon/readiness");
            security=security.prepared(strike);
        }else{
            var outcome=(SecurityEvent.Completed)event;var strike=Objects.requireNonNull(security.pending().get(outcome.duty()),"pending strike");
            if(!subject.equals(outcome.duty()) || outcome.attemptOrdinal()!=strike.attemptOrdinal()
                    || !outcome.after().equals(outcome.applied() && strike.profile().wear()>0?strike.before().wear(strike.profile().wear()):strike.before()))
                throw new IllegalArgumentException("strike outcome lost its exact wear/effect declaration");
            if(outcome.applied()) {
                var item=state.inventory().items().get(strike.weapon());
                // A confirmed later transfer/death may move or retire this exact item. Wear neither recreates it nor changes its holder/title.
                if(item!=null) {
                    if(!item.itemKind().equals(strike.profile().itemKind()) || !item.condition().equals(strike.before()) && !item.condition().equals(outcome.after()))
                        throw new IllegalArgumentException("strike receipt conflicts with the current exact weapon condition");
                    if(!item.condition().equals(outcome.after())) update.inventory(state.inventory().changeCondition(item,outcome.after()));
                }
            }
            security=security.completed(strike,tick);
        }
        return state.withChanges(update.security(security));
    }
    private SecurityTransitions(){}
}
