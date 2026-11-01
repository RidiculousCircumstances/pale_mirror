package io.farfrontier.palemirror.frontier.v3.model.security;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorActuationId;
import java.util.*;
/** Relations survive contact expiry. Contacts, attack intents and readiness each have finite lifetimes. */
public record SecurityState(Map<Relation, Offence> hostility, Map<SubjectId, Contact> contacts,
                            Map<SubjectId, Strike> pending, Map<SubjectId, Long> nextAttack) {
    public static final int MAX_RELATIONS=16384;
    public record Relation(SubjectId home, UUID player) { public Relation { Objects.requireNonNull(home); Objects.requireNonNull(player); } }
    public record Offence(long firstTick, long lastTick, long incidents) {
        public Offence { if(firstTick<0 || lastTick<firstTick || incidents<1)throw new IllegalArgumentException("invalid offence history"); }
    }
    public record Contact(SubjectId home, CombatTarget target, BlockPosition position, long atTick, long revision) {
        public Contact { Objects.requireNonNull(home);Objects.requireNonNull(target);Objects.requireNonNull(position);
            if(atTick<0 || revision<1)throw new IllegalArgumentException("invalid dated security contact"); }
        public boolean current(long tick, long lifetime) { return tick>=atTick && tick-atTick <= lifetime; }
    }
    public record Strike(SubjectId duty, long dutyRevision, ActorActuationId attacker, CombatTarget target,
                         SubjectId weapon, ItemCondition before, WeaponProfile profile, long targetIncarnation, long attemptOrdinal, long preparedAt) {
        public Strike { Objects.requireNonNull(duty);Objects.requireNonNull(attacker);Objects.requireNonNull(target);
            Objects.requireNonNull(weapon);Objects.requireNonNull(before);Objects.requireNonNull(profile);
            if(targetIncarnation<1 || dutyRevision<1 || attemptOrdinal<1 || preparedAt<0 || !before.usable() || before.remaining()<profile.wear())
                throw new IllegalArgumentException("invalid combat strike declaration"); }
    }
    public SecurityState {
        hostility=ImmutableLookupMap.copyOf(hostility);contacts=ImmutableLookupMap.copyOf(contacts);
        pending=ImmutableLookupMap.copyOf(pending);nextAttack=ImmutableLookupMap.copyOf(nextAttack);
        if(hostility.size()>MAX_RELATIONS || contacts.size()>SimulationCapacity.MAX_SETTLEMENTS || pending.size()>SimulationCapacity.MAX_RESIDENTS
                || nextAttack.size()>SimulationCapacity.MAX_RESIDENTS || nextAttack.values().stream().anyMatch(tick->tick<0))
            throw new IllegalArgumentException("unbounded security state");
        contacts.forEach((key,value)->{if(!key.equals(value.home()))throw new IllegalArgumentException("foreign security contact key");});
        pending.forEach((key,value)->{if(!key.equals(value.duty()))throw new IllegalArgumentException("foreign strike key");});
    }
    public static SecurityState empty(){return new SecurityState(Map.of(),Map.of(),Map.of(),Map.of());}
    public boolean hostile(SubjectId home, UUID player){return hostility.containsKey(new Relation(home,player));}
    public SecurityState offence(SubjectId home,UUID player,long tick){
        var key=new Relation(home,player);var prior=hostility.get(key);var next=new LinkedHashMap<>(hostility);
        next.put(key,new Offence(prior==null?tick:prior.firstTick(),tick,prior==null?1:Math.incrementExact(prior.incidents())));
        return new SecurityState(next,contacts,pending,nextAttack);
    }
    public SecurityState observed(Contact contact){
        var prior=contacts.get(contact.home());
        if(prior!=null && (contact.atTick()<prior.atTick() || contact.revision()!=prior.revision()+1)
                || prior==null && contact.revision()!=1)throw new IllegalArgumentException("stale security contact");
        return new SecurityState(hostility,ImmutableLookupMap.withEntry(contacts,contact.home(),contact),pending,nextAttack);
    }
    public SecurityState prepared(Strike strike){
        if(pending.containsKey(strike.duty()))throw new IllegalArgumentException("guard already retains a strike");
        return new SecurityState(hostility,contacts,ImmutableLookupMap.withEntry(pending,strike.duty(),strike),nextAttack);
    }
    public SecurityState retired(SubjectId duty) {
        if(pending.containsKey(duty))throw new IllegalArgumentException("cannot retire an unsettled combat owner");
        return new SecurityState(hostility,contacts,pending,ImmutableLookupMap.withoutEntry(nextAttack,duty));
    }
    public SecurityState completed(Strike strike,long tick){
        if(!strike.equals(pending.get(strike.duty())) || tick<strike.preparedAt())throw new IllegalArgumentException("stale strike completion");
        return new SecurityState(hostility,contacts,ImmutableLookupMap.withoutEntry(pending,strike.duty()),
                ImmutableLookupMap.withEntry(nextAttack,strike.duty(),Math.addExact(tick,strike.profile().attackIntervalTicks())));
    }
}
