package io.farfrontier.palemirror.frontier.v3.model.combat;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import java.util.*;

/** Pure local encounter calculation over declared participants and registered role permissions. No route search or body writer. */
public final class CombatEngagementPlanner {
    public static CombatState reconcile(FrontierWorldState state, CombatState combat, ExactInventory inventory, CombatEngagement.Key key, long tick, CombatCapabilities capabilities) {
        var before=combat.engagements().get(key);if(before==null)return combat;
        var rules=state.bootstrap().ruleset().melee();var policy=rules.engagements();
        var members=new LinkedHashMap<SubjectId,CombatEngagement.Participant>();var orders=new LinkedHashMap<SubjectId,CombatOrder>();
        for(var member:before.participants().values()){
            var execution=member.execution();var actor=state.actorLocations().get(execution.actorId());
            if(actor==null || actor.condition().status()!=ActorLifeStatus.ALIVE || !execution.equals(state.actorExecutions().current(execution.activityKind()).get(execution.actorId())))continue;
            var order=capabilities.current(state,execution,tick).filter(value->CombatEngagement.Key.of(value).equals(key) && value.current(tick)).orElse(null);
            if(order==null || tick-member.renewedAt()>policy.participantLifetime())continue;
            var item=inventory.items().get(order.weapon());
            if(item==null || !item.condition().usable() || !item.custody().equals(new InventoryCustody.Actor(execution.actorId())))continue;
            members.put(execution.activityOwnerId(),member);orders.put(execution.activityOwnerId(),order);
        }
        if(members.isEmpty())return combat.withoutEngagement(key);
        boolean promoted=promote(members);
        for(var entry:new ArrayList<>(members.entrySet())){
            var member=entry.getValue();var point=combat.positions().get(entry.getKey());
            if(member.role()!=MeleeSector.Role.PRESSURE && member.role()!=MeleeSector.Role.SUPPORT && point!=null
                    && point.execution().equals(member.execution()) && point.target().equals(key.target())
                    && (point.reason().unavailable() || point.reason()==MeleePosition.Reason.UNREACHABLE)
                    && tick-point.sinceTick()>=policy.confirmedFlankFailureTicks()){
                members.put(entry.getKey(),new CombatEngagement.Participant(key,member.execution(),MeleeSector.Role.SUPPORT,member.renewedAt()));promoted=true;
            }
        }
        final boolean formationChanged=promoted;
        var newest=orders.values().stream().max(Comparator.comparingLong(CombatOrder::observedAt).thenComparing(CombatOrder::owner)).orElseThrow();
        boolean rebased=CombatEngagement.distance(before.anchor(),newest.targetPosition())>=policy.rebaseDistance();
        var bearing=rebased?MeleeSector.pressure(orders.get(members.entrySet().stream().filter(e->e.getValue().role()==MeleeSector.Role.PRESSURE)
                .map(Map.Entry::getKey).findFirst().orElseThrow()).actorPosition(),newest.targetPosition()):new MeleeSector(MeleeSector.Role.PRESSURE,before.axisX(),before.axisZ());
        var wave=before.wave().filter(value->!formationChanged && !rebased && value.endsAt()>=tick && value.slots().stream().anyMatch(slot->!slot.committed())
                && CombatEngagement.distance(value.targetPosition(),newest.targetPosition())<=policy.targetDriftMicros()/1_000_000.0
                && value.slots().stream().allMatch(slot->{var member=members.get(slot.execution().activityOwnerId());return member!=null && member.execution().equals(slot.execution())
                        && ActorBodyAuthority.retainsPhysicalCustody(state,slot.execution().actorId())
                        && ActorBodyAuthority.current(state,slot.execution().actorId()).equals(slot.participant().body());}));
        var ready=orders.values().stream().filter(order->ready(state,combat,order,tick,rules)).sorted(Comparator.comparingLong((CombatOrder order)->combat.nextAttack().getOrDefault(order.owner(),0L)).thenComparing(CombatOrder::owner)).toList();
        long since=ready.isEmpty()?0:before.assemblySince()==0?tick:before.assemblySince();
        if(wave.isEmpty() && !ready.isEmpty() && (ready.size()>1 || members.size()==1 || tick-since>=policy.assemblyTicks())){
            var slots=new ArrayList<CombatEngagement.Slot>();
            for(var order:ready.stream().limit(policy.maximumWaveSize()).toList()){
                long opens=tick+(members.size()==1?0:policy.releaseLeadTicks())+(long)slots.size()*policy.slotSpacingTicks();
                slots.add(new CombatEngagement.Slot(combat.stances().get(order.owner()).observer(),opens,opens+policy.slotWindowTicks(),false));
            }
            wave=Optional.of(new CombatEngagement.Wave(newest.targetPosition(),slots));since=0;
        }
        var next=new CombatEngagement(key,before.revision()+1,rebased?newest.targetPosition():before.anchor(),bearing.axisX(),bearing.axisZ(),members,since,wave);
        return combat.withEngagement(next);
    }
    private static boolean ready(FrontierWorldState state,CombatState combat,CombatOrder order,long tick,MeleeRules rules){
        var stance=combat.stances().get(order.owner());
        return stance!=null && stance.attackReady() && stance.current(order,tick,rules)
                && ActorBodyAuthority.retainsPhysicalCustody(state,order.execution().actorId())
                && ActorBodyAuthority.current(state,order.execution().actorId()).equals(stance.observer().body())
                && !combat.pending().containsKey(order.owner()) && !combat.maneuverBlocksStrike(order.owner(),tick,rules.maneuvers())
                && tick>=combat.nextAttack().getOrDefault(order.owner(),0L)
                && combat.affords(order.owner(),tick,rules.readiness().quickCost(),rules);
    }
    static boolean promote(Map<SubjectId,CombatEngagement.Participant> members){
        if(members.values().stream().anyMatch(member->member.role()==MeleeSector.Role.PRESSURE))return false;
        var id=members.keySet().stream().min(Comparator.naturalOrder()).orElseThrow();var member=members.get(id);
        members.put(id,new CombatEngagement.Participant(member.engagement(),member.execution(),MeleeSector.Role.PRESSURE,member.renewedAt()));return true;
    }
    public static boolean permitsStrike(CombatState state,CombatOrder order,io.farfrontier.palemirror.frontier.v3.model.execution.ActorActuationId observer,long tick,MeleeRules rules){
        return permitsStrike(state,order,observer,MeleeAttack.LUNGE,tick,rules);
    }
    /** Real close contact may spend an ordinary strike now, while the encounter still caps live commitments. */
    public static boolean permitsStrike(CombatState state,CombatOrder order,io.farfrontier.palemirror.frontier.v3.model.execution.ActorActuationId observer,MeleeAttack attack,long tick,MeleeRules rules){
        var plan=state.engagements().get(CombatEngagement.Key.of(order));
        if(plan==null || state.pending().containsKey(order.owner()) || state.recovering(order.owner(),tick)
                || state.maneuverBlocksStrike(order.owner(),tick,rules.maneuvers()) && !contactHandoff(state,order,observer,attack,tick,rules) || !state.affords(order.owner(),tick,rules.readiness().cost(attack),rules)
                || plan.participants().keySet().stream().filter(state.pending()::containsKey).limit(rules.engagements().maximumWaveSize()).count()>=rules.engagements().maximumWaveSize())return false;
        if(plan.permits(order,observer,tick,rules.engagements()))return true;
        var member=plan.participants().get(order.owner());var stance=state.stances().get(order.owner());
        return attack!=MeleeAttack.LUNGE && member!=null && member.execution().equals(order.execution())
                && observer.execution().equals(order.execution()) && stance!=null && stance.observer().equals(observer)
                && stance.current(order,tick,rules) && stance.attackReady() && stance.holding();
    }
    /** Admission preview only: the process must checkpoint and close this exact burst before Prepared. */
    public static boolean contactHandoff(CombatState state,CombatOrder order,io.farfrontier.palemirror.frontier.v3.model.execution.ActorActuationId observer,MeleeAttack attack,long tick,MeleeRules rules){
        var maneuver=state.maneuvers().get(order.owner());var stance=state.stances().get(order.owner());
        return attack!=MeleeAttack.LUNGE && maneuver!=null && maneuver.open() && maneuver.kind()==MeleeManeuver.Kind.BURST
                && maneuver.launchedAt()>0 && tick>=maneuver.launchedAt() && tick<maneuver.launchedAt()+rules.maneuvers().travelTicks()
                && maneuver.matches(order) && maneuver.actor().equals(observer)
                && stance!=null && stance.observer().equals(observer) && stance.current(order,tick,rules) && stance.holding() && stance.attackReady();
    }
    /** Consumes an existing reservation when present; the retained pending strike itself owns immediate admission. */
    public static CombatState committed(CombatState state,CombatOrder order,io.farfrontier.palemirror.frontier.v3.model.execution.ActorActuationId observer){
        var plan=Objects.requireNonNull(state.engagements().get(CombatEngagement.Key.of(order)));
        boolean slot=plan.wave().stream().flatMap(value->value.slots().stream()).anyMatch(value->value.participant().equals(observer) && !value.committed());
        return slot?state.withEngagement(plan.committed(observer)):state;
    }
    private CombatEngagementPlanner() { }
}
