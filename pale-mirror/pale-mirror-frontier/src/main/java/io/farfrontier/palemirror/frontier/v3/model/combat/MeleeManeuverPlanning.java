package io.farfrontier.palemirror.frontier.v3.model.combat;

import io.farfrontier.palemirror.frontier.v3.model.*;
import java.util.Optional;

/** One positive destination assessment for admission and reversible-attack withdrawal. */
public final class MeleeManeuverPlanning {
    public record Candidate(MeleeManeuver.Kind kind,SurfaceAnchor station) { }
    public record Assessment(Optional<Candidate> candidate,String detail) { }
    public static Assessment assess(FrontierWorldState state,CombatOrder order,MeleeManeuver.Kind preferred,
            MeleeManeuvers.Facts facts,long tick){
        var capability=CombatComposition.CAPABILITIES.require(order.execution());
        if(!capability.maneuvers().contains(preferred))return new Assessment(Optional.empty(),"maneuver not permitted by role");
        var knowledge=capability.maneuverKnowledge(state,order.execution());
        var result=MeleeManeuvers.destination(state,order,preferred,knowledge,tick,facts);
        if(result.station().isPresent())return new Assessment(Optional.of(new Candidate(preferred,result.station().orElseThrow())),result.detail());
        if(preferred.lateral()){
            var other=preferred.opposite();
            if(capability.maneuvers().contains(other)){
                var alternative=MeleeManeuvers.destination(state,order,other,knowledge,tick,facts);
                if(alternative.station().isPresent())return new Assessment(Optional.of(new Candidate(other,alternative.station().orElseThrow())),alternative.detail());
                return new Assessment(Optional.empty(),preferred+":"+result.detail()+";"+other+":"+alternative.detail());
            }
        }
        return new Assessment(Optional.empty(),preferred+":"+result.detail());
    }
    private MeleeManeuverPlanning() { }
}
