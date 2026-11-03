package io.farfrontier.palemirror.internal.frontier.v3;
import io.farfrontier.palemirror.internal.network.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import java.util.*;
/** Cosmetic only. Work owners prove their current labour before emitting a bounded pulse. */
final class FrontierV3WorkPresentation {
    // Six native ticks match vanilla's ordinary arm-swing duration, not a work-rate rule.
    static final int DURATION=6;
    private record Stamp(long epoch,long sequence,long until,ResidentWorkGesture gesture){}
    private static final Map<Mob,Stamp> STAMPS=new WeakHashMap<>();
    static boolean active(Mob worker){var p=STAMPS.get(worker);return p!=null && p.gesture()!=ResidentWorkGesture.CLEAR && worker.level().getGameTime()<p.until();}
    static boolean pulse(Mob worker,ResidentWorkGesture gesture){
        if(gesture==ResidentWorkGesture.CLEAR)throw new IllegalArgumentException("use explicit work gesture retirement");
        var level=(ServerLevel)worker.level();
        var body=FrontierV3ActorCarrierComposition.declaredBy(worker).orElseThrow(()->new IllegalArgumentException("work cue requires a declared physical body"));
        var previous=STAMPS.get(worker);long now=level.getGameTime();
        if(previous!=null && previous.epoch()==body.epoch() && now<previous.until() && previous.gesture()==gesture)return false;
        long sequence=previous==null?1:Math.addExact(previous.sequence(),1);
        STAMPS.put(worker,new Stamp(body.epoch(),sequence,now+DURATION,gesture));
        send(level,worker,new ResidentWorkGesturePayload(worker.getId(),worker.getUUID(),body.epoch(),sequence,gesture,DURATION));return true;
    }
    static void clear(Mob worker){
        var prior=STAMPS.get(worker);if(prior==null || prior.gesture()==ResidentWorkGesture.CLEAR)return;
        var body=FrontierV3ActorCarrierComposition.declaredBy(worker);
        if(body.isEmpty() || body.orElseThrow().epoch()!=prior.epoch()){STAMPS.remove(worker);return;}
        var level=(ServerLevel)worker.level();long sequence=Math.addExact(prior.sequence(),1);
        STAMPS.put(worker,new Stamp(prior.epoch(),sequence,level.getGameTime(),ResidentWorkGesture.CLEAR));
        send(level,worker,new ResidentWorkGesturePayload(worker.getId(),worker.getUUID(),prior.epoch(),sequence,ResidentWorkGesture.CLEAR,DURATION));
    }
    private static void send(ServerLevel level,Mob worker,ResidentWorkGesturePayload cue){
        for(var player:level.players())if(player.distanceToSqr(worker)<=128*128 && NetworkRegistry.hasChannel(player.connection,ResidentWorkGesturePayload.TYPE.id()))
            PacketDistributor.sendToPlayer(player,cue);
    }
    private FrontierV3WorkPresentation(){}
}
