package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.combat.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import java.util.*;

/** A melee approach requests finite physical latitude; common navigation alone drives the body. */
final class FrontierV3MeleeGuidance {
    static void observe(ServerLevel level,FrontierWorldState state,FrontierV3ServerRuntime<FrontierWorldState,?> runtime,
            Mob fighter,LivingEntity target,MeleeStrike strike,boolean tracking,long tick){
        if(!strike.attack().guidesApproach())return;
        var deadline=FrontierV3CombatPresentation.guidanceDeadline(level,fighter,strike,state.bootstrap().ruleset().melee());
        if(deadline.isEmpty()){trace(level,fighter,target,strike,"EXPIRED");return;}
        var move=state.actorMovements().get(strike.attacker().execution().actorId());
        var execution=strike.attacker().execution();
        if(move!=null && (move.coldTravel().isPresent() || !move.executionId().equals(execution)
                || !CombatComposition.CAPABILITIES.require(execution).permitsApproach(state,move))){trace(level,fighter,target,strike,"FOREIGN_APPROACH");return;}
        var parent=move!=null?Optional.of(move.order()):ActorExecutionComposition.CAPABILITIES
                .require(execution.activityKind()).navigationOrder(state,execution);
        if(parent.isEmpty()){trace(level,fighter,target,strike,"NO_PARENT");return;}
        var order=parent.orElseThrow();
        var actuation=FrontierV3ActorActuation.capture(state,fighter,execution,runtime::decodedState);
        var prior=FrontierV3LocalGoalCorrection.current(fighter,actuation,order);
        if(!tracking){
            trace(level,fighter,target,strike,prior.filter(p->p.token()==strike.attemptOrdinal()).isPresent()?"COMMITTED_RETAINED":"COMMITTED_WITHOUT_CORRECTION");
            return; // The last observed target is frozen until the original finite deadline.
        }
        var origin=FrontierV3BodyObservation.capture(fighter).supportedBody();
        var observed=FrontierV3BodyObservation.capture(target).support();
        if(origin.isEmpty() || observed.isEmpty()){trace(level,fighter,target,strike,"UNSUPPORTED_BODY");return;}
        var contact=observed.orElseThrow();var direction=fighter.position().subtract(target.position()).normalize();
        if(strike.attack()==MeleeAttack.FOLLOWUP && Math.sqrt(target.getBoundingBox().distanceToSqr(fighter.getEyePosition()))
                >strike.profile().reach()+state.bootstrap().ruleset().melee().followup().technique().approachAllowance())return;
        var permission=CombatComposition.CAPABILITIES.current(state,execution,tick);
        if(permission.isEmpty() || !permission.orElseThrow().target().equals(strike.target())){trace(level,fighter,target,strike,"PERMISSION");return;}
        // A short contact step preserves the declared style gap; it never orders a backward retreat.
        double contactGap=Math.min(permission.orElseThrow().style().preferredGapMicros()/1_000_000.0,
                strike.profile().reach()-state.bootstrap().ruleset().melee().rangeHysteresisMicros()/1_000_000.0);
        double separation=strike.attack()==MeleeAttack.QUICK?Math.max(1.0,contactGap):strike.attack()==MeleeAttack.FOLLOWUP?
                Math.max(1.0,strike.profile().reach()-state.bootstrap().ruleset().melee().rangeHysteresisMicros()/1_000_000.0):1.0;
        var station=SurfaceAnchor.at((int)Math.floor(target.getX()+direction.x*separation),contact.y(),(int)Math.floor(target.getZ()+direction.z*separation));
        if(strike.attack()==MeleeAttack.LATERAL || strike.attack()==MeleeAttack.FLANK || strike.attack()==MeleeAttack.RETREAT){
            var options=MeleeFootwork.candidates(state.combat(),permission.orElseThrow(),strike.attack(),origin.orElseThrow().supportingSurface(),contact,tick,state.bootstrap().ruleset().melee());
            var selected=options.stream().filter(p->MeleePositions.available(state,permission.orElseThrow(),p,tick)).findFirst();
            if(selected.isEmpty())return;station=selected.orElseThrow();
        }
        final var selectedStation=station;
        int radius=strike.attack().closesDistance()?state.bootstrap().ruleset().melee().engagements().lungeCorrectionBlocks():state.bootstrap().ruleset().melee().engagements().contactCorrectionBlocks();
        if(strike.attack()==MeleeAttack.FLANK)radius=state.bootstrap().ruleset().melee().training().number("footwork.flankRadius");
        final int permittedRadius=radius;
        if(origin.orElseThrow().supportingSurface().manhattanDistance(station)>radius
                && order.legalStations().stream().noneMatch(p->p.manhattanDistance(selectedStation)<=permittedRadius)){trace(level,fighter,target,strike,"OUTSIDE_LATITUDE");return;}
        if(strike.attack()==MeleeAttack.QUICK
                && Math.hypot(station.x()+0.5-target.getX(),station.z()+0.5-target.getZ())
                    >=Math.hypot(fighter.getX()-target.getX(),fighter.getZ()-target.getZ())){trace(level,fighter,target,strike,"CONTACT_RETAINED");return;}
        if(!permission.orElseThrow().engagement().contains(station)){trace(level,fighter,target,strike,"PERMISSION");return;}
        var envelope=LocalNavigationEnvelope.between(origin.orElseThrow(),List.of(station));
        var allowed=envelope.permittedSupports().stream().filter(p->state.bootstrap().bounds().contains(p)
                && permission.orElseThrow().engagement().contains(new SurfaceAnchor(p))).collect(java.util.stream.Collectors.toSet());
        if(!allowed.contains(station.support())){trace(level,fighter,target,strike,"OUTSIDE_SCOPE");return;}
        boolean changed=FrontierV3LocalGoalCorrection.offer(fighter,actuation,order,strike.attemptOrdinal(),station,new LocalNavigationEnvelope(allowed),deadline.getAsLong(),
                ()->runtime.decodedState().map(current->strike.equals(current.combat().pending().get(strike.owner()))).orElse(false));
        if(changed)FrontierV3ActorProbeSchedule.wake(runtime,state,execution.actorId(),level.getGameTime());
        trace(level,fighter,target,strike,changed?"OFFERED":"RETAINED");
    }
    private static void trace(ServerLevel level,Mob fighter,LivingEntity target,MeleeStrike strike,String result){
        FrontierV3CombatTrace.strike(level,fighter,target,strike,"GUIDANCE",result);
    }
    private FrontierV3MeleeGuidance(){}
}
