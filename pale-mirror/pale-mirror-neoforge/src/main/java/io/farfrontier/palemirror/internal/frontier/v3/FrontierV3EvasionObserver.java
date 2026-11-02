package io.farfrontier.palemirror.internal.frontier.v3;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.combat.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import java.util.*;
/** Actual dated threat perception only. No movement, damage, canonical repair or COLD target tracking. */
final class FrontierV3EvasionObserver {
    private static final int MAX_PER_PASS=32;
    private static final Map<ServerLevel,Long> NEXT=new WeakHashMap<>();
    private static final Map<ServerLevel,Integer> CURSOR=new WeakHashMap<>();
    static void tick(ServerLevel level,FrontierV3ServerRuntime<FrontierWorldState,FrontierWorldProjection> runtime){
        var state=runtime.decodedState().orElseThrow();var rules=state.bootstrap().ruleset().evasion();long nativeTick=level.getGameTime();
        if(nativeTick<NEXT.getOrDefault(level,0L))return;NEXT.put(level,nativeTick+rules.reviewTicks());
        var episodes=state.combat().evasions().values().stream().sorted(Comparator.comparing(Evasion::actor)).toList();
        if(episodes.isEmpty()){CURSOR.remove(level);return;}
        int start=Math.floorMod(CURSOR.getOrDefault(level,0),episodes.size()),count=Math.min(MAX_PER_PASS,episodes.size());
        for(int i=0;i<count;i++){
            var value=episodes.get((start+i)%episodes.size());if(!level.dimension().location().toString().equals(value.threat().dimension()))continue;
            var body=level.getEntity(ActorBodyId.entityId(state.bootstrap().worldId(),value.actor()));var target=level.getEntity(value.threat().entity());
            if(!(body instanceof LivingEntity victim) || !(target instanceof LivingEntity attacker) || !victim.isAlive() || !attacker.isAlive())continue;
            double distance=victim.distanceToSqr(attacker);if(distance>rules.safeRadius()*(double)rules.safeRadius() || !victim.hasLineOfSight(attacker))continue;
            if(!ActorBodyAuthority.retainsPhysicalCustody(state,value.actor()))continue;
            var position=new SurfaceAnchor(new BlockPosition(attacker.getOnPos().getX(),attacker.getOnPos().getY(),attacker.getOnPos().getZ()));
            var bodyId=ActorBodyAuthority.current(state,value.actor());
            FrontierV3CommandSubmission.submit(runtime,"evasion-perception",value.actor().value(),
                    new EvasionEvent.Seen(bodyId,value.id(),value.observedAt(),position,true));
        }
        CURSOR.put(level,(start+count)%episodes.size());
    }
    private FrontierV3EvasionObserver(){}
}
