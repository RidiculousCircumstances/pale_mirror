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
        if(strike.attack()!=MeleeAttack.LUNGE)return;
        var move=state.actorMovements().get(strike.attacker().execution().actorId());
        if(move==null || move.coldTravel().isPresent() || !move.executionId().equals(strike.attacker().execution())
                || !CombatComposition.CAPABILITIES.require(move.executionId()).permitsApproach(state,move))return;
        var actuation=FrontierV3ActorActuation.capture(state,fighter,move.executionId(),currentState);
        var prior=FrontierV3LocalGoalCorrection.current(fighter,actuation,move.order());
        if(!tracking){
            // Final commitment retains the last observed correction; it never follows later inputs.
            prior.filter(p->p.token()==strike.attemptOrdinal()).ifPresent(p->FrontierV3LocalGoalCorrection.offer(fighter,actuation,
                    move.order(),p.token(),p.station(),p.envelope(),level.getGameTime()+2));return;
        }
        var origin=FrontierV3BodyObservation.capture(fighter).supportedBody();
        var observed=FrontierV3BodyObservation.capture(target).support();
        if(origin.isEmpty() || observed.isEmpty())return;
        var contact=observed.orElseThrow();var direction=fighter.position().subtract(target.position()).normalize();
        var station=SurfaceAnchor.at((int)Math.floor(target.getX()+direction.x),contact.y(),(int)Math.floor(target.getZ()+direction.z));
        int radius=state.bootstrap().ruleset().melee().engagements().lungeCorrectionBlocks();
        if(move.order().legalStations().stream().noneMatch(p->p.manhattanDistance(station)<=radius))return;
        var permission=CombatComposition.CAPABILITIES.current(state,move.executionId(),tick);
        if(permission.isEmpty() || !permission.orElseThrow().target().equals(strike.target()) || !permission.orElseThrow().engagement().contains(station))return;
        var envelope=LocalNavigationEnvelope.between(origin.orElseThrow(),List.of(station));
        var allowed=envelope.permittedSupports().stream().filter(p->state.bootstrap().bounds().contains(p)
                && permission.orElseThrow().engagement().contains(new SurfaceAnchor(p))).collect(java.util.stream.Collectors.toSet());
        if(!allowed.contains(station.support()))return;
        if(Boolean.getBoolean("pale_mirror.frontier_v3.combat_trace") && prior.filter(p->p.token()==strike.attemptOrdinal() && p.station().equals(station)).isEmpty())
            io.farfrontier.palemirror.PaleMirrorMod.LOGGER.info("PMV3_MELEE_GUIDANCE actor={} ordinal={} parent={} correction={} nativeTick={}",
                    move.executionId().actorId().value(),strike.attemptOrdinal(),move.order().legalStations(),station,level.getGameTime());
        FrontierV3LocalGoalCorrection.offer(fighter,actuation,move.order(),strike.attemptOrdinal(),station,new LocalNavigationEnvelope(allowed),level.getGameTime()+2);
    }
    private FrontierV3MeleeGuidance(){}
}
