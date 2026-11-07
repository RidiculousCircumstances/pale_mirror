package io.farfrontier.palemirror.frontier.v3.model.combat;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.navigation.*;
import java.util.*;
/** Bounded firing/retreat stations over shared multi-level geometry; never drives an actuator. */
final class RangedPositions implements CombatPositioning.Strategy {
    public CombatMethod method(){return CombatMethod.CROSSBOW;}
    public static double distance(SurfaceAnchor a,SurfaceAnchor b){return Math.sqrt(Math.pow((long)a.x()-b.x(),2)+Math.pow((long)a.y()-b.y(),2)+Math.pow((long)a.z()-b.z(),2));}
    public boolean legal(FrontierWorldState s,CombatOrder o,SurfaceAnchor p,long t){
        var r=s.bootstrap().ruleset().ranged();double d=distance(p,o.targetPosition());
        return o.engagement().contains(p) && d>=r.minimumRange() && d<=r.maximumRange();
    }
    public boolean holding(FrontierWorldState s,CombatOrder o,long t){
        var sight=s.combat().ranged().stances().get(o.owner());var r=s.bootstrap().ruleset().ranged();
        return !RangedTactics.relocate(s.combat().ranged(),o,t) && sight!=null && sight.current(o,t,r) && sight.visible() && sight.clearShot() && legal(s,o,o.actorPosition(),t)
                && ActorBodyAuthority.retainsPhysicalCustody(s,o.execution().actorId()) && ActorBodyAuthority.current(s,o.execution().actorId()).equals(sight.observer().body());
    }
    public boolean needsReplacement(FrontierWorldState s,CombatOrder o,long t){
        var sight=s.combat().ranged().stances().get(o.owner());var r=s.bootstrap().ruleset().ranged();
        var relocation=RangedTactics.followup(s.combat().ranged(),o,t).filter(v->RangedTactics.relocate(s.combat().ranged(),o,t));
        boolean needsRelocation=relocation.filter(v->o.station().filter(p->RangedTactics.relocationStation(v,o,p)).isEmpty()).isPresent();
        return needsRelocation || o.station().filter(p->legal(s,o,p,t) && !s.combat().rejected(o,p,t,s.bootstrap().ruleset().melee())).isEmpty()
                || sight!=null && sight.current(o,t,r) && (!sight.visible()||!sight.clearShot()) && o.station().filter(p->p.equals(o.actorPosition())).isPresent();
    }
    public CombatState reconcile(FrontierWorldState s,CombatState combat,ExactInventory inventory,CombatOrder o,long t,CombatCapabilities capabilities){return combat;}
    public CombatState enlisted(CombatState s,CombatOrder o,long t,MeleeRules rules){return s;}
    public CombatPositionChoice plan(FrontierWorldState s,CombatOrder o,KnownPedestrianRouteKnowledge k,List<CombatOrder> peers,long t,Set<SurfaceAnchor> failed){
        var r=s.bootstrap().ruleset().ranged();var candidates=new LinkedHashSet<SurfaceAnchor>();
        if(holding(s,o,t))return new CombatPositionChoice(Optional.of(o.actorPosition()),MeleePosition.Reason.RESERVED);
        // Three rings and sixteen bearings bound work independently of world size.
        double heading=Math.atan2(o.actorPosition().z()-o.targetPosition().z(),o.actorPosition().x()-o.targetPosition().x());
        boolean unknown=false;var relocation=RangedTactics.followup(s.combat().ranged(),o,t).filter(v->RangedTactics.relocate(s.combat().ranged(),o,t));
        int preferred=RangedTactics.preferredRange(o,r);
        int outer=relocation.isPresent()?Math.max(preferred+3,(int)Math.ceil(distance(o.actorPosition(),o.targetPosition())+3)):preferred+3;
        for(int radius:new int[]{r.minimumRange()+1,preferred,Math.min(r.maximumRange()-1,outer)})for(int i=0;i<16;i++){
            double angle=heading+i*Math.PI/8;int x=o.targetPosition().x()+(int)Math.round(Math.cos(angle)*radius),z=o.targetPosition().z()+(int)Math.round(Math.sin(angle)*radius);
            var levels=k.surfacesAt(x,z);if(levels.isEmpty())unknown=true;
            for(var p:levels)if(legal(s,o,p,t)&&k.clearStation(p)&&!failed.contains(p)&&!p.equals(o.actorPosition())
                    && !s.combat().rejected(o,p,t,s.bootstrap().ruleset().melee()) && CombatPositioning.available(s,o,p,t)
                    && relocation.map(v->RangedTactics.relocationStation(v,o,p)).orElse(true)
                    && !FacilityPedestrianContacts.of(s.bootstrap()).noParking().contains(p.support())
                    && ServiceBoundaryComposition.turnoverPoint(s,p).isEmpty())candidates.add(p);
        }
        var ranked=candidates.stream().sorted(Comparator.comparingInt(k.geometry()::traversalPenalty)
                .thenComparingDouble(p->score(o,p,peers,r)).thenComparingInt(SurfaceAnchor::x).thenComparingInt(SurfaceAnchor::z).thenComparingInt(SurfaceAnchor::y)).toList();
        try{var station=MeleePositions.reachable(ranked,s.bootstrap().ruleset().melee().routeRequestLimit(),p->k.plannedBetween(o.actorPosition(),p));
            if(station.isPresent())return new CombatPositionChoice(station,MeleePosition.Reason.RESERVED);
            if(unknown){try{k.plannedBetween(o.actorPosition(),o.targetPosition());}catch(KnownPedestrianNavigation.RouteUnavailable waiting){}return new CombatPositionChoice(Optional.empty(),MeleePosition.Reason.ROUTE_PENDING);}
            return new CombatPositionChoice(Optional.empty(),MeleePosition.Reason.NO_PATH);
        }catch(KnownPedestrianNavigation.RouteUnavailable waiting){if(waiting.status()==PedestrianRouteResult.Status.NO_PATH)throw waiting;return new CombatPositionChoice(Optional.empty(),MeleePosition.Reason.ROUTE_PENDING);}
    }
    private static double score(CombatOrder o,SurfaceAnchor p,List<CombatOrder> peers,RangedRules r){
        double cost=distance(p,o.actorPosition())+2*Math.abs(distance(p,o.targetPosition())-RangedTactics.preferredRange(o,r));
        for(var peer:peers)if(!peer.owner().equals(o.owner())&&peer.party().equals(o.party())&&peer.target().equals(o.target())){
            var ally=peer.station().orElse(peer.actorPosition());double gap=distance(p,ally);if(gap<3)cost+=20*(3-gap);
            // Preserve a friendly front while favoring an offset line of fire instead of shooting through sword fighters.
            double vx=o.targetPosition().x()-p.x(),vz=o.targetPosition().z()-p.z();double norm=vx*vx+vz*vz;
            double along=((ally.x()-p.x())*vx+(ally.z()-p.z())*vz)/Math.max(1,norm);
            double across=Math.abs((ally.x()-p.x())*vz-(ally.z()-p.z())*vx)/Math.sqrt(Math.max(1,norm));
            if(along>0&&along<1&&across<2)cost+=(30+o.style().retreatWeight()/4.0)*(2-across);
            if(peer.method()==CombatMethod.MELEE){double ax=ally.x()-o.targetPosition().x(),az=ally.z()-o.targetPosition().z();if((p.x()-o.targetPosition().x())*ax+(p.z()-o.targetPosition().z())*az<0)cost+=12;}
        }
        return cost;
    }
}
