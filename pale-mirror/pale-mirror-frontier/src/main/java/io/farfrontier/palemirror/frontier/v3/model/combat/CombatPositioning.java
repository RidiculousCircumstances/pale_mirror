package io.farfrontier.palemirror.frontier.v3.model.combat;
import io.farfrontier.palemirror.frontier.v3.model.*;
import java.util.*;
/** Closed mechanical strategies. Roles supply complete orders; this coordinator never inspects appointments. */
public final class CombatPositioning {
    public interface Strategy {
        CombatMethod method();
        CombatPositionChoice plan(FrontierWorldState s,CombatOrder o,KnownPedestrianRouteKnowledge k,List<CombatOrder> peers,long t,Set<SurfaceAnchor> failed);
        boolean legal(FrontierWorldState s,CombatOrder o,SurfaceAnchor p,long t);
        boolean holding(FrontierWorldState s,CombatOrder o,long t);
        boolean needsReplacement(FrontierWorldState s,CombatOrder o,long t);
        CombatState enlisted(CombatState s,CombatOrder o,long t,MeleeRules rules);
        CombatState reconcile(FrontierWorldState s,CombatState combat,ExactInventory inventory,CombatOrder o,long t,CombatCapabilities capabilities);
    }
    private static final Map<CombatMethod,Strategy> ENTRIES=register(List.of(new MeleePositioning(),new RangedPositions()));
    public static Map<CombatMethod,Strategy> register(List<Strategy> values){
        var result=new EnumMap<CombatMethod,Strategy>(CombatMethod.class);
        for(var v:values)if(result.put(v.method(),v)!=null)throw new IllegalArgumentException("duplicate tactical method");
        if(!result.keySet().equals(EnumSet.allOf(CombatMethod.class)))throw new IllegalArgumentException("missing tactical method");return Map.copyOf(result);
    }
    public static Strategy require(CombatOrder order){return Objects.requireNonNull(ENTRIES.get(order.method()),"undeclared combat method");}
    public static boolean holding(FrontierWorldState s,CombatOrder o,long t){return require(o).holding(s,o,t);}
    public static boolean legal(FrontierWorldState s,CombatOrder o,SurfaceAnchor p,long t){return require(o).legal(s,o,p,t);}
    public static boolean available(FrontierWorldState s,CombatOrder o,SurfaceAnchor p,long t){return MeleePositions.available(s,o,p,t);}
    public static boolean needsReplacement(FrontierWorldState s,CombatOrder o,long t){return require(o).needsReplacement(s,o,t);}
    public static void requireClaim(FrontierWorldState s,CombatOrder o,SurfaceAnchor p,long t){
        if(!legal(s,o,p,t)||!available(s,o,p,t))throw new IllegalArgumentException("foreign tactical reach or competing spatial claim");
    }
    public static CombatPositionChoice plan(FrontierWorldState s,CombatOrder o,KnownPedestrianRouteKnowledge k,List<CombatOrder> peers,long t,Set<SurfaceAnchor> failed){return require(o).plan(s,o,k,peers,t,failed);}
    public static CombatState enlisted(CombatState s,CombatOrder o,long t,MeleeRules rules){return require(o).enlisted(s,o,t,rules);}
    private CombatPositioning(){}
}
