package io.farfrontier.palemirror.frontier.v3.model.security;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import java.util.*;

/** Settlement response policy only. Never routes, acquires execution or moves a body. */
public final class SecurityDispatch {
    public static boolean eligible(FrontierWorldState state, GuardDuty duty) {
        var actor=state.actorLocations().get(duty.execution().actorId());
        var execution=state.actorExecutions().actors().get(duty.execution().actorId());
        return duty.phase()==GuardDuty.Phase.ON_DUTY && GuardEquipment.held(state,duty)
                && actor!=null && actor.condition().status()==ActorLifeStatus.ALIVE
                && execution!=null && execution.current().equals(Optional.of(duty.execution()))
                && !GuardStaffingRelease.requested(state,duty);
    }
    /** Read-only nominated duty; eligibility/ownership stays at the canonical composition boundary. */
    public record Candidate(SubjectId duty,SurfaceAnchor position,Optional<CombatTarget> missionTarget) {
        public Candidate {Objects.requireNonNull(duty);Objects.requireNonNull(position);Objects.requireNonNull(missionTarget);}
    }
    public static int budget(FrontierWorldState state, int available) {
        return available==0?0:Math.max(1,available*state.bootstrap().ruleset().security().responsePercent()/100);
    }
    public static Map<SubjectId,SecurityState.Response> plan(FrontierWorldState state,SubjectId home,long tick) {
        var rules=state.bootstrap().ruleset().security();
        var guards=GuardEquipment.in(state,home).stream().filter(g -> eligible(state,g))
                .map(g -> new Candidate(g.id(),state.actorLocations().get(g.execution().actorId()).supportingSurface(),
                        g.mission().flatMap(GuardMission::response))).toList();
        var contacts=state.security().contactsIn(home).stream().filter(c -> GuardResponse.inScope(state,home,c)).toList();
        return select(rules,home,tick,contacts,guards,state.security().responses());
    }
    public static Map<SubjectId,SecurityState.Response> select(SecurityRules rules,SubjectId home,long tick,
            List<SecurityState.Contact> observations,List<Candidate> guards,Map<SubjectId,SecurityState.Response> previous) {
        if(guards.stream().map(Candidate::duty).distinct().count()!=guards.size()
                || observations.stream().anyMatch(c -> !c.home().equals(home)))throw new IllegalArgumentException("foreign or duplicate dispatch inputs");
        int budget=guards.isEmpty()?0:Math.max(1,guards.size()*rules.responsePercent()/100);
        var contacts=observations.stream()
                .filter(c -> c.current(tick,rules.investigationTicks()))
                .sorted(Comparator.comparing((SecurityState.Contact c)->!c.current(tick,rules.contactLifetime()))
                        .thenComparing(c->c.target().kind().wireTag()).thenComparing(c->c.target().entity())
                        .thenComparing(c->c.target().dimension())).toList();
        var result=new LinkedHashMap<SubjectId,SecurityState.Response>();
        for(int slot=0;slot<rules.respondersPerThreat() && result.size()<budget;slot++)for(var contact:contacts) {
            if(result.size()>=budget)break;
            var key=new SecurityState.ContactKey(home,contact.target());
            boolean fresh=contact.current(tick,rules.contactLifetime());
            var candidates=guards.stream().filter(g -> !result.containsKey(g.duty()))
                    .filter(g -> fresh || Optional.ofNullable(previous.get(g.duty())).filter(r -> r.contact().equals(key)).isPresent()
                            && g.missionTarget().equals(Optional.of(contact.target())))
                    .sorted(Comparator.comparing((Candidate g)->!Optional.ofNullable(previous.get(g.duty())).filter(r -> r.contact().equals(key)).isPresent())
                            .thenComparingLong(g -> SecurityCoverage.distance(g.position(),
                                    new SurfaceAnchor(contact.position()))).thenComparing(Candidate::duty)).toList();
            if(candidates.isEmpty())continue;
            var guard=candidates.getFirst();
            result.put(guard.duty(),Optional.ofNullable(previous.get(guard.duty())).filter(r -> r.contact().equals(key))
                    .orElseGet(() -> new SecurityState.Response(guard.duty(),key,tick)));
        }
        return Map.copyOf(result);
    }
    private SecurityDispatch() { }
}
