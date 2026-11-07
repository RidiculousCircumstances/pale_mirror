package io.farfrontier.palemirror.frontier.v3.model.combat;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import java.util.*;
/** Bounded state of supply/shot obligations. Ammunition stock and physical bodies retain their common owners. */
public record RangedState(Map<SubjectId,RangedSupply> supplies,Map<SubjectId,RangedShot> pending,
        Map<SubjectId,Long> nextAttack,Map<SubjectId,RangedStance> stances,Map<SubjectId,RangedFollowup> followups) {
    public RangedState {
        followups=ImmutableLookupMap.copyOf(followups);supplies=ImmutableLookupMap.copyOf(supplies);pending=ImmutableLookupMap.copyOf(pending);
        nextAttack=ImmutableLookupMap.copyOf(nextAttack);stances=ImmutableLookupMap.copyOf(stances);
        if(supplies.size()>SimulationCapacity.MAX_RESIDENTS || pending.size()>SimulationCapacity.MAX_RESIDENTS
                || followups.size()>SimulationCapacity.MAX_RESIDENTS || nextAttack.size()>SimulationCapacity.MAX_RESIDENTS || stances.size()>SimulationCapacity.MAX_RESIDENTS
                || nextAttack.values().stream().anyMatch(t->t<0))throw new IllegalArgumentException("ranged state exceeds bounded owner population");
        supplies.forEach((id,v)->{if(!id.equals(v.owner()))throw new IllegalArgumentException("foreign ammunition owner");});
        followups.forEach((id,v)->{if(!id.equals(v.execution().activityOwnerId()))throw new IllegalArgumentException("foreign ranged followup");});
        var supply=supplies;
        pending.forEach((id,v)->{if(!id.equals(v.owner()) || supply.containsKey(id))throw new IllegalArgumentException("competing ranged obligations");});
        stances.forEach((id,v)->{if(!id.equals(v.observer().execution().activityOwnerId()))throw new IllegalArgumentException("foreign ranged stance");});
    }
    public static RangedState empty(){return new RangedState(Map.of(),Map.of(),Map.of(),Map.of(),Map.of());}
    public boolean unsettled(SubjectId owner){return pending.containsKey(owner) || Optional.ofNullable(supplies.get(owner)).flatMap(RangedSupply::pending).isPresent();}
    public RangedState supply(RangedSupply value){return new RangedState(ImmutableLookupMap.withEntry(supplies,value.owner(),value),pending,nextAttack,stances,followups);}
    public RangedState withoutSupply(SubjectId owner){return new RangedState(ImmutableLookupMap.withoutEntry(supplies,owner),pending,nextAttack,stances,followups);}
    public RangedState prepared(RangedShot shot){if(pending.containsKey(shot.owner()))throw new IllegalArgumentException("ranged owner already retains a shot");return new RangedState(supplies,ImmutableLookupMap.withEntry(pending,
            shot.owner(),shot),nextAttack,stances,followups);}
    public RangedState completed(RangedShot shot,long tick,RangedRules rules,boolean emitted){
        if(!shot.equals(pending.get(shot.owner())))throw new IllegalArgumentException("stale ranged outcome");
        return new RangedState(supplies,ImmutableLookupMap.withoutEntry(pending,shot.owner()),ImmutableLookupMap.withEntry(nextAttack,shot.owner(),Math.addExact(tick,rules.recoveryTicks())),stances,
                emitted?ImmutableLookupMap.withEntry(followups,shot.owner(),new RangedFollowup(shot.attacker().execution(),shot.weapon(),shot.target(),shot.origin(),shot.technique(),tick)):followups);
    }
    public RangedState observed(RangedStance value){return new RangedState(supplies,pending,nextAttack,ImmutableLookupMap.withEntry(stances,value.observer().execution().activityOwnerId(),value),followups);}
    public RangedState retired(SubjectId owner){if(unsettled(owner))throw new IllegalArgumentException("cannot retire unsettled ranged effects");return new RangedState(ImmutableLookupMap.withoutEntry(supplies,owner),pending,
            ImmutableLookupMap.withoutEntry(nextAttack,owner),ImmutableLookupMap.withoutEntry(stances,owner),ImmutableLookupMap.withoutEntry(followups,owner));}
}
