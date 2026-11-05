package io.farfrontier.palemirror.frontier.v3.model.combat;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.navigation.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorActuationId;
import java.util.*;
/** Shared tactical selection. Roles provide permission/knowledge; navigation proves traversability. */
public final class MeleeManeuvers {
    public record Facts(double distance,double separatingSpeed,boolean threateningSwing,double targetVelocityX,double targetVelocityZ){
        public Facts(double distance,double separatingSpeed){this(distance,separatingSpeed,false);}
        public Facts(double distance,double separatingSpeed,boolean threateningSwing){this(distance,separatingSpeed,threateningSwing,0,0);}
        public Facts{if(!Double.isFinite(distance)||distance<0||!Double.isFinite(separatingSpeed)
                || !Double.isFinite(targetVelocityX) || !Double.isFinite(targetVelocityZ))throw new IllegalArgumentException("invalid maneuver observations");}
    }
    public static Optional<MeleeManeuver.Kind> preference(FrontierWorldState state,CombatOrder order,Facts facts,long tick,CombatCapability capability){
        return MeleeDuelPolicy.choose(state,order,facts,tick,capability).maneuver();
    }
    static Optional<MeleeManeuver.Kind> ordinaryPreference(CombatState combat,CombatOrder order,Facts facts,long tick,MeleeRules melee,
            OptionalLong harm,Set<MeleeManeuver.Kind> allowed){
        var rules=melee.maneuvers();var prior=combat.maneuvers().get(order.owner());
        if(combat.recovering(order.owner(),tick)
                || combat.pending().containsKey(order.owner()) || prior!=null && prior.busy(tick,rules))return Optional.empty();
        boolean hurt=harm.isPresent() && tick>=harm.getAsLong() && tick-harm.getAsLong()<=rules.harmLifetime();
        var kind=((hurt || facts.threateningSwing()) && facts.distance()<=rules.hurtGapMicros()/1_000_000.0 || facts.distance()<rules.crowdedGapMicros()/1_000_000.0)?MeleeManeuver.Kind.BACKSTEP:
            facts.distance()>=rules.minimumBurstGapMicros()/1_000_000.0 && facts.distance()<=rules.maximumBurstGapMicros()/1_000_000.0
                && (facts.distance()>=rules.distantGapMicros()/1_000_000.0 || facts.separatingSpeed()>=rules.retreatSpeedMicros()/1_000_000.0)?MeleeManeuver.Kind.BURST:null;
        if(kind==MeleeManeuver.Kind.BURST && MeleePositions.weapon(combat,order)
                .filter(profile->facts.distance()<=rules.burstDistance()+profile.reach()).isEmpty())return Optional.empty();
        return kind!=null && combat.affords(order.owner(),tick,CombatMechanics.cost(order.profile(),melee.readiness().cost(kind)),melee)
                && allowed.contains(kind) && CombatMechanics.permits(order.profile(),kind) && (prior==null || prior.ready(kind,tick))?Optional.of(kind):Optional.empty();
    }
    public static Optional<SurfaceAnchor> destination(FrontierWorldState state,CombatOrder order,MeleeManeuver.Kind kind,KnownPedestrianRouteKnowledge knowledge,long tick){
        return destination(state,order,kind,knowledge,tick,new Facts(distance(order.actorPosition(),order.targetPosition()),0)).station();
    }
    public record Destination(Optional<SurfaceAnchor> station,String detail) { }
    public static Destination destination(FrontierWorldState state,CombatOrder order,MeleeManeuver.Kind kind,KnownPedestrianRouteKnowledge knowledge,long tick,Facts facts){
        var rules=state.bootstrap().ruleset().melee().maneuvers();var origin=order.actorPosition();var target=order.targetPosition();
        if(kind==MeleeManeuver.Kind.BLOCK)return new Destination(Optional.of(origin),"stationary weapon defence");
        int radius=rules.distance(kind);
        var physical=PedestrianInteractionStations.within(knowledge.geometry(),origin,radius*2,Set.of()).stream().filter(knowledge::clearStation).toList();
        var access=ServiceAccessCoordinator.transientDestinations(state,order.execution().actorId());
        var publicSpace=physical.stream().filter(access).toList();
        var space=publicSpace.stream().filter(p->MeleePositions.available(state,order,p,tick)).toList();
        var ranked=kind==MeleeManeuver.Kind.PURSUIT_RUN
                ?MeleePursuit.rank(state.combat(),order,space,facts,tick,state.bootstrap().ruleset().melee()):rank(order,kind,space,rules);
        var candidates=ranked.stream().filter(point->useful(state,order,kind,point))
                .filter(point->!kind.lateral() || compatibleSector(state,order,point,tick))
                .filter(point->kind!=MeleeManeuver.Kind.BURST
                || MeleeTactics.readyAt(state.combat(),order,point,tick,state.bootstrap().ruleset().melee())).toList();
        var preferred=candidates.stream().sorted(Comparator.comparingInt(knowledge.geometry()::traversalPenalty)).toList();
        var station=MeleePositions.reachable(preferred,3,p->{
            var route=knowledge.plannedPath(origin,new MovementOrder(order.owner(),order.execution().actorId(),1,1,List.of(p),TraversalCapability.PEDESTRIAN,MovementOrder.ArrivalPolicy.EXACT_STATION));
            if(route.size()>radius+2)throw new KnownPedestrianNavigation.RouteUnavailable(PedestrianRouteResult.Status.NO_PATH,"maneuver detour exceeds bounded distance");});
        return new Destination(station,"geometry="+physical.size()+",access="+publicSpace.size()+",space="+space.size()+",tactical="+candidates.size()+",route="+station.isPresent());
    }
    public static List<SurfaceAnchor> rank(CombatOrder order,MeleeManeuver.Kind kind,List<SurfaceAnchor> candidates,ManeuverRules rules){
        var origin=order.actorPosition();var target=order.targetPosition();double old=distance(origin,target);
        int radius=rules.distance(kind);
        if(kind.lateral())return lateralRank(order,kind,candidates,rules);
        return candidates.stream().filter(p->!p.equals(origin) && Math.abs(p.x()-origin.x())+Math.abs(p.z()-origin.z())<=radius
                && p.y()<=origin.y()+radius*order.navigation().stepUpBlocks() && p.y()>=origin.y()-radius*order.navigation().dropBlocks() && order.engagement().contains(p)
                && (kind==MeleeManeuver.Kind.BURST?distance(p,target)>=1.25 && distance(p,target)<old-0.75:distance(p,target)>old+0.5))
            .distinct().sorted(Comparator.comparingDouble((SurfaceAnchor p)->kind==MeleeManeuver.Kind.BURST?Math.abs(distance(p,target)-1.6):-distance(p,target))
                .thenComparingInt(SurfaceAnchor::x).thenComparingInt(SurfaceAnchor::z).thenComparingInt(SurfaceAnchor::y)).toList();
    }
    private static List<SurfaceAnchor> lateralRank(CombatOrder order,MeleeManeuver.Kind kind,List<SurfaceAnchor> candidates,ManeuverRules rules){
        var origin=order.actorPosition();var target=order.targetPosition();double gap=distance(origin,target);
        if(gap<1.0E-6)return List.of();
        double dx=(target.x()-origin.x())/gap,dz=(target.z()-origin.z())/gap;
        var shape=rules.duel().shape();
        double forwardDistance=kind==MeleeManeuver.Kind.ADVANCE_LEFT || kind==MeleeManeuver.Kind.ADVANCE_RIGHT?1.0:kind==MeleeManeuver.Kind.DIAGONAL_LEFT || kind==MeleeManeuver.Kind.DIAGONAL_RIGHT?-0.5:0;
        double desiredX=origin.x()+dx*forwardDistance+dz*kind.side()*shape.lateralMicros()/1_000_000.0;
        double desiredZ=origin.z()+dz*forwardDistance-dx*kind.side()*shape.lateralMicros()/1_000_000.0;
        return candidates.stream().filter(point->{
            double x=point.x()-origin.x(),z=point.z()-origin.z();
            double side=(dz*x-dx*z)*kind.side(),forward=dx*x+dz*z;
            double next=distance(point,target);
            return Math.abs(point.x()-origin.x())+Math.abs(point.z()-origin.z())<=rules.distance(kind) && order.engagement().contains(point)
                    && point.y()<=origin.y()+order.navigation().stepUpBlocks()
                    && point.y()>=origin.y()-order.navigation().dropBlocks()
                    && side>=shape.minimumSideMicros()/1_000_000.0 && forward>=Math.min(shape.minimumForwardMicros()/1_000_000.0,forwardDistance-0.5)
                    && forward<=shape.maximumForwardMicros()/1_000_000.0 && next>=shape.minimumGapMicros()/1_000_000.0
                    && next<=gap+shape.radialSlackMicros()/1_000_000.0;
        }).distinct().sorted(Comparator.comparingDouble((SurfaceAnchor point)->Math.hypot(point.x()-desiredX,point.z()-desiredZ))
                .thenComparingInt(SurfaceAnchor::x).thenComparingInt(SurfaceAnchor::z).thenComparingInt(SurfaceAnchor::y)).toList();
    }
    private static boolean compatibleSector(FrontierWorldState state,CombatOrder order,SurfaceAnchor point,long tick){
        var rules=state.bootstrap().ruleset().melee();var sector=MeleeTactics.sector(state.combat(),order,tick,rules);
        if(sector.role()==MeleeSector.Role.PRESSURE || sector.role()==MeleeSector.Role.SUPPORT)return true;
        return sector.alignment(point,order.targetPosition())>=Math.min(sector.alignment(order.actorPosition(),order.targetPosition()),
                rules.engagements().flankAlignmentPermille()/1000.0);
    }
    /** A closing burst must finish within real weapon reach, not merely somewhere closer. */
    public static boolean useful(FrontierWorldState state,CombatOrder order,MeleeManeuver.Kind kind,SurfaceAnchor station){
        return kind!=MeleeManeuver.Kind.BURST || MeleePositions.weapon(state.combat(),order)
                .filter(profile->MeleePositions.legal(order,profile,station)).isPresent();
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
            kind==MeleeManeuver.Kind.BLOCK?tick+CombatTrainingCatalog.standard().number("defence.blockCooldown"):kind.lateral()?tick+(order.profile().knows(CombatSkillId.EVASION,
            CombatTrainingCatalog.standard().number("defence.chain"))?rules.duel().lateralCooldown()/2:rules.duel().lateralCooldown()):kind==MeleeManeuver.Kind.BACKSTEP?tick+rules.retreatCooldown():previous==null?0:previous.retreatReadyAt(),
            tick+(kind==MeleeManeuver.Kind.PURSUIT_RUN?rules.pursuit().sharedCooldown():kind.lateral()?rules.duel().sharedCooldown():rules.sharedCooldown()),
            kind==MeleeManeuver.Kind.PURSUIT_RUN?tick+rules.pursuit().cooldown():previous==null?0:previous.runReadyAt());
    }
    private MeleeManeuvers(){}
}
