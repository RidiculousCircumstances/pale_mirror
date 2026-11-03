package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.combat.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import java.util.*;
import java.util.function.Supplier;

/** A melee approach requests finite physical latitude; common navigation alone drives the body. */
final class FrontierV3MeleeGuidance {
    static void observe(ServerLevel level,FrontierWorldState state,Supplier<Optional<FrontierWorldState>> currentState,
            Mob fighter,LivingEntity target,MeleeStrike strike,boolean tracking,long tick){
        if(!strike.attack().closesDistance())return;
        var move=state.actorMovements().get(strike.attacker().execution().actorId());
        var execution=strike.attacker().execution();
        if(move!=null && (move.coldTravel().isPresent() || !move.executionId().equals(execution)
                || !CombatComposition.CAPABILITIES.require(execution).permitsApproach(state,move)))return;
        var parent=move!=null?Optional.of(move.order()):ActorExecutionComposition.CAPABILITIES
                .require(execution.activityKind()).navigationOrder(state,execution);
        if(parent.isEmpty())return;
        var order=parent.orElseThrow();
        var actuation=FrontierV3ActorActuation.capture(state,fighter,execution,currentState);
        var prior=FrontierV3LocalGoalCorrection.current(fighter,actuation,order);
        if(!tracking){
            // Final commitment retains the last observed correction; it never follows later inputs.
            prior.filter(p->p.token()==strike.attemptOrdinal()).ifPresent(p->FrontierV3LocalGoalCorrection.offer(fighter,actuation,
                    order,p.token(),p.station(),p.envelope(),level.getGameTime()+2));return;
        }
        var origin=FrontierV3BodyObservation.capture(fighter).supportedBody();
        var observed=FrontierV3BodyObservation.capture(target).support();
        if(origin.isEmpty() || observed.isEmpty())return;
        var contact=observed.orElseThrow();var direction=fighter.position().subtract(target.position()).normalize();
        if(strike.attack()==MeleeAttack.FOLLOWUP && Math.sqrt(target.getBoundingBox().distanceToSqr(fighter.getEyePosition()))
                >strike.profile().reach()+state.bootstrap().ruleset().melee().followup().technique().approachAllowance())return;
        // A recovery step aims at weapon contact, not all the way into the target's cell.
        double separation=strike.attack()==MeleeAttack.FOLLOWUP?Math.max(1.0,strike.profile().reach()-state.bootstrap().ruleset().melee().rangeHysteresisMicros()/1_000_000.0):1.0;
        var station=SurfaceAnchor.at((int)Math.floor(target.getX()+direction.x*separation),contact.y(),(int)Math.floor(target.getZ()+direction.z*separation));
        int radius=state.bootstrap().ruleset().melee().engagements().lungeCorrectionBlocks();
        if(origin.orElseThrow().supportingSurface().manhattanDistance(station)>radius
                && order.legalStations().stream().noneMatch(p->p.manhattanDistance(station)<=radius))return;
        var permission=CombatComposition.CAPABILITIES.current(state,execution,tick);
        if(permission.isEmpty() || !permission.orElseThrow().target().equals(strike.target()) || !permission.orElseThrow().engagement().contains(station))return;
        var envelope=LocalNavigationEnvelope.between(origin.orElseThrow(),List.of(station));
        var allowed=envelope.permittedSupports().stream().filter(p->state.bootstrap().bounds().contains(p)
                && permission.orElseThrow().engagement().contains(new SurfaceAnchor(p))).collect(java.util.stream.Collectors.toSet());
        if(!allowed.contains(station.support()))return;
        if(Boolean.getBoolean("pale_mirror.frontier_v3.combat_trace") && prior.filter(p->p.token()==strike.attemptOrdinal() && p.station().equals(station)).isEmpty())
            io.farfrontier.palemirror.PaleMirrorMod.LOGGER.info("PMV3_MELEE_GUIDANCE actor={} ordinal={} parent={} correction={} nativeTick={}",
                    execution.actorId().value(),strike.attemptOrdinal(),order.legalStations(),station,level.getGameTime());
        FrontierV3LocalGoalCorrection.offer(fighter,actuation,order,strike.attemptOrdinal(),station,new LocalNavigationEnvelope(allowed),level.getGameTime()+2);
    }
    private FrontierV3MeleeGuidance(){}
}
