package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.supply.SupplyReservePlanner.*;
import java.util.*;

/** Patients supply current dressing demand; resident exposure supplies the first-aid buffer. */
final class ClinicalReserveSupply implements ReserveSupplyPort {
    public Key key(){return Key.CLINICAL;}
    public Facts read(FrontierWorldState state,SubjectId home){
        var rules=state.bootstrap().ruleset().clinical();
        if(!state.bootstrap().ruleset().goodsTrade().supplyPlanning().commodities().containsKey(rules.bandage()))
            return new Facts(Map.of(),List.of(),Map.of());
        var locals=state.humanPopulation().residentsIn(home).stream()
                .filter(r->state.actorLocations().get(r.id()).condition().status()==ActorLifeStatus.ALIVE).toList();
        long needs=locals.stream().map(r->state.humanPopulation().clinical().wounds().get(r.id())).filter(Objects::nonNull)
                .mapToLong(w->Math.ceilDiv(w.untreated(),rules.coverageUnits())).sum();
        long pockets=locals.stream().mapToLong(r->ActorConsumables.available(state,r.id(),home,rules.bandage())).sum();
        long buffer=Math.ceilDiv((long)locals.size()*rules.firstAidPermille(),1000);
        return new Facts(Map.of(rules.bandage(),new Need(Rate.ZERO,Math.max(0,needs+buffer-pockets))),List.of(),Map.of());
    }
}
