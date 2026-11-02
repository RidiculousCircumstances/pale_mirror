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
    public static boolean needsReplacement(CombatState state,CombatOrder order,long tick,MeleeRules rules){
        return order.station().filter(station->!state.rejected(order,station,tick,rules)
                && weapon(state,order).filter(profile->legal(order,profile,station)).isPresent()).isEmpty();
    }
    public static Optional<SurfaceAnchor> select(FrontierWorldState state,CombatOrder order,KnownPedestrianRouteKnowledge knowledge,
            List<CombatOrder> peers,long tick,Set<SurfaceAnchor> failed){
        var profile=weapon(state.combat(),order);if(profile.isEmpty())return Optional.empty();
        var weapon=profile.orElseThrow();var rules=state.bootstrap().ruleset().melee();
        var occupied=peers.stream().filter(peer->!peer.execution().equals(order.execution())).flatMap(peer->peer.station().stream()).collect(java.util.stream.Collectors.toSet());
        var retained=order.station().filter(station->!failed.contains(station) && !occupied.contains(station)
                && !state.combat().rejected(order,station,tick,state.bootstrap().ruleset().melee()) && legal(order,weapon,station) && knowledge.clearStation(station));
        if(retained.isPresent())return retained;
        int radius=Math.min(rules.maximumRadius(),Math.max(1,(int)Math.ceil(weapon.reach())));
        var stations=PedestrianInteractionStations.within(knowledge.geometry(),order.targetPosition(),radius,occupied).stream()
                .filter(station->!failed.contains(station) && !state.combat().rejected(order,station,tick,state.bootstrap().ruleset().melee()) && legal(order,weapon,station)
                        && knowledge.clearStation(station) && !FacilityPedestrianContacts.of(state.bootstrap()).noParking().contains(station.support())
                        && ServiceBoundaryComposition.turnoverPoint(state,station).isEmpty())
                .toList();
        return reachable(rank(order,weapon,stations,peers),rules.routeRequestLimit(),station->knowledge.plannedBetween(order.actorPosition(),station));
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
    public static List<SurfaceAnchor> rank(CombatOrder order,WeaponProfile weapon,List<SurfaceAnchor> candidates,List<CombatOrder> peers){
        var claimed=peers.stream().filter(peer->!peer.execution().equals(order.execution())).flatMap(peer->peer.station().stream()).collect(java.util.stream.Collectors.toSet());
        return candidates.stream().distinct().filter(station->legal(order,weapon,station) && !claimed.contains(station))
                .sorted(Comparator.comparingDouble((SurfaceAnchor station)->score(order,weapon,station,peers))
                        .thenComparingInt(SurfaceAnchor::x).thenComparingInt(SurfaceAnchor::z).thenComparingInt(SurfaceAnchor::y)).toList();
    }
    private static double score(CombatOrder order,WeaponProfile weapon,SurfaceAnchor station,List<CombatOrder> peers){
        double range=Math.hypot(station.x()-order.targetPosition().x(),station.z()-order.targetPosition().z());
        double preferred=Math.min(1.6,weapon.reach()*0.7),crowding=0;
        for(var peer:peers)if(!peer.execution().equals(order.execution()) && peer.target().equals(order.target())){
            var point=peer.station().orElse(peer.actorPosition());double dx=station.x()-point.x(),dz=station.z()-point.z();
            double spacing=(order.navigation().width()+peer.navigation().width())/2.0+0.4;
            crowding+=4.0*spacing*spacing/(1.0+dx*dx+dz*dz);
        }
        return crowding+2.0*Math.abs(range-preferred)+0.05*station.manhattanDistance(order.actorPosition());
    }
    private MeleePositions(){}
}
