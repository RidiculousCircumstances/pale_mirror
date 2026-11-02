package io.farfrontier.palemirror.internal.client;

import io.farfrontier.palemirror.internal.network.CombatGesturePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import java.util.*;

/** Ephemeral, UUID/epoch/attempt fenced interpolation. No target, navigation or damage decisions. */
public final class PaleMirrorCombatClient {
    private record Cue(CombatGesturePayload payload,long receivedAt){}
    private static final Map<UUID,Cue> CUES=new HashMap<>();
    private static long clock;
    public record Rendered(String attack,long attempt,float age,int preparation,float shoulder,float elbow,long atTick){}
    private static final Map<UUID,Rendered> RENDERED=new HashMap<>();
    public static Rendered rendered(Entity actor){
        var value=RENDERED.get(actor.getUUID());return value!=null && clock-value.atTick()<=2?value:null;
    }
    static void rendered(Entity actor,Frame frame,float shoulder,float elbow){
        if(frame!=null)RENDERED.put(actor.getUUID(),new Rendered(frame.payload().attack().name(),frame.payload().attempt(),frame.age(),
                frame.payload().preparation(),shoulder,elbow,clock));
    }
    public record Frame(CombatGesturePayload payload,float age){}
    public static void receive(CombatGesturePayload payload){
        var prior=CUES.get(payload.body());
        if(prior!=null && (payload.epoch()<prior.payload().epoch()
                || payload.epoch()==prior.payload().epoch() && payload.attempt()<prior.payload().attempt()
                || payload.epoch()==prior.payload().epoch() && payload.attempt()==prior.payload().attempt()
                    && (payload.sampledAt()<prior.payload().sampledAt() || payload.phase().wireTag()<prior.payload().phase().wireTag())))return;
        // Tracking can precede entity insertion. Retain a finite UUID cue, never apply by runtime id alone.
        if(payload.sampledAt()-payload.startedAt()>=payload.duration())return;
        if(CUES.size()>=256 && !CUES.containsKey(payload.body()))return;
        CUES.put(payload.body(),new Cue(payload,clock));
    }
    public static Frame frame(Entity actor,float partial){
        if(actor instanceof net.minecraft.world.entity.LivingEntity living && !living.isAlive())return null;
        var cue=CUES.get(actor.getUUID());
        if(cue==null || cue.payload().entityId()!=actor.getId())return null;
        float age=cue.payload().sampledAt()-cue.payload().startedAt()+clock-cue.receivedAt()+partial;
        return age>=cue.payload().duration()?null:new Frame(cue.payload(),age);
    }
    public static void tick(){
        clock++;
        if(Minecraft.getInstance().level==null){clear();return;}
        CUES.values().removeIf(cue->clock-cue.receivedAt()+cue.payload().sampledAt()-cue.payload().startedAt()>=cue.payload().duration());
        RENDERED.keySet().removeIf(id->!CUES.containsKey(id));
    }
    public static void clear(){CUES.clear();RENDERED.clear();clock=0;}
    private PaleMirrorCombatClient(){}
}
