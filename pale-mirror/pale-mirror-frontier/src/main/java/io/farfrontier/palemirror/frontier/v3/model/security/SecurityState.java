package io.farfrontier.palemirror.frontier.v3.model.security;
import io.farfrontier.palemirror.frontier.v3.model.combat.*;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorActuationId;
import java.util.*;
/** Security owns relations, contacts and response assignments; mechanical attempts belong to CombatState. */
public record SecurityState(Map<Relation, Offence> hostility, Map<ContactKey, Contact> contacts,
                            Map<SubjectId, Response> responses, long observationRevision, CombatAwareness awareness) {
    public static final int MAX_RELATIONS=16384, MAX_CONTACTS_PER_HOME=16;
    public record ContactKey(SubjectId home, CombatTarget target) {
        public ContactKey { Objects.requireNonNull(home); Objects.requireNonNull(target); }
    }
    public record Response(SubjectId duty, ContactKey contact, long assignedAt, CombatEngagement engagement) {
        public Response { Objects.requireNonNull(duty); Objects.requireNonNull(contact); Objects.requireNonNull(engagement);
            if (!contact.target().equals(engagement.target())) throw new IllegalArgumentException("response engagement names another target");
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
    public SecurityState allocated(SubjectId home, Map<SubjectId,Response> assigned, long tick, SecurityRules rules) {
        var next = new LinkedHashMap<>(responses); next.entrySet().removeIf(e -> e.getValue().contact().home().equals(home));
        next.putAll(assigned);
        var kept = new LinkedHashMap<>(contacts);
        kept.entrySet().removeIf(e -> e.getKey().home().equals(home) && !e.getValue().current(tick,rules.investigationTicks()));
        return new SecurityState(hostility,kept,next,observationRevision,awareness.pruned(home,tick,rules.investigationTicks(),rules.harmMemoryTicks()));
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
    public SecurityState {
        Objects.requireNonNull(awareness);
        hostility=ImmutableLookupMap.copyOf(hostility);contacts=ImmutableLookupMap.copyOf(contacts);
        responses=ImmutableLookupMap.copyOf(responses);
        if(hostility.size()>MAX_RELATIONS || contacts.size()>SimulationCapacity.MAX_SETTLEMENTS*MAX_CONTACTS_PER_HOME
                || responses.size()>SimulationCapacity.MAX_RESIDENTS || observationRevision<0)
            throw new IllegalArgumentException("unbounded security state");
        contacts.forEach((key,value)->{if(!key.equals(new ContactKey(value.home(),value.target())) || value.revision()>observationRevision)throw new IllegalArgumentException("foreign security contact key");});
        var sizes = new HashMap<SubjectId,Integer>();
        contacts.values().forEach(c -> sizes.merge(c.home(),1,Integer::sum));
        if(sizes.values().stream().anyMatch(n -> n>MAX_CONTACTS_PER_HOME))throw new IllegalArgumentException("too many threat contacts");
        for(var entry:responses.entrySet()) if(!entry.getKey().equals(entry.getValue().duty()) || !contacts.containsKey(entry.getValue().contact()))
            throw new IllegalArgumentException("response lost exact duty/contact declaration");
    }
    public static SecurityState empty(){return new SecurityState(Map.of(),Map.of(),Map.of(),0,CombatAwareness.empty());}
    public boolean hostile(SubjectId home, UUID player){return hostility.containsKey(new Relation(home,player));}
    public SecurityState offence(SubjectId home,UUID player,long tick){
        var key=new Relation(home,player);var prior=hostility.get(key);var next=new LinkedHashMap<>(hostility);
        next.put(key,new Offence(prior==null?tick:prior.firstTick(),tick,prior==null?1:Math.incrementExact(prior.incidents())));
        return new SecurityState(next,contacts,responses,observationRevision,awareness);
    }
    public SecurityState observed(Contact contact){
        var key=new ContactKey(contact.home(),contact.target());
        var prior=contacts.get(key);
        if(contact.revision()!=Math.incrementExact(observationRevision) || prior!=null && contact.atTick()<prior.atTick())
            throw new IllegalArgumentException("stale security contact");
        if(prior==null && contactsIn(contact.home()).size()>=MAX_CONTACTS_PER_HOME)
            throw new IllegalArgumentException("contact admission exceeds bounded threat register");
        return new SecurityState(hostility,ImmutableLookupMap.withEntry(contacts,key,contact),responses,contact.revision(),awareness);
    }

    public SecurityState seen(Contact contact, SubjectId observer, BlockPosition observerPosition) {
        var next=observed(contact);
        return new SecurityState(next.hostility,next.contacts,next.responses,next.observationRevision,
                next.awareness.seen(new CombatAwareness.Sight(new CombatAwareness.SightKey(contact.home(),observer,contact.target()),contact.position(),observerPosition,contact.atTick())));
    }
    public SecurityState harmed(SecurityEvent.Incident incident,long tick) {
        var next=incident.attacker().kind()==CombatTarget.Kind.PLAYER?offence(incident.home(),incident.attacker().entity(),tick):this;
        if(incident.visibleAttackerPosition().isPresent() && (next.contact(incident.home(),incident.attacker())!=null
                || next.contactsIn(incident.home()).size()<MAX_CONTACTS_PER_HOME))
            next=next.seen(new Contact(incident.home(),incident.attacker(),incident.visibleAttackerPosition().orElseThrow(),tick,
                    Math.incrementExact(next.observationRevision)),incident.victim().actorId(),incident.victimPosition());
        return new SecurityState(next.hostility,next.contacts,next.responses,next.observationRevision,
                next.awareness.harmed(new CombatAwareness.Harm(new CombatAwareness.HarmKey(incident.home(),incident.victim().actorId(),incident.attacker()),incident.victimPosition(),tick)));
    }

    public SecurityState contactEnded(ContactKey key) {
        var kept=new LinkedHashMap<>(responses);kept.entrySet().removeIf(e -> e.getValue().contact().equals(key));
        return new SecurityState(hostility,ImmutableLookupMap.withoutEntry(contacts,key),kept,observationRevision,awareness.ended(key.home(),key.target()));
    }
    public SecurityState retired(SubjectId duty) {
        return new SecurityState(hostility,contacts,ImmutableLookupMap.withoutEntry(responses,duty),observationRevision,awareness);
    }
}
