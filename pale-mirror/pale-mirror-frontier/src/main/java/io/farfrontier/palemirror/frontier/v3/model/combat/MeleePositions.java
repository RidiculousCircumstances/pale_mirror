package io.farfrontier.palemirror.frontier.v3.model.combat;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.navigation.*;
import java.util.*;
/** Bounded melee station selection over common geometry/navigation. No role or local pathfinder. */
public final class MeleePositions {
    public static Optional<WeaponProfile> weapon(CombatState state,CombatOrder order){
        return Optional.ofNullable(state.armaments().get(order.owner())).filter(arm->arm.matches(order)).map(CombatState.Armament::profile);
    }
    public static boolean legal(CombatOrder order,WeaponProfile weapon,SurfaceAnchor station){
        long dx=(long)station.x()-order.targetPosition().x(),dy=(long)station.y()-order.targetPosition().y(),dz=(long)station.z()-order.targetPosition().z();
        return order.engagement().contains(station) && (dx!=0 || dz!=0) && dx*dx+dy*dy+dz*dz<=weapon.reach()*weapon.reach();
    }
    public static boolean permitted(FrontierWorldState state,CombatOrder order,WeaponProfile weapon,SurfaceAnchor station,long tick){
        var rules=state.bootstrap().ruleset().melee();var sector=MeleeTactics.sector(state.combat(),order,tick,rules);
        return permitted(order,weapon,station,CombatApproach.arc(order,sector,rules.engagements()),rules.engagements());
    }
    private static boolean permitted(CombatOrder order,WeaponProfile weapon,SurfaceAnchor station,boolean outer,EngagementRules rules){
        if(!outer)return legal(order,weapon,station);
        double distance=CombatEngagement.distance(station,order.targetPosition());
        return order.engagement().contains(station) && distance>=weapon.reach()+0.5 && distance<=rules.outerRadius()+1;
    }
    public static boolean needsReplacement(CombatState state,CombatOrder order,long tick,MeleeRules rules){
        return order.station().filter(station->!state.rejected(order,station,tick,rules)
                && weapon(state,order).filter(profile->{
                    var sector=MeleeTactics.sector(state,order,tick,rules);
                    if(CombatApproach.arc(order,sector,rules.engagements())){
                        double distance=CombatEngagement.distance(station,order.targetPosition());
                        return order.engagement().contains(station) && distance>=profile.reach()+0.5 && distance<=rules.engagements().outerRadius()+1
                                && station.manhattanDistance(order.actorPosition())>1;
                    }
                    return legal(order,profile,station);
                }).isPresent()).isEmpty();
    }
    public static boolean holding(FrontierWorldState state,CombatOrder order,long tick){
        var stance=state.combat().stances().get(order.owner());
        return stance!=null && state.combat().holding(order,tick,state.bootstrap().ruleset().melee())
                && ActorBodyAuthority.retainsPhysicalCustody(state,order.execution().actorId())
                && ActorBodyAuthority.current(state,order.execution().actorId()).equals(stance.observer().body());
    }
    public record Choice(Optional<SurfaceAnchor> station,MeleePosition.Reason reason){
        public CombatEvent.PositionPlanned event(CombatOrder order){return new CombatEvent.PositionPlanned(order.execution(),order.target(),order.targetPosition(),station,reason);}
    }
    public static void requireClaim(FrontierWorldState state,CombatOrder order,SurfaceAnchor station,long tick){
        if(weapon(state.combat(),order).filter(profile->permitted(state,order,profile,station,tick)).isEmpty() || !available(state,order,station,tick))
            throw new IllegalArgumentException("melee position has foreign reach or a competing spatial claim");
    }
    public static boolean available(FrontierWorldState state,CombatOrder order,SurfaceAnchor station,long tick){
        var rules=state.bootstrap().ruleset().melee();
        return MeleeManeuvers.available(state,order,station) && state.combat().positions().values().stream().filter(point->!point.execution().equals(order.execution()) && point.active(tick,rules)
                && point.execution().equals(state.actorExecutions().current(point.execution().activityKind()).get(point.execution().actorId())))
                .noneMatch(point->point.overlaps(station,order.navigation().widthMicros(),order.navigation().heightMicros()));
    }
    public static Optional<SurfaceAnchor> select(FrontierWorldState state,CombatOrder order,KnownPedestrianRouteKnowledge knowledge,
            List<CombatOrder> peers,long tick,Set<SurfaceAnchor> failed){return plan(state,order,knowledge,peers,tick,failed).station();}
    public static Choice plan(FrontierWorldState state,CombatOrder order,KnownPedestrianRouteKnowledge knowledge,
            List<CombatOrder> peers,long tick,Set<SurfaceAnchor> failed){
        var profile=weapon(state.combat(),order);if(profile.isEmpty())return new Choice(Optional.empty(),MeleePosition.Reason.ROUTE_PENDING);
        var weapon=profile.orElseThrow();var rules=state.bootstrap().ruleset().melee();
        var activePeers=peers.stream().filter(peer->peer.party().equals(order.party()) && peer.target().equals(order.target()))
                .filter(peer->{var point=state.combat().positions().get(peer.owner());return point!=null && point.active(tick,rules);}).toList();
        var retained=order.station().filter(station->!failed.contains(station) && available(state,order,station,tick)
                && !state.combat().rejected(order,station,tick,rules) && permitted(state,order,weapon,station,tick)
                && !needsReplacement(state.combat(),order,tick,rules) && knowledge.clearStation(station));
        if(retained.isPresent())try{knowledge.plannedBetween(order.actorPosition(),retained.orElseThrow());return new Choice(retained,MeleePosition.Reason.RESERVED);}
        catch(KnownPedestrianNavigation.RouteUnavailable waiting){
            if(waiting.status()!=PedestrianRouteResult.Status.NO_PATH)return new Choice(Optional.empty(),MeleePosition.Reason.ROUTE_PENDING);
        }
        var sector=MeleeTactics.sector(state.combat(),order,tick,rules);
        boolean arc=CombatApproach.arc(order,sector,rules.engagements());
        int radius=arc?rules.engagements().outerRadius()+1:Math.min(rules.maximumRadius(),Math.max(1,(int)Math.ceil(weapon.reach())));
        var physical=PedestrianInteractionStations.within(knowledge.geometry(),order.targetPosition(),radius,Set.of()).stream()
                .filter(station->!failed.contains(station) && !state.combat().rejected(order,station,tick,rules) && permitted(state,order,weapon,station,tick)
                        && knowledge.clearStation(station) && !FacilityPedestrianContacts.of(state.bootstrap()).noParking().contains(station.support())
                        && ServiceBoundaryComposition.turnoverPoint(state,station).isEmpty()).toList();
        boolean missing=knowledgeMissing(knowledge.geometry(),order,weapon,radius,arc,rules.engagements());
        if(physical.isEmpty())return missing?pendingGeometry(knowledge,order):new Choice(Optional.empty(),MeleePosition.Reason.NO_POSITION);
        var candidates=physical.stream().filter(station->available(state,order,station,tick)).toList();
        if(candidates.isEmpty())return new Choice(Optional.empty(),MeleePosition.Reason.SPACE_OCCUPIED);
        var ranked=arc?candidates.stream().sorted(Comparator.comparingDouble((SurfaceAnchor station)->
                CombatApproach.score(order,sector,station,rules.engagements())+crowding(order,station,activePeers,rules))
                .thenComparingInt(SurfaceAnchor::x).thenComparingInt(SurfaceAnchor::z).thenComparingInt(SurfaceAnchor::y)).toList()
                :rank(order,weapon,candidates,activePeers,sector,rules);
        try{var selected=reachable(ranked,rules.routeRequestLimit(),station->knowledge.plannedBetween(order.actorPosition(),station));
            if(selected.isEmpty() && missing)return pendingGeometry(knowledge,order);
            return new Choice(selected,selected.isPresent()?MeleePosition.Reason.RESERVED:MeleePosition.Reason.NO_PATH);
        }catch(KnownPedestrianNavigation.RouteUnavailable pending){
            if(pending.status()==PedestrianRouteResult.Status.NO_PATH)throw pending;
            return new Choice(Optional.empty(),MeleePosition.Reason.ROUTE_PENDING);
        }
    }
    /** Missing columns/clearance cannot establish that every attack station is impossible. */
    public static boolean knowledgeMissing(PedestrianRouteGeometry geometry,CombatOrder order,WeaponProfile weapon,int radius,boolean outer,EngagementRules rules){
        for(int dx=-radius;dx<=radius;dx++)for(int dz=-radius;dz<=radius;dz++){
            if(Math.abs(dx)+Math.abs(dz)==0 || Math.abs(dx)+Math.abs(dz)>radius)continue;
            var levels=geometry.surfacesAt(order.targetPosition().x()+dx,order.targetPosition().z()+dz);
            if(levels.isEmpty())return true;
            if(levels.stream().anyMatch(point->permitted(order,weapon,point,outer,rules) && !geometry.clearanceKnown(point)))return true;
        }
        return false;
    }
    private static Choice pendingGeometry(KnownPedestrianRouteKnowledge knowledge,CombatOrder order){
        // Request ordinary knowledge along the actual observed target, never invent a floor for a missing column.
        try{knowledge.plannedBetween(order.actorPosition(),order.targetPosition());}
        catch(KnownPedestrianNavigation.RouteUnavailable pending){ /* Shared planner retains the dependency. */ }
        return new Choice(Optional.empty(),MeleePosition.Reason.ROUTE_PENDING);
    }
    /** Completed negative answers do not consume the outstanding-query budget or hide later candidates. */
    public static Optional<SurfaceAnchor> reachable(List<SurfaceAnchor> ranked,int requestLimit,java.util.function.Consumer<SurfaceAnchor> requireRoute){
        if(requestLimit<1)throw new IllegalArgumentException("invalid melee route request budget");
        KnownPedestrianNavigation.RouteUnavailable pending=null;int requests=0;
        for(var station:ranked)try{requireRoute.accept(station);return Optional.of(station);}
        catch(KnownPedestrianNavigation.RouteUnavailable unavailable){
            if(unavailable.status()==PedestrianRouteResult.Status.NO_PATH)continue;
            if(pending==null)pending=unavailable;
            if(++requests>=requestLimit)throw pending;
        }
        if(pending!=null)throw pending;return Optional.empty();
    }
    /** Pure ranking API: the caller supplies geometry candidates, never a fabricated route. */
    public static List<SurfaceAnchor> rank(CombatOrder order,WeaponProfile weapon,List<SurfaceAnchor> candidates,
            List<CombatOrder> peers,MeleeSector sector,MeleeRules rules){
        var claimed=peers.stream().filter(peer->!peer.execution().equals(order.execution())).flatMap(peer->peer.station().stream()).collect(java.util.stream.Collectors.toSet());
        return candidates.stream().distinct().filter(station->legal(order,weapon,station) && !claimed.contains(station))
                .sorted(Comparator.comparingDouble((SurfaceAnchor station)->score(order,weapon,station,peers,rules)+rules.sectorWeightPermille()/1000.0*(1.0-sector.alignment(station,order.targetPosition())))
                        .thenComparingInt(SurfaceAnchor::x).thenComparingInt(SurfaceAnchor::z).thenComparingInt(SurfaceAnchor::y)).toList();
    }
    private static double score(CombatOrder order,WeaponProfile weapon,SurfaceAnchor station,List<CombatOrder> peers,MeleeRules rules){
        double range=Math.hypot(station.x()-order.targetPosition().x(),station.z()-order.targetPosition().z());
        double preferred=Math.min(rules.engagements().attackDistanceMicros()/1_000_000.0,weapon.reach()*0.75);
        return crowding(order,station,peers,rules)+2.0*Math.abs(range-preferred)+0.05*station.manhattanDistance(order.actorPosition());
    }
    private static double crowding(CombatOrder order,SurfaceAnchor station,List<CombatOrder> peers,MeleeRules rules){
        double crowding=0;
        for(var peer:peers)if(!peer.execution().equals(order.execution()) && peer.party().equals(order.party()) && peer.target().equals(order.target())){
            var point=peer.station().orElse(peer.actorPosition());double dx=station.x()-point.x(),dz=station.z()-point.z();
            double spacing=(order.navigation().width()+peer.navigation().width())/2.0+rules.engagements().spacingMicros()/1_000_000.0;
            crowding+=4.0*spacing*spacing/(1.0+dx*dx+dz*dz);
        }
        return crowding;
    }
    private MeleePositions(){}
}
