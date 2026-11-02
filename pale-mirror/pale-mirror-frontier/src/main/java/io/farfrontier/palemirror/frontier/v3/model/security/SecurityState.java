package io.farfrontier.palemirror.frontier.v3.model.security;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorActuationId;
import java.util.*;
/** Relations survive contact expiry. Contacts, attack intents and readiness each have finite lifetimes. */
public record SecurityState(Map<Relation, Offence> hostility, Map<ContactKey, Contact> contacts,
                            Map<SubjectId, Strike> pending, Map<SubjectId, Long> nextAttack,
                            Map<SubjectId, Response> responses, long observationRevision) {
    public static final int MAX_RELATIONS=16384, MAX_CONTACTS_PER_HOME=16;
    public record ContactKey(SubjectId home, CombatTarget target) {
        public ContactKey { Objects.requireNonNull(home); Objects.requireNonNull(target); }
    }
    public record Response(SubjectId duty, ContactKey contact, long assignedAt) {
        public Response { Objects.requireNonNull(duty); Objects.requireNonNull(contact);
            if (assignedAt < 0) throw new IllegalArgumentException("invalid response assignment date"); }
    }
    public Contact contact(SubjectId home, CombatTarget target) { return contacts.get(new ContactKey(home,target)); }
    private static final ImmutableInputView<Map<SubjectId,List<Contact>>> CONTACT_INDEX=new ImmutableInputView<>();
    public List<Contact> contactsIn(SubjectId home) {
        return CONTACT_INDEX.get(List.of(contacts),()->{
            var grouped=new HashMap<SubjectId,List<Contact>>();
            contacts.values().forEach(c -> grouped.computeIfAbsent(c.home(),key -> new ArrayList<>()).add(c));
            grouped.replaceAll((key,values)->values.stream().sorted(Comparator.comparing((Contact c)->c.target().kind().wireTag())
                    .thenComparing(c->c.target().entity()).thenComparing(c->c.target().dimension())).toList());
            return Map.copyOf(grouped);
        }).getOrDefault(home,List.of());
    }
    public Optional<Response> response(SubjectId duty) { return Optional.ofNullable(responses.get(duty)); }
    public SecurityState allocated(SubjectId home, Map<SubjectId,Response> assigned, long tick, long lifetime) {
        var next = new LinkedHashMap<>(responses); next.entrySet().removeIf(e -> e.getValue().contact().home().equals(home));
        next.putAll(assigned);
        var kept = new LinkedHashMap<>(contacts);
        kept.entrySet().removeIf(e -> e.getKey().home().equals(home) && !e.getValue().current(tick,lifetime));
        return new SecurityState(hostility,kept,pending,nextAttack,next,observationRevision);
    }
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
        pending=ImmutableLookupMap.copyOf(pending);nextAttack=ImmutableLookupMap.copyOf(nextAttack);responses=ImmutableLookupMap.copyOf(responses);
        if(hostility.size()>MAX_RELATIONS || contacts.size()>SimulationCapacity.MAX_SETTLEMENTS*MAX_CONTACTS_PER_HOME || pending.size()>SimulationCapacity.MAX_RESIDENTS
                || responses.size()>SimulationCapacity.MAX_RESIDENTS || observationRevision<0 || nextAttack.size()>SimulationCapacity.MAX_RESIDENTS || nextAttack.values().stream().anyMatch(tick->tick<0))
            throw new IllegalArgumentException("unbounded security state");
        contacts.forEach((key,value)->{if(!key.equals(new ContactKey(value.home(),value.target())) || value.revision()>observationRevision)throw new IllegalArgumentException("foreign security contact key");});
        var sizes = new HashMap<SubjectId,Integer>();
        contacts.values().forEach(c -> sizes.merge(c.home(),1,Integer::sum));
        if(sizes.values().stream().anyMatch(n -> n>MAX_CONTACTS_PER_HOME))throw new IllegalArgumentException("too many threat contacts");
        for(var entry:responses.entrySet()) if(!entry.getKey().equals(entry.getValue().duty()) || !contacts.containsKey(entry.getValue().contact()))
            throw new IllegalArgumentException("response lost exact duty/contact declaration");
        pending.forEach((key,value)->{if(!key.equals(value.duty()))throw new IllegalArgumentException("foreign strike key");});
    }
    public static SecurityState empty(){return new SecurityState(Map.of(),Map.of(),Map.of(),Map.of(),Map.of(),0);}
    public boolean hostile(SubjectId home, UUID player){return hostility.containsKey(new Relation(home,player));}
    public SecurityState offence(SubjectId home,UUID player,long tick){
        var key=new Relation(home,player);var prior=hostility.get(key);var next=new LinkedHashMap<>(hostility);
        next.put(key,new Offence(prior==null?tick:prior.firstTick(),tick,prior==null?1:Math.incrementExact(prior.incidents())));
        return new SecurityState(next,contacts,pending,nextAttack,responses,observationRevision);
    }
    public SecurityState observed(Contact contact){
        var key=new ContactKey(contact.home(),contact.target());
        var prior=contacts.get(key);
        if(contact.revision()!=Math.incrementExact(observationRevision) || prior!=null && contact.atTick()<prior.atTick())
            throw new IllegalArgumentException("stale security contact");
        if(prior==null && contactsIn(contact.home()).size()>=MAX_CONTACTS_PER_HOME)
            throw new IllegalArgumentException("contact admission exceeds bounded threat register");
        return new SecurityState(hostility,ImmutableLookupMap.withEntry(contacts,key,contact),pending,nextAttack,responses,contact.revision());
    }

    public SecurityState prepared(Strike strike){
        if(pending.containsKey(strike.duty()))throw new IllegalArgumentException("guard already retains a strike");
        return new SecurityState(hostility,contacts,ImmutableLookupMap.withEntry(pending,strike.duty(),strike),nextAttack,responses,observationRevision);
    }
    public SecurityState contactEnded(ContactKey key) {
        var kept=new LinkedHashMap<>(responses);kept.entrySet().removeIf(e -> e.getValue().contact().equals(key));
        return new SecurityState(hostility,ImmutableLookupMap.withoutEntry(contacts,key),pending,nextAttack,kept,observationRevision);
    }
    public SecurityState retired(SubjectId duty) {
        if(pending.containsKey(duty))throw new IllegalArgumentException("cannot retire an unsettled combat owner");
        return new SecurityState(hostility,contacts,pending,ImmutableLookupMap.withoutEntry(nextAttack,duty),ImmutableLookupMap.withoutEntry(responses,duty),observationRevision);
    }
    public SecurityState completed(Strike strike,long tick){
        if(!strike.equals(pending.get(strike.duty())) || tick<strike.preparedAt())throw new IllegalArgumentException("stale strike completion");
        return new SecurityState(hostility,contacts,ImmutableLookupMap.withoutEntry(pending,strike.duty()),
                ImmutableLookupMap.withEntry(nextAttack,strike.duty(),Math.addExact(tick,strike.profile().attackIntervalTicks())),responses,observationRevision);
    }
}
