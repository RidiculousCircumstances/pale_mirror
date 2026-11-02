package io.farfrontier.palemirror.frontier.v3.model.combat;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionId;
import java.util.*;
/** Sole durable mechanical combat owner; role orders and body/equipment title remain external. */
public record CombatState(Map<SubjectId,MeleeStrike> pending,Map<SubjectId,Long> nextAttack,
        Map<SubjectId,Armament> armaments,Map<SubjectId,Obstruction> obstructions) {
    public record Armament(ActorExecutionId execution,SubjectId weapon,WeaponProfile profile) {
        public Armament {Objects.requireNonNull(execution);Objects.requireNonNull(weapon);Objects.requireNonNull(profile);}
        public boolean matches(CombatOrder order){return execution.equals(order.execution()) && weapon.equals(order.weapon());}
    }
    public record Obstruction(ActorExecutionId execution,CombatTarget target,SurfaceAnchor targetPosition,List<SurfaceAnchor> stations,long atTick) {
        public Obstruction {
            Objects.requireNonNull(execution);Objects.requireNonNull(target);Objects.requireNonNull(targetPosition);stations=List.copyOf(stations);
            if(atTick<0 || stations.isEmpty() || stations.size()>8 || stations.stream().distinct().count()!=stations.size())throw new IllegalArgumentException("invalid rejected melee stations");
        }
        public boolean matches(CombatOrder order,long tick){return execution.equals(order.execution()) && target.equals(order.target())
                && targetPosition.equals(order.targetPosition()) && tick>=atTick && tick-atTick<=MeleeRules.initial().obstructionTicks();}
    }
    public CombatState {
        pending=ImmutableLookupMap.copyOf(pending);nextAttack=ImmutableLookupMap.copyOf(nextAttack);
        armaments=ImmutableLookupMap.copyOf(armaments);obstructions=ImmutableLookupMap.copyOf(obstructions);
        if(pending.size()>SimulationCapacity.MAX_RESIDENTS || nextAttack.size()>SimulationCapacity.MAX_RESIDENTS
                || armaments.size()>SimulationCapacity.MAX_RESIDENTS || obstructions.size()>SimulationCapacity.MAX_RESIDENTS
                || nextAttack.values().stream().anyMatch(t->t<0) || !armaments.keySet().containsAll(nextAttack.keySet()))throw new IllegalArgumentException("invalid combat state bounds/readiness");
        var knownArmaments=armaments;
        pending.forEach((id,value)->{if(!id.equals(value.owner()) || !knownArmaments.containsKey(id))throw new IllegalArgumentException("foreign pending melee owner");});
        armaments.forEach((id,value)->{if(!id.equals(value.execution().activityOwnerId()))throw new IllegalArgumentException("foreign combat weapon owner");});
        obstructions.forEach((id,value)->{if(!id.equals(value.execution().activityOwnerId()))throw new IllegalArgumentException("foreign combat station owner");});
    }
    public static CombatState empty(){return new CombatState(Map.of(),Map.of(),Map.of(),Map.of());}
    public CombatState observed(Armament value){return new CombatState(pending,nextAttack,ImmutableLookupMap.withEntry(armaments,value.execution().activityOwnerId(),value),obstructions);}
    public CombatState prepared(MeleeStrike value){
        if(pending.containsKey(value.owner()))throw new IllegalArgumentException("combat owner already retains a native attempt");
        var armed=observed(new Armament(value.attacker().execution(),value.weapon(),value.profile()));
        return new CombatState(ImmutableLookupMap.withEntry(pending,value.owner(),value),nextAttack,armed.armaments,obstructions);
    }
    public CombatState completed(MeleeStrike value,long tick){
        if(!value.equals(pending.get(value.owner())) || tick<value.preparedAt())throw new IllegalArgumentException("stale melee completion");
        return new CombatState(ImmutableLookupMap.withoutEntry(pending,value.owner()),
                ImmutableLookupMap.withEntry(nextAttack,value.owner(),Math.addExact(tick,value.profile().attackIntervalTicks())),armaments,obstructions);
    }
    public CombatState blocked(CombatOrder order,SurfaceAnchor station,long tick){
        var prior=obstructions.get(order.owner());var points=new ArrayList<SurfaceAnchor>();
        if(prior!=null && prior.matches(order,tick))points.addAll(prior.stations());
        if(!points.contains(station)){if(points.size()==8)points.removeFirst();points.add(station);}
        return new CombatState(pending,nextAttack,armaments,ImmutableLookupMap.withEntry(obstructions,order.owner(),
                new Obstruction(order.execution(),order.target(),order.targetPosition(),points,tick)));
    }
    public boolean rejected(CombatOrder order,SurfaceAnchor station,long tick){
        var prior=obstructions.get(order.owner());return prior!=null && prior.matches(order,tick) && prior.stations().contains(station);
    }
    public CombatState retired(SubjectId owner){
        if(pending.containsKey(owner))throw new IllegalArgumentException("cannot retire unsettled melee effects");
        return new CombatState(pending,ImmutableLookupMap.withoutEntry(nextAttack,owner),ImmutableLookupMap.withoutEntry(armaments,owner),ImmutableLookupMap.withoutEntry(obstructions,owner));
    }
}
