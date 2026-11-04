package io.farfrontier.palemirror.internal.frontier.v3;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.combat.*;
import io.farfrontier.palemirror.internal.network.CombatGesture;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import java.util.*;
/** Native telegraph observation only. The common movement provider is the sole actuator. */
final class FrontierV3CombatManeuvers {
    private static final Map<ServerLevel,Integer> CURSOR=new WeakHashMap<>();
    private static final Map<Mob,String> TRACED=new WeakHashMap<>();
    static void tick(ServerLevel level,FrontierV3ServerRuntime<FrontierWorldState,FrontierWorldProjection> runtime){
        var state=runtime.decodedState().orElseThrow();var values=List.copyOf(state.combat().maneuvers().values());if(values.isEmpty())return;
        int cursor=Math.floorMod(CURSOR.getOrDefault(level,0),values.size());
        for(int i=0;i<Math.min(values.size(),32);i++){
            var m=values.get(cursor);cursor=(cursor+1)%values.size();
            var entity=level.getEntity(io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyId.entityId(state.bootstrap().worldId(),m.actor().execution().actorId()));
            if(!(entity instanceof Mob body) || !body.isAlive())continue;
            trace(state,runtime,body,m);
            if(!m.open()){FrontierV3CombatPresentation.maneuverEnded(level,body,m);continue;}
            if(!FrontierV3ActorBodyController.readyForExecution(level,state,List.of(m.actor().body())))continue;
            try{ActorBodyAuthority.requireActuation(state,m.actor());}catch(IllegalArgumentException stale){continue;}
            var order=CombatComposition.CAPABILITIES.current(state,m.actor().execution(),runtime.executionView().orElseThrow().instant().ticks());
            if(order.filter(m::matches).isEmpty())continue;
            if(m.launchedAt()>0){FrontierV3CombatPresentation.maneuverMoving(level,body,m);continue;}
            var opponent=level.getEntity(m.target().entity());if(!(opponent instanceof LivingEntity target) || !target.isAlive() || !body.hasLineOfSight(target))continue;
            // Preparation may not spend a burst whose fixed endpoint has already fallen out of range.
            if (!MeleeManeuvers.useful(state,order.orElseThrow(),m.kind(),m.station())) {
                FrontierV3CommandSubmission.propose(runtime,"combat-maneuver-obsolete",m.owner().value(),
                        new CombatEvent.ManeuverClosed(m.owner(),m.attemptOrdinal()));
                continue;
            }
            var rules=state.bootstrap().ruleset().melee().maneuvers();
            long start=FrontierV3CombatPresentation.begin(level,body,m.owner().value(),m.attemptOrdinal(),m.actor().body().physicalEpoch(),CombatGesture.maneuver(m.kind()),rules.preparation(m.kind()),
                rules.travelTicks(m.kind()),rules.recovery(m.kind()),target);
            if(level.getGameTime()-start>=rules.preparation(m.kind()) && runtime.executionView().orElseThrow().instant().ticks()>=m.selectedAt()+rules.preparation(m.kind()))
                FrontierV3CommandSubmission.propose(runtime,"combat-maneuver-launch",m.owner().value(),new CombatEvent.ManeuverLaunched(m.actor(),m.attemptOrdinal()));
        }
        CURSOR.put(level,cursor);
    }
    private static void trace(FrontierWorldState state,FrontierV3ServerRuntime<FrontierWorldState,?> runtime,Mob body,MeleeManeuver m){
        if(!Boolean.getBoolean("pale_mirror.frontier_v3.combat_trace"))return;
        var move=state.actorMovements().get(m.actor().execution().actorId());var lease=state.ambientLeases().get(m.actor().execution().actorId());
        String status=m.attemptOrdinal()+":"+m.launchedAt()+":"+m.closedAt()+":"+(move==null?"NONE":move.context().provider())+":"+(lease==null?"NONE":lease.status()+":"+lease.goal()+":"+lease.goalBody());
        if(m.open())status+=":"+body.level().getGameTime()/4;
        if(status.equals(TRACED.put(body,status)))return;
        io.farfrontier.palemirror.PaleMirrorMod.LOGGER.info("PMV3_MANEUVER actor={} tick={} selected={} launched={} closed={} kind={} goal={} move={} lease={} physical={}",
            m.actor().execution().actorId().value(),runtime.calendarInstant().orElseThrow(),m.selectedAt(),m.launchedAt(),m.closedAt(),m.kind(),m.station(),
            move==null?"NONE":move.context().provider()+":"+FrontierV3ActorMovementNavigation.waitReason((ServerLevel)body.level(),state,move).orElse("NO_RECORDED_WAIT"),lease,body.position());
    }
    private FrontierV3CombatManeuvers(){}
}
