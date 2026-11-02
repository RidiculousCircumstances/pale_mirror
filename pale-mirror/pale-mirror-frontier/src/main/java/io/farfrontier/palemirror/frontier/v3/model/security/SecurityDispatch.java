package io.farfrontier.palemirror.frontier.v3.model.security;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import java.util.*;

/** Combines individual local defence with settlement reinforcement. Never routes or acquires a body. */
public final class SecurityDispatch {
    public static boolean eligible(FrontierWorldState state, GuardDuty duty) {
        var actor=state.actorLocations().get(duty.execution().actorId());
        var execution=state.actorExecutions().actors().get(duty.execution().actorId());
        return duty.phase()==GuardDuty.Phase.ON_DUTY && GuardEquipment.held(state,duty)
                && actor!=null && actor.condition().status()==ActorLifeStatus.ALIVE
                && execution!=null && execution.current().equals(Optional.of(duty.execution()))
                && !GuardStaffingRelease.requested(state,duty);
    }
    public record Candidate(SubjectId duty,SurfaceAnchor position,Optional<CombatTarget> missionTarget,
                            Optional<CombatEngagement> localReaction) {
        public Candidate {
            Objects.requireNonNull(duty);Objects.requireNonNull(position);Objects.requireNonNull(missionTarget);Objects.requireNonNull(localReaction);
            if(localReaction.filter(e -> !e.kind().local()).isPresent())throw new IllegalArgumentException("local nomination is a reinforcement");
        }
    }
    /** Policy-authorized distant targets, supplied separately from individual local knowledge. */
    public record Reinforcement(SurfaceAnchor anchor,Set<CombatTarget> targets) {
        public Reinforcement {Objects.requireNonNull(anchor);targets=Set.copyOf(targets);}
    }
    public static int budget(SecurityRules rules,int available) {
        return available==0?0:Math.max(1,available*rules.responsePercent()/100);
    }
    public static Map<SubjectId,SecurityState.Response> plan(FrontierWorldState state,SubjectId home,long tick) {
        var rules=state.bootstrap().ruleset().security();var awareness=state.security().awareness();
        var contacts=state.security().contactsIn(home);
        var harms=awareness.harmsIn(home);
        var areas=ProtectionObligations.read(state,home).stream()
                .map(target -> new CombatReactionPolicy.Area(target.observationAnchor(),rules.pursuitMargin())).toList();
        var guards=GuardEquipment.in(state,home).stream().filter(g -> eligible(state,g)).map(g -> {
            var actor=g.execution().actorId();var position=io.farfrontier.palemirror.frontier.v3.model.navigation.ActorMovementProjection.bodyAt(state,actor,tick).supportingSurface();
            var threats=contacts.stream().filter(c -> c.current(tick,rules.contactLifetime()))
                    .map(c -> new CombatReactionPolicy.Threat(c.target(),new SurfaceAnchor(c.position()),awareness.sight(home,actor,c.target()),
                            harms.stream().filter(h -> h.key().attacker().equals(c.target())).toList())).toList();
            var local=CombatReactionPolicy.choose(rules.reactionRules(),actor,position,areas,threats,
                    state.security().response(g.id()).map(SecurityState.Response::engagement),tick);
            return new Candidate(g.id(),position,g.mission().flatMap(GuardMission::response),local);
        }).toList();
        var reinforcement=new Reinforcement(new SurfaceAnchor(FrontierWorldStateSupport.settlement(state.bootstrap(),home).anchor()),
                contacts.stream().filter(c -> GuardResponse.inScope(state,home,c)).map(SecurityState.Contact::target)
                        .collect(java.util.stream.Collectors.toSet()));
        return select(rules,home,tick,contacts,guards,reinforcement,state.security().responses());
    }
    public static Map<SubjectId,SecurityState.Response> select(SecurityRules rules,SubjectId home,long tick,
            List<SecurityState.Contact> observations,List<Candidate> guards,Reinforcement reinforcement,
            Map<SubjectId,SecurityState.Response> previous) {
        if(guards.stream().map(Candidate::duty).distinct().count()!=guards.size()
                || observations.stream().anyMatch(c -> !c.home().equals(home))
                || observations.stream().map(SecurityState.Contact::target).distinct().count()!=observations.size())
            throw new IllegalArgumentException("foreign or duplicate dispatch inputs");
        var contacts=observations.stream().filter(c -> c.current(tick,rules.investigationTicks()))
                .sorted(Comparator.comparing((SecurityState.Contact c)->!c.current(tick,rules.contactLifetime()))
                        .thenComparing(c->c.target().kind().wireTag()).thenComparing(c->c.target().entity())
                        .thenComparing(c->c.target().dimension())).toList();
        var result=new LinkedHashMap<SubjectId,SecurityState.Response>();
        for(var guard:guards) {
            if(guard.localReaction().isPresent()) {
                var local=guard.localReaction().orElseThrow();
                if(contacts.stream().noneMatch(c -> c.target().equals(local.target()) && c.current(tick,rules.contactLifetime())
                        && local.contains(new SurfaceAnchor(c.position()))))throw new IllegalArgumentException("local reaction lacks a current bounded contact");
                result.put(guard.duty(),response(guard.duty(),home,local,tick,previous));
            } else {
                // A lost local sight retains only this guard's already selected finite search.
                var old=previous.get(guard.duty());
                if(old!=null && old.contact().home().equals(home) && old.engagement().kind().local()
                        && guard.missionTarget().equals(Optional.of(old.contact().target()))
                        && contacts.stream().anyMatch(c -> c.target().equals(old.contact().target()) && !c.current(tick,rules.contactLifetime())
                        && old.engagement().contains(new SurfaceAnchor(c.position()))))result.put(guard.duty(),old);
            }
        }
        int budget=budget(rules,guards.size()-result.size()),sent=0;
        for(int slot=0;slot<rules.respondersPerThreat() && sent<budget;slot++)for(var contact:contacts) {
            if(sent>=budget)break;
            if(!reinforcement.targets().contains(contact.target()) || result.values().stream()
                    .filter(r -> r.contact().target().equals(contact.target())).count()>slot)continue;
            var key=new SecurityState.ContactKey(home,contact.target());var point=new SurfaceAnchor(contact.position());
            if(SecurityCoverage.distance(reinforcement.anchor(),point)>rules.homePursuitLimit())continue;
            boolean fresh=contact.current(tick,rules.contactLifetime());
            var candidates=guards.stream().filter(g -> !result.containsKey(g.duty()))
                    .filter(g -> fresh || Optional.ofNullable(previous.get(g.duty())).filter(r -> r.contact().equals(key)
                            && r.engagement().kind()==CombatEngagement.Kind.REINFORCEMENT).isPresent()
                            && g.missionTarget().equals(Optional.of(contact.target())))
                    .sorted(Comparator.comparingLong((Candidate g)->SecurityCoverage.distance(g.position(),point))
                            .thenComparing(Candidate::duty)).toList();
            if(candidates.isEmpty())continue;
            var nearest=candidates.getFirst();
            var guard=candidates.stream().filter(g -> Optional.ofNullable(previous.get(g.duty())).filter(r -> r.contact().equals(key)).isPresent()
                            && SecurityCoverage.distance(g.position(),point)<=SecurityCoverage.distance(nearest.position(),point)+rules.reassignmentAdvantage())
                    .min(Comparator.comparing(Candidate::duty)).orElse(nearest);
            var engagement=new CombatEngagement(contact.target(),CombatEngagement.Kind.REINFORCEMENT,reinforcement.anchor(),rules.homePursuitLimit());
            result.put(guard.duty(),response(guard.duty(),home,engagement,tick,previous));sent++;
        }
        return Map.copyOf(result);
    }
    private static SecurityState.Response response(SubjectId duty,SubjectId home,CombatEngagement engagement,long tick,
            Map<SubjectId,SecurityState.Response> previous) {
        var key=new SecurityState.ContactKey(home,engagement.target());
        return Optional.ofNullable(previous.get(duty)).filter(r -> r.contact().equals(key) && r.engagement().equals(engagement))
                .orElseGet(() -> new SecurityState.Response(duty,key,tick,engagement));
    }
    private SecurityDispatch() { }
}
