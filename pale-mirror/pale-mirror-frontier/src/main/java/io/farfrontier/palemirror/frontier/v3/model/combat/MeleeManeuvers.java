package io.farfrontier.palemirror.frontier.v3.model.combat;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.navigation.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorActuationId;
import java.util.*;
/** Shared tactical selection. Roles provide permission/knowledge; navigation proves traversability. */
public final class MeleeManeuvers {
    public record Facts(double distance,double separatingSpeed){public Facts{if(!Double.isFinite(distance)||distance<0||!Double.isFinite(separatingSpeed))throw new IllegalArgumentException("invalid maneuver observations");}}
    public static Optional<MeleeManeuver.Kind> preference(FrontierWorldState state,CombatOrder order,Facts facts,long tick,CombatCapability capability){
        var rules=state.bootstrap().ruleset().melee().maneuvers();var prior=state.combat().maneuvers().get(order.owner());
        if(state.combat().pending().containsKey(order.owner()) || prior!=null && prior.busy(tick,rules))return Optional.empty();
        var harm=capability.harmedAt(state,order);
        boolean hurt=harm.isPresent() && tick>=harm.getAsLong() && tick-harm.getAsLong()<=rules.harmLifetime();
        var kind=(hurt && facts.distance()<=rules.hurtGapMicros()/1_000_000.0 || facts.distance()<rules.crowdedGapMicros()/1_000_000.0)?MeleeManeuver.Kind.BACKSTEP:
            facts.distance()>=rules.minimumBurstGapMicros()/1_000_000.0 && facts.distance()<=rules.maximumBurstGapMicros()/1_000_000.0
                && (facts.distance()>=rules.distantGapMicros()/1_000_000.0 || facts.separatingSpeed()>=rules.retreatSpeedMicros()/1_000_000.0)?MeleeManeuver.Kind.BURST:null;
        if(kind==MeleeManeuver.Kind.BURST && !MeleeTactics.readyAt(state.combat(),order,order.actorPosition(),tick,state.bootstrap().ruleset().melee()))return Optional.empty();
        return kind!=null && capability.maneuvers().contains(kind) && (prior==null || prior.ready(kind,tick))?Optional.of(kind):Optional.empty();
    }
    public static Optional<SurfaceAnchor> destination(FrontierWorldState state,CombatOrder order,MeleeManeuver.Kind kind,KnownPedestrianRouteKnowledge knowledge,long tick){
        var rules=state.bootstrap().ruleset().melee().maneuvers();var origin=order.actorPosition();var target=order.targetPosition();
        int radius=kind==MeleeManeuver.Kind.BURST?rules.burstDistance():rules.retreatDistance();
        var physical=PedestrianInteractionStations.within(knowledge.geometry(),origin,radius*2,Set.of()).stream()
            .filter(p->knowledge.clearStation(p) && !FacilityPedestrianContacts.of(state.bootstrap()).noParking().contains(p.support())
                && ServiceBoundaryComposition.turnoverPoint(state,p).isEmpty() && MeleePositions.available(state,order,p,tick)).toList();
        var candidates=rank(order,kind,physical,rules).stream().filter(point->kind!=MeleeManeuver.Kind.BURST
                || MeleeTactics.readyAt(state.combat(),order,point,tick,state.bootstrap().ruleset().melee())).toList();
        return MeleePositions.reachable(candidates,3,p->{var route=knowledge.plannedPath(origin,new MovementOrder(order.owner(),order.execution().actorId(),1,1,List.of(p),TraversalCapability.PEDESTRIAN,MovementOrder.ArrivalPolicy.EXACT_STATION));
            if(route.size()>radius+2)throw new KnownPedestrianNavigation.RouteUnavailable(PedestrianRouteResult.Status.NO_PATH,"maneuver detour exceeds bounded distance");});
    }
    public static List<SurfaceAnchor> rank(CombatOrder order,MeleeManeuver.Kind kind,List<SurfaceAnchor> candidates,ManeuverRules rules){
        var origin=order.actorPosition();var target=order.targetPosition();double old=distance(origin,target);
        int radius=kind==MeleeManeuver.Kind.BURST?rules.burstDistance():rules.retreatDistance();
        return candidates.stream().filter(p->!p.equals(origin) && Math.abs(p.x()-origin.x())+Math.abs(p.z()-origin.z())<=radius
                && p.y()<=origin.y()+radius*order.navigation().stepUpBlocks() && p.y()>=origin.y()-radius*order.navigation().dropBlocks() && order.engagement().contains(p)
                && (kind==MeleeManeuver.Kind.BURST?distance(p,target)>=1.25 && distance(p,target)<old-0.75:distance(p,target)>old+0.5))
            .distinct().sorted(Comparator.comparingDouble((SurfaceAnchor p)->kind==MeleeManeuver.Kind.BURST?Math.abs(distance(p,target)-1.6):-distance(p,target))
                .thenComparingInt(SurfaceAnchor::x).thenComparingInt(SurfaceAnchor::z).thenComparingInt(SurfaceAnchor::y)).toList();
    }
    public static boolean available(FrontierWorldState state,CombatOrder order,SurfaceAnchor station){
        return state.combat().maneuvers().values().stream().filter(m->m.open() && !m.owner().equals(order.owner())).noneMatch(m->{
            var profile=state.actorLocations().get(m.actor().execution().actorId()).navigationProfile();double half=(profile.width()+order.navigation().width())/2;
            return Math.abs(m.station().x()-station.x())<half && Math.abs(m.station().z()-station.z())<half
                && m.station().y()<station.y()+order.navigation().height() && station.y()<m.station().y()+profile.height();});
    }
    private static double distance(SurfaceAnchor a,SurfaceAnchor b){return Math.hypot(a.x()-b.x(),a.z()-b.z());}
    public static MeleeManeuver selected(FrontierWorldState state,CombatOrder order,ActorActuationId actor,MeleeManeuver.Kind kind,SurfaceAnchor station,long tick){
        var previous=state.combat().maneuvers().get(order.owner());var rules=state.bootstrap().ruleset().melee().maneuvers();
        return new MeleeManeuver(actor,order.weapon(),order.target(),kind,tick+1,order.actorPosition(),station,tick,0,0,
            kind==MeleeManeuver.Kind.BURST?tick+rules.burstCooldown():previous==null?0:previous.burstReadyAt(),
            kind==MeleeManeuver.Kind.BACKSTEP?tick+rules.retreatCooldown():previous==null?0:previous.retreatReadyAt(),tick+rules.sharedCooldown());
    }
    private MeleeManeuvers(){}
}
