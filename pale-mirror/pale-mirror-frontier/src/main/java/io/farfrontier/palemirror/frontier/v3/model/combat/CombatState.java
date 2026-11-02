package io.farfrontier.palemirror.frontier.v3.model.combat;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionId;
import java.util.*;
/** Sole durable mechanical combat owner; role orders and body/equipment title remain external. */
public record CombatState(Map<SubjectId,MeleeStrike> pending,Map<SubjectId,Long> nextAttack,
        Map<SubjectId,Armament> armaments,Map<SubjectId,Obstruction> obstructions,
        Map<SubjectId,MeleeStance> stances,Map<SubjectId,MeleePosition> positions,Map<SubjectId,Evasion> evasions) {
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
    public CombatState {
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
        var knownArmaments=armaments;
        pending.forEach((id,value)->{if(!id.equals(value.owner()) || !knownArmaments.containsKey(id))throw new IllegalArgumentException("foreign pending melee owner");});
        armaments.forEach((id,value)->{if(!id.equals(value.execution().activityOwnerId()))throw new IllegalArgumentException("foreign combat weapon owner");});
        obstructions.forEach((id,value)->{if(!id.equals(value.execution().activityOwnerId()))throw new IllegalArgumentException("foreign combat station owner");});
    }
    public static CombatState empty(){return new CombatState(Map.of(),Map.of(),Map.of(),Map.of(),Map.of(),Map.of(),Map.of());}
    public CombatState observed(Armament value){return new CombatState(pending,nextAttack,ImmutableLookupMap.withEntry(armaments,value.execution().activityOwnerId(),value),obstructions,stances,positions,evasions);}
    public CombatState prepared(MeleeStrike value){
        if(pending.containsKey(value.owner()))throw new IllegalArgumentException("combat owner already retains a native attempt");
        var armed=observed(new Armament(value.attacker().execution(),value.weapon(),value.profile()));
        return new CombatState(ImmutableLookupMap.withEntry(pending,value.owner(),value),nextAttack,armed.armaments,obstructions,stances,positions,evasions);
    }
    public CombatState completed(MeleeStrike value,long tick){
        if(!value.equals(pending.get(value.owner())) || tick<value.preparedAt())throw new IllegalArgumentException("stale melee completion");
        return new CombatState(ImmutableLookupMap.withoutEntry(pending,value.owner()),
                ImmutableLookupMap.withEntry(nextAttack,value.owner(),Math.max(Math.addExact(tick,1),Math.addExact(value.preparedAt(),value.profile().attackIntervalTicks()))),armaments,obstructions,stances,positions,evasions);
    }
    public CombatState blocked(CombatOrder order,SurfaceAnchor station,long tick,MeleeRules rules){
        var prior=obstructions.get(order.owner());var points=new ArrayList<SurfaceAnchor>();
        if(prior!=null && prior.matches(order,tick,rules))points.addAll(prior.stations());
        if(!points.contains(station)){if(points.size()==8)points.removeFirst();points.add(station);}
        return new CombatState(pending,nextAttack,armaments,ImmutableLookupMap.withEntry(obstructions,order.owner(),
                new Obstruction(order.execution(),order.target(),order.targetPosition(),points,tick)),stances,positions,evasions);
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
        return new CombatState(pending,nextAttack,armaments,obstructions,ImmutableLookupMap.withEntry(stances,owner,value),points,evasions);
    }
    public boolean holding(CombatOrder order,long tick,MeleeRules rules){var stance=stances.get(order.owner());return stance!=null && stance.holding() && stance.current(order,tick,rules);}
    public boolean deferred(CombatOrder order,long tick){var point=positions.get(order.owner());return point!=null && point.deferred(order,tick);}
    public CombatState positioned(CombatOrder order,Optional<SurfaceAnchor> station,MeleePosition.Reason reason,long tick,MeleeRules rules){
        var prior=positions.get(order.owner());long since=prior!=null && prior.matches(order) && (prior.reason()==reason || prior.reason().unavailable() && reason.unavailable())?prior.sinceTick():tick;
        if(reason.unavailable() && tick-since>=rules.unreachableTicks())reason=MeleePosition.Reason.UNREACHABLE;
        var value=new MeleePosition(order.execution(),order.target(),order.targetPosition(),station,order.navigation().widthMicros(),order.navigation().heightMicros(),
                reason,since,tick,reason==MeleePosition.Reason.UNREACHABLE?Math.addExact(tick,rules.retryTicks()):0,MeleeTactics.sector(this,order,tick,rules));
        return new CombatState(pending,nextAttack,armaments,obstructions,stances,ImmutableLookupMap.withEntry(positions,order.owner(),value),evasions);
    }
    public CombatState releasePosition(SubjectId owner){
        var point=positions.get(owner);if(point==null || point.station().isEmpty())return this;
        return new CombatState(pending,nextAttack,armaments,obstructions,stances,ImmutableLookupMap.withoutEntry(positions,owner),evasions);
    }
    public CombatState withEvasion(Evasion value){return new CombatState(pending,nextAttack,armaments,obstructions,stances,positions,ImmutableLookupMap.withEntry(evasions,value.actor(),value));}
    public CombatState withoutEvasion(SubjectId actor){return new CombatState(pending,nextAttack,armaments,obstructions,stances,positions,ImmutableLookupMap.withoutEntry(evasions,actor));}
    public CombatState retired(SubjectId owner){
        if(pending.containsKey(owner))throw new IllegalArgumentException("cannot retire unsettled melee effects");
        return new CombatState(pending,ImmutableLookupMap.withoutEntry(nextAttack,owner),ImmutableLookupMap.withoutEntry(armaments,owner),ImmutableLookupMap.withoutEntry(obstructions,owner),ImmutableLookupMap.withoutEntry(stances,
        owner),ImmutableLookupMap.withoutEntry(positions,owner),evasions);
    }
}
