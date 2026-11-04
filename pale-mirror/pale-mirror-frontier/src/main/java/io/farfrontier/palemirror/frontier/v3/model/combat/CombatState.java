package io.farfrontier.palemirror.frontier.v3.model.combat;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionId;
import java.util.*;
/** Sole durable mechanical combat owner; role orders and body/equipment title remain external. */
public record CombatState(Map<SubjectId,MeleeStrike> pending,Map<SubjectId,Long> nextAttack,
        Map<SubjectId,Armament> armaments,Map<SubjectId,Obstruction> obstructions,
        Map<SubjectId,MeleeStance> stances,Map<SubjectId,MeleePosition> positions,Map<SubjectId,Evasion> evasions,Map<SubjectId,MeleeManeuver> maneuvers,
        Map<CombatEngagement.Key,CombatEngagement> engagements,Map<SubjectId,MeleeFollowup> followups,Map<SubjectId,CombatReadiness> readiness,Map<SubjectId,MeleeExchange> exchanges) {
    public record Armament(ActorExecutionId execution,SubjectId weapon,WeaponProfile profile) {
        public Armament {Objects.requireNonNull(execution);Objects.requireNonNull(weapon);Objects.requireNonNull(profile);}
        public boolean matches(CombatOrder order){return execution.equals(order.execution()) && weapon.equals(order.weapon());}
    }
    public record Obstruction(ActorExecutionId execution,CombatTarget target,SurfaceAnchor targetPosition,List<SurfaceAnchor> stations,long atTick) {
        public Obstruction {
            Objects.requireNonNull(execution);Objects.requireNonNull(target);Objects.requireNonNull(targetPosition);stations=List.copyOf(stations);
            if(atTick<0 || stations.isEmpty() || stations.size()>8 || stations.stream().distinct().count()!=stations.size())throw new IllegalArgumentException("invalid rejected melee stations");
        }
        public boolean matches(CombatOrder order,long tick,MeleeRules rules){return execution.equals(order.execution()) && target.equals(order.target())
                && targetPosition.equals(order.targetPosition()) && tick>=atTick && tick-atTick<=rules.obstructionTicks();}
    }
    public CombatState(Map<SubjectId,MeleeStrike> pending,Map<SubjectId,Long> nextAttack,Map<SubjectId,Armament> armaments,
            Map<SubjectId,Obstruction> obstructions,Map<SubjectId,MeleeStance> stances,Map<SubjectId,MeleePosition> positions){
        this(pending,nextAttack,armaments,obstructions,stances,positions,Map.of());
    }
    public CombatState(Map<SubjectId,MeleeStrike> pending,Map<SubjectId,Long> nextAttack,Map<SubjectId,Armament> armaments,
            Map<SubjectId,Obstruction> obstructions,Map<SubjectId,MeleeStance> stances,Map<SubjectId,MeleePosition> positions,Map<SubjectId,Evasion> evasions){
        this(pending,nextAttack,armaments,obstructions,stances,positions,evasions,Map.of());
    }
    public CombatState(Map<SubjectId,MeleeStrike> pending,Map<SubjectId,Long> nextAttack,Map<SubjectId,Armament> armaments,
            Map<SubjectId,Obstruction> obstructions,Map<SubjectId,MeleeStance> stances,Map<SubjectId,MeleePosition> positions,
            Map<SubjectId,Evasion> evasions,Map<SubjectId,MeleeManeuver> maneuvers){
        this(pending,nextAttack,armaments,obstructions,stances,positions,evasions,maneuvers,Map.of());
    }
    public CombatState(Map<SubjectId,MeleeStrike> pending,Map<SubjectId,Long> nextAttack,Map<SubjectId,Armament> armaments,
            Map<SubjectId,Obstruction> obstructions,Map<SubjectId,MeleeStance> stances,Map<SubjectId,MeleePosition> positions,
            Map<SubjectId,Evasion> evasions,Map<SubjectId,MeleeManeuver> maneuvers,
        Map<CombatEngagement.Key,CombatEngagement> engagements){
        this(pending,nextAttack,armaments,obstructions,stances,positions,evasions,maneuvers,engagements,Map.of());
    }
    public CombatState(Map<SubjectId,MeleeStrike> pending,Map<SubjectId,Long> nextAttack,Map<SubjectId,Armament> armaments,
            Map<SubjectId,Obstruction> obstructions,Map<SubjectId,MeleeStance> stances,Map<SubjectId,MeleePosition> positions,
            Map<SubjectId,Evasion> evasions,Map<SubjectId,MeleeManeuver> maneuvers,
        Map<CombatEngagement.Key,CombatEngagement> engagements,
            Map<SubjectId,MeleeFollowup> followups){
        this(pending,nextAttack,armaments,obstructions,stances,positions,evasions,maneuvers,engagements,followups,Map.of());
    }
    public CombatState(Map<SubjectId,MeleeStrike> pending,Map<SubjectId,Long> nextAttack,
            Map<SubjectId,Armament> armaments,Map<SubjectId,Obstruction> obstructions,
            Map<SubjectId,MeleeStance> stances,Map<SubjectId,MeleePosition> positions,Map<SubjectId,Evasion> evasions,
            Map<SubjectId,MeleeManeuver> maneuvers,Map<CombatEngagement.Key,CombatEngagement> engagements,
            Map<SubjectId,MeleeFollowup> followups,Map<SubjectId,CombatReadiness> readiness) {
        this(pending,nextAttack,armaments,obstructions,stances,positions,evasions,maneuvers,engagements,followups,readiness,Map.of());
    }
    public CombatState {
        exchanges=ImmutableLookupMap.copyOf(exchanges);
        if(exchanges.size()>SimulationCapacity.MAX_RESIDENTS || !armaments.keySet().containsAll(exchanges.keySet()))
            throw new IllegalArgumentException("exchange lost bounded armed owner");
        exchanges.forEach((owner,value)->{if(!owner.equals(value.execution().activityOwnerId()))throw new IllegalArgumentException("foreign duel memory owner");});
        readiness=ImmutableLookupMap.copyOf(readiness);
        if(readiness.size()>SimulationCapacity.MAX_RESIDENTS)throw new IllegalArgumentException("readiness bound");

        followups=ImmutableLookupMap.copyOf(followups);
        if(followups.size()>SimulationCapacity.MAX_RESIDENTS)throw new IllegalArgumentException("followup bound");
        followups.forEach((owner,value)->{if(!owner.equals(value.owner()))throw new IllegalArgumentException("foreign followup owner");});
        engagements=ImmutableLookupMap.copyOf(engagements);
        if(engagements.size()>SimulationCapacity.MAX_RESIDENTS)throw new IllegalArgumentException("engagement bound");
        var participants=new HashSet<SubjectId>();
        engagements.forEach((key,plan)->{if(!key.equals(plan.key()))throw new IllegalArgumentException("foreign engagement key");
            for(var member:plan.participants().values())if(!participants.add(member.execution().activityOwnerId()))
                throw new IllegalArgumentException("combat owner belongs to competing encounters");});
        maneuvers=ImmutableLookupMap.copyOf(maneuvers);
        if(maneuvers.size()>SimulationCapacity.MAX_RESIDENTS)throw new IllegalArgumentException("maneuver bound");
        maneuvers.forEach((owner,value)->{if(!owner.equals(value.owner()))throw new IllegalArgumentException("foreign maneuver owner");});
        pending=ImmutableLookupMap.copyOf(pending);nextAttack=ImmutableLookupMap.copyOf(nextAttack);
        armaments=ImmutableLookupMap.copyOf(armaments);obstructions=ImmutableLookupMap.copyOf(obstructions);
        stances=ImmutableLookupMap.copyOf(stances);positions=ImmutableLookupMap.copyOf(positions);evasions=ImmutableLookupMap.copyOf(evasions);
        if(evasions.size()>SimulationCapacity.MAX_RESIDENTS)throw new IllegalArgumentException("safety episode bound");
        evasions.forEach((actor,value)->{if(!actor.equals(value.actor()))throw new IllegalArgumentException("foreign safety episode");});
        if(pending.size()>SimulationCapacity.MAX_RESIDENTS || nextAttack.size()>SimulationCapacity.MAX_RESIDENTS
                || armaments.size()>SimulationCapacity.MAX_RESIDENTS || obstructions.size()>SimulationCapacity.MAX_RESIDENTS
                || stances.size()>SimulationCapacity.MAX_RESIDENTS || positions.size()>SimulationCapacity.MAX_RESIDENTS
                || nextAttack.values().stream().anyMatch(t->t<0) || !armaments.keySet().containsAll(nextAttack.keySet()))throw new IllegalArgumentException("invalid combat state bounds/readiness");
        stances.forEach((id,value)->{if(!id.equals(value.observer().execution().activityOwnerId()))throw new IllegalArgumentException("foreign melee stance");});
        positions.forEach((id,value)->{if(!id.equals(value.execution().activityOwnerId()))throw new IllegalArgumentException("foreign melee position");});
        if(!armaments.keySet().containsAll(readiness.keySet()))throw new IllegalArgumentException("readiness lost its armed owner");
        var knownArmaments=armaments;
        pending.forEach((id,value)->{if(!id.equals(value.owner()) || !knownArmaments.containsKey(id))throw new IllegalArgumentException("foreign pending melee owner");});
        armaments.forEach((id,value)->{if(!id.equals(value.execution().activityOwnerId()))throw new IllegalArgumentException("foreign combat weapon owner");});
        obstructions.forEach((id,value)->{if(!id.equals(value.execution().activityOwnerId()))throw new IllegalArgumentException("foreign combat station owner");});
    }
    public static CombatState empty(){return new CombatState(Map.of(),Map.of(),Map.of(),Map.of(),Map.of(),Map.of(),Map.of());}
    public CombatState recalled(CombatOrder order,java.util.OptionalLong harm,long tick,MeleeDuelRules rules){
        var prior=exchanges.get(order.owner());
        var next=(prior!=null && prior.matches(order)?prior:MeleeExchange.first(order)).observed(harm,tick,rules);
        if(next.equals(prior))return this;
        return withExchange(order.owner(),next);
    }
    public CombatState withExchange(SubjectId owner,MeleeExchange exchange){
        return new CombatState(pending,nextAttack,armaments,obstructions,stances,positions,evasions,maneuvers,engagements,followups,readiness,
                ImmutableLookupMap.withEntry(exchanges,owner,exchange));
    }
    public boolean affords(SubjectId owner,long tick,int cost,MeleeRules rules){
        return CombatReadiness.available(readiness.get(owner),tick,rules.readiness())>=cost;
    }
    public CombatState spent(SubjectId owner,long tick,int cost,MeleeRules rules){
        return new CombatState(pending,nextAttack,armaments,obstructions,stances,positions,evasions,maneuvers,engagements,followups,
                ImmutableLookupMap.withEntry(readiness,owner,CombatReadiness.spend(readiness.get(owner),tick,cost,rules.readiness())),exchanges);
    }
    public boolean recovering(SubjectId owner,long tick){
        return !pending.containsKey(owner) && tick<nextAttack.getOrDefault(owner,0L);
    }
    public CombatState observed(Armament value){
        return new CombatState(pending,nextAttack,ImmutableLookupMap.withEntry(armaments,value.execution().activityOwnerId(),value),obstructions,stances,positions,evasions,maneuvers,engagements,followups,readiness,exchanges);}
    public CombatState prepared(MeleeStrike value){
        if(pending.containsKey(value.owner()))throw new IllegalArgumentException("combat owner already retains a native attempt");
        var armed=observed(new Armament(value.attacker().execution(),value.weapon(),value.profile()));
        return new CombatState(ImmutableLookupMap.withEntry(pending,value.owner(),value),nextAttack,armed.armaments,obstructions,stances,positions,
                evasions,maneuvers,engagements,
                ImmutableLookupMap.withoutEntry(followups,value.owner()),readiness,exchanges);
    }
    public CombatState completed(MeleeStrike value,long tick,MeleeRules rules){return completed(value,tick,rules,false);}
    public CombatState completed(MeleeStrike value,long tick,MeleeRules rules,boolean missed){
        return completed(value,tick,rules,missed?MeleeOutcome.MISS:MeleeOutcome.HIT);
    }
    public CombatState completed(MeleeStrike value,long tick,MeleeRules rules,MeleeOutcome outcome){
        boolean missed=outcome==MeleeOutcome.MISS;
        if(!value.equals(pending.get(value.owner())) || tick<value.preparedAt())throw new IllegalArgumentException("stale melee completion");
        boolean continueMiss=missed && value.attack()==MeleeAttack.LUNGE && tick>=value.impactAt(rules);
        var next=ImmutableLookupMap.withoutEntry(followups,value.owner());
        if(continueMiss)next=ImmutableLookupMap.withEntry(next,value.owner(),new MeleeFollowup(value.attacker(),value.target(),value.weapon(),
                value.targetIncarnation(),value.attemptOrdinal(),tick,Math.addExact(tick,rules.followup().windowTicks())));
        var priorExchange=exchanges.get(value.owner());
        var exchange=(priorExchange!=null && priorExchange.matches(value)?priorExchange:MeleeExchange.first(value))
                .completed(outcome,tick,rules.maneuvers().duel());
        return new CombatState(ImmutableLookupMap.withoutEntry(pending,value.owner()),
                ImmutableLookupMap.withEntry(nextAttack,value.owner(),Math.max(Math.addExact(tick,continueMiss?rules.followup().recoveryTicks():rules.techniques().require(value.attack()).recoveryTicks()),
                        Math.addExact(value.preparedAt(),value.profile().attackIntervalTicks()))),armaments,obstructions,stances,positions,evasions,maneuvers,engagements,next,readiness,ImmutableLookupMap.withEntry(exchanges,value.owner(),exchange));
    }
    public CombatState blocked(CombatOrder order,SurfaceAnchor station,long tick,MeleeRules rules){
        var prior=obstructions.get(order.owner());var points=new ArrayList<SurfaceAnchor>();
        if(prior!=null && prior.matches(order,tick,rules))points.addAll(prior.stations());
        if(!points.contains(station)){if(points.size()==8)points.removeFirst();points.add(station);}
        return new CombatState(pending,nextAttack,armaments,ImmutableLookupMap.withEntry(obstructions,order.owner(),
                new Obstruction(order.execution(),order.target(),order.targetPosition(),points,tick)),stances,positions,evasions,maneuvers,engagements,followups,readiness,exchanges);
    }
    public boolean rejected(CombatOrder order,SurfaceAnchor station,long tick,MeleeRules rules){
        var prior=obstructions.get(order.owner());return prior!=null && prior.matches(order,tick,rules) && prior.stations().contains(station);
    }
    public CombatState stance(MeleeStance value){
        var points=positions;var owner=value.observer().execution().activityOwnerId();var point=points.get(owner);
        if(value.holding() && point!=null){
            if(point.reason()!=MeleePosition.Reason.RESERVED)points=ImmutableLookupMap.withoutEntry(points,owner);
            else if(point.execution().equals(value.observer().execution()) && point.target().equals(value.target()))points=ImmutableLookupMap.withEntry(points,owner,point.renewed(value.atTick()));
        }
        return new CombatState(pending,nextAttack,armaments,obstructions,ImmutableLookupMap.withEntry(stances,owner,value),points,evasions,maneuvers,engagements,followups,readiness,exchanges);
    }
    public boolean holding(CombatOrder order,long tick,MeleeRules rules){var stance=stances.get(order.owner());return stance!=null && stance.holding() && stance.current(order,tick,rules);}
    public boolean deferred(CombatOrder order,long tick){var point=positions.get(order.owner());return point!=null && point.deferred(order,tick);}
    public CombatState positioned(CombatOrder order,Optional<SurfaceAnchor> station,MeleePosition.Reason reason,long tick,MeleeRules rules){
        var joined=enlisted(order,tick,rules);
        var prior=positions.get(order.owner());long since=prior!=null && prior.matches(order) && (prior.reason()==reason || prior.reason().unavailable() && reason.unavailable())?prior.sinceTick():tick;
        if(reason.unavailable() && tick-since>=rules.unreachableTicks())reason=MeleePosition.Reason.UNREACHABLE;
        var value=new MeleePosition(order.execution(),order.target(),order.targetPosition(),station,order.navigation().widthMicros(),order.navigation().heightMicros(),
                reason,since,tick,reason==MeleePosition.Reason.UNREACHABLE?Math.addExact(tick,rules.retryTicks()):0);
        return new CombatState(pending,nextAttack,armaments,obstructions,stances,ImmutableLookupMap.withEntry(positions,order.owner(),value),evasions,maneuvers,joined.engagements,followups,readiness,exchanges);
    }
    public CombatState releasePosition(SubjectId owner){
        var point=positions.get(owner);if(point==null || point.station().isEmpty())return this;
        return new CombatState(pending,nextAttack,armaments,obstructions,stances,ImmutableLookupMap.withoutEntry(positions,owner),evasions,maneuvers,engagements,followups,readiness,exchanges);
    }
    public CombatState withEvasion(Evasion value){return new CombatState(pending,nextAttack,armaments,obstructions,stances,positions,ImmutableLookupMap.withEntry(evasions,value.actor(),value),maneuvers,engagements,followups,readiness,exchanges);}
    public CombatState withoutEvasion(SubjectId actor){return new CombatState(pending,nextAttack,armaments,obstructions,stances,positions,ImmutableLookupMap.withoutEntry(evasions,actor),maneuvers,engagements,followups,readiness,exchanges);}
    public CombatState maneuver(MeleeManeuver value){return new CombatState(pending,nextAttack,armaments,obstructions,stances,positions,evasions,ImmutableLookupMap.withEntry(maneuvers,value.owner(),value),engagements,followups,readiness,exchanges);}
    public boolean maneuverOpen(SubjectId owner){var value=maneuvers.get(owner);return value!=null && value.open();}
    public boolean maneuverBusy(SubjectId owner,long tick,ManeuverRules rules){var value=maneuvers.get(owner);return value!=null && value.busy(tick,rules);}
    public boolean maneuverBlocksStrike(SubjectId owner,long tick,ManeuverRules rules){var value=maneuvers.get(owner);return value!=null && value.blocksStrike(tick,rules);}
    public CombatState withEngagement(CombatEngagement value){return new CombatState(pending,nextAttack,armaments,obstructions,stances,positions,evasions,maneuvers,
            ImmutableLookupMap.withEntry(engagements,value.key(),value),followups,readiness,exchanges);}
    public CombatState withoutEngagement(CombatEngagement.Key key){return new CombatState(pending,nextAttack,armaments,obstructions,stances,positions,evasions,maneuvers,ImmutableLookupMap.withoutEntry(engagements,key),followups,readiness,exchanges);}
    public CombatState enlisted(CombatOrder order,long tick,MeleeRules rules){
        var key=CombatEngagement.Key.of(order);var group=engagements.get(key);
        var next=this;
        if(group==null || !group.participants().containsKey(order.owner()))next=withoutParticipant(order.owner());
        group=next.engagements.get(key);
        return next.withEngagement(group==null?CombatEngagement.first(order,tick):group.admitted(order,tick));
    }
    /** Explicit withdrawal of this execution from all encounter plans; never retires its unsettled physical effects. */
    public CombatState withdrawn(io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionId execution){
        var next=this;
        for(var group:engagements.values()){
            var member=group.participants().get(execution.activityOwnerId());
            if(member!=null && member.execution().equals(execution))next=next.withoutParticipant(execution.activityOwnerId());
        }
        var followup=next.followups.get(execution.activityOwnerId());
        return followup!=null && followup.actor().execution().equals(execution)?next.withoutFollowup(execution.activityOwnerId()):next;
    }
    public CombatState withoutFollowup(SubjectId owner){
        return new CombatState(pending,nextAttack,armaments,obstructions,stances,positions,evasions,maneuvers,engagements,ImmutableLookupMap.withoutEntry(followups,owner),readiness,exchanges);
    }
    private CombatState withoutParticipant(SubjectId owner){
        var groups=new LinkedHashMap<>(engagements);boolean changed=false;
        for(var group:engagements.values())if(group.participants().containsKey(owner)){
            changed=true;var members=new LinkedHashMap<>(group.participants());members.remove(owner);
            if(members.isEmpty())groups.remove(group.key());
            else{CombatEngagementPlanner.promote(members);groups.put(group.key(),new CombatEngagement(group.key(),group.revision()+1,group.anchor(),group.axisX(),group.axisZ(),members,0,Optional.empty()));}
        }
        return changed?new CombatState(pending,nextAttack,armaments,obstructions,stances,positions,evasions,maneuvers,groups,followups,readiness,exchanges):this;
    }
    public CombatState retired(SubjectId owner){
        if(pending.containsKey(owner) || maneuvers.containsKey(owner) && maneuvers.get(owner).open())throw new IllegalArgumentException("cannot retire unsettled melee effects");
        return new CombatState(pending,ImmutableLookupMap.withoutEntry(nextAttack,owner),ImmutableLookupMap.withoutEntry(armaments,owner),ImmutableLookupMap.withoutEntry(obstructions,owner),ImmutableLookupMap.withoutEntry(stances,
        owner),ImmutableLookupMap.withoutEntry(positions,owner),evasions,ImmutableLookupMap.withoutEntry(maneuvers,owner),withoutParticipant(owner).engagements,
                ImmutableLookupMap.withoutEntry(followups,owner),ImmutableLookupMap.withoutEntry(readiness,owner),ImmutableLookupMap.withoutEntry(exchanges,owner));
    }
}
