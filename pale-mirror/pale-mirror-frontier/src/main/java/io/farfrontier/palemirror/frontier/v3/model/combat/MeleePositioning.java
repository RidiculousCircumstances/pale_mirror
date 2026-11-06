package io.farfrontier.palemirror.frontier.v3.model.combat;
import io.farfrontier.palemirror.frontier.v3.model.*;
import java.util.*;
final class MeleePositioning implements CombatPositioning.Strategy {
    public CombatMethod method(){return CombatMethod.MELEE;}
    public CombatPositionChoice plan(FrontierWorldState s,CombatOrder o,KnownPedestrianRouteKnowledge k,List<CombatOrder> peers,long t,Set<SurfaceAnchor> failed){return MeleePositions.plan(s,o,k,peers,t,failed);}
    public boolean legal(FrontierWorldState s,CombatOrder o,SurfaceAnchor p,long t){return MeleePositions.weapon(s.combat(),o).filter(w->MeleePositions.permitted(s,o,w,p,t)).isPresent();}
    public boolean holding(FrontierWorldState s,CombatOrder o,long t){return MeleePositions.holding(s,o,t);}
    public boolean needsReplacement(FrontierWorldState s,CombatOrder o,long t){return MeleePositions.needsReplacement(s.combat(),o,t,s.bootstrap().ruleset().melee());}
    public CombatState reconcile(FrontierWorldState s,CombatState combat,ExactInventory inventory,CombatOrder o,long t,CombatCapabilities capabilities){
        return CombatEngagementPlanner.reconcile(s,combat.enlisted(o,t,s.bootstrap().ruleset().melee()),inventory,CombatEngagement.Key.of(o),t,capabilities);
    }
    public CombatState enlisted(CombatState s,CombatOrder o,long t,MeleeRules rules){return s.enlisted(o,t,rules);}
}
