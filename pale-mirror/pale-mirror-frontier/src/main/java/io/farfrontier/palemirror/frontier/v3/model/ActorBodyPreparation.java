package io.farfrontier.palemirror.frontier.v3.model;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.*;
/** Explicit independent state owners settle elapsed COLD work before a new physical incarnation fences them. */
public final class ActorBodyPreparation {
    public enum Key { CLINICAL_RECOVERY }
    public interface Port {Key key();Set<FrontierWorldStateUpdate.Component> ownedComponents();Acknowledgement prepare(FrontierWorldState state,SubjectId actor,long tick);}
    public record Acknowledgement(FrontierWorldState basis,SubjectId actor,long tick,ActorCondition before,ActorCondition after,FrontierWorldStateUpdate changes){
        public Acknowledgement{Objects.requireNonNull(basis);Objects.requireNonNull(actor);Objects.requireNonNull(before);Objects.requireNonNull(after);Objects.requireNonNull(changes);}
        public static Acknowledgement unchanged(FrontierWorldState s,SubjectId actor,long tick){var condition=s.actorLocations().get(actor).condition();return new Acknowledgement(s,actor,tick,condition,condition,FrontierWorldStateUpdate.begin());}
    }
    private static final Map<Key,Port> PORTS=register(List.of(new io.farfrontier.palemirror.frontier.v3.model.clinical.ClinicalBodyPreparation()));
    public static Map<Key,Port> register(List<Port> ports){
        var registered=new EnumMap<Key,Port>(Key.class);for(var p:ports)if(registered.put(p.key(),p)!=null)throw new IllegalArgumentException("duplicate body preparation owner");
        if(!registered.keySet().equals(EnumSet.allOf(Key.class)))throw new IllegalArgumentException("missing body preparation owner");return Map.copyOf(registered);
    }
    public static FrontierWorldState prepare(FrontierWorldState state,SubjectId actor,long tick){
        if(tick<0)throw new IllegalArgumentException("negative body preparation instant");
        if(!ActorExecutionCoordinator.coldAvailable(state,actor))return state;
        var next=state;for(var key:Key.values()){
            var owner=PORTS.get(key);var receipt=Objects.requireNonNull(owner.prepare(next,actor,tick));var before=next.actorLocations().get(actor);
            if(receipt.basis()!=next||!receipt.actor().equals(actor)||receipt.tick()!=tick||!receipt.before().equals(before.condition())
                    ||receipt.after().status()!=ActorLifeStatus.ALIVE||receipt.after().health().compareTo(receipt.before().health())<0
                    ||!owner.ownedComponents().containsAll(receipt.changes().changedComponents())
                    ||receipt.changes().changedComponents().contains(FrontierWorldStateUpdate.Component.ACTOR_LOCATIONS))throw new IllegalArgumentException("body preparation has a foreign basis, health or component grant");
            if(receipt.changes().changedComponents().isEmpty()&&receipt.before().equals(receipt.after()))continue;
            next=next.withChanges(receipt.changes().actorLocations(ImmutableLookupMap.withEntry(next.actorLocations(),actor,before.withObserved(before.body(),receipt.after()))));
        }return next;
    }
    private ActorBodyPreparation(){}
}
