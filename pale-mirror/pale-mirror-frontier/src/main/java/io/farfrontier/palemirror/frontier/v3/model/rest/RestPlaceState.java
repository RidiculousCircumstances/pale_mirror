package io.farfrontier.palemirror.frontier.v3.model.rest;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import java.util.*;

/** Generic rest-place occupancy authority. Authored/native place evidence remains external. */
public record RestPlaceState(Map<SubjectId,RestUse> uses,Set<SettlementBeds.Id> unavailable){
    public RestPlaceState(Map<SubjectId,RestUse> uses){this(uses,Set.of());}
    public RestPlaceState {
        uses=ImmutableLookupMap.copyOf(uses);unavailable=Set.copyOf(unavailable);
        if(unavailable.size()>SimulationCapacity.MAX_RESIDENTS*2||uses.size()>SimulationCapacity.MAX_RESIDENTS||uses.values().stream().map(v->v.place().id()).distinct().count()!=uses.size())
            throw new IllegalArgumentException("competing or unbounded resting reservations");
        uses.forEach((actor,v)->{if(!actor.equals(v.execution().actorId()))throw new IllegalArgumentException("foreign resting actor");});
    }
    public static RestPlaceState empty(){return new RestPlaceState(Map.of());}
    public RestPlaceState with(RestUse use){return new RestPlaceState(ImmutableLookupMap.withEntry(uses,use.execution().actorId(),use),unavailable);}
    public RestPlaceState without(SubjectId actor){
        if(Optional.ofNullable(uses.get(actor)).flatMap(RestUse::pendingBody).isPresent())throw new IllegalArgumentException("resting interaction remains unsettled");
        return new RestPlaceState(ImmutableLookupMap.withoutEntry(uses,actor),unavailable);
    }
    public RestPlaceState availability(SettlementBeds.Id id,boolean available){var next=new HashSet<>(unavailable);if(available)next.remove(id);else next.add(id);return new RestPlaceState(uses,next);}
    public boolean free(SettlementBeds.Id place){return !unavailable.contains(place)&&uses.values().stream().noneMatch(v->v.place().id().equals(place));}
}
