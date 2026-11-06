package io.farfrontier.palemirror.frontier.v3.model;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.combat.CombatMethod;
import io.farfrontier.palemirror.frontier.v3.model.supply.SupplyReservePlanner.*;
import java.util.*;
final class AmmunitionReserveSupply implements ReserveSupplyPort {
    public Key key(){return Key.AMMUNITION;}
    public Facts read(FrontierWorldState s,SubjectId home){
        int total=SettlementWorkPolicy.permissions(s,home).workers(ResidentWorkKind.GUARD).size();
        int wanted=GuardEquipment.rangedTarget(s,home,total);if(wanted==0)return new Facts(Map.of(),List.of(),Map.of());
        long carried=GuardEquipment.in(s,home).stream().filter(d->d.method()==CombatMethod.CROSSBOW)
                .mapToLong(d->io.farfrontier.palemirror.frontier.v3.model.combat.Ammunition.available(s,d.execution().actorId(),home,GuardAmmunition.ARROW)).sum();
        long reserve=Math.max(0,(long)wanted*s.bootstrap().ruleset().ranged().reservePerGuard()-carried);
        return new Facts(Map.of(GuardAmmunition.ARROW,new Need(Rate.ZERO,reserve)),List.of(),Map.of());
    }
}
