package io.farfrontier.palemirror.internal.client;
import io.farfrontier.palemirror.internal.network.*;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import java.util.*;
/** Bounded body-fenced visual state. No business phase is inferred from its pose. */
public final class PaleMirrorResidentWorkClient {
    private record Cue(ResidentWorkGesturePayload payload,long received,Vec3 position,int settled){}
    private static final Map<UUID,Cue> CUES=new HashMap<>();
    private static final Map<UUID,Rendered> RENDERED=new HashMap<>();
    private static long clock;
    public record Rendered(String gesture,float age,float shoulder,float elbow,long atTick){}
    record Frame(ResidentWorkGesture kind,float age,int duration){}
    public static void receive(ResidentWorkGesturePayload p){
        var old=CUES.get(p.body());
        if(old!=null && (p.epoch()<old.payload().epoch() || p.epoch()==old.payload().epoch() && p.sequence()<=old.payload().sequence()))return;
        if(CUES.size()>=256 && old==null)return;
        boolean same=old!=null && old.payload().entityId()==p.entityId() && old.payload().epoch()==p.epoch();
        var level=Minecraft.getInstance().level;var entity=level==null?null:level.getEntity(p.entityId());
        Vec3 position=same?old.position():entity!=null && entity.getUUID().equals(p.body())?entity.position():null;
        int settled=same?old.settled():position!=null && position.distanceToSqr(entity.xOld,entity.yOld,entity.zOld)<=1e-6?2:0;
        CUES.put(p.body(),new Cue(p,clock,position,settled));
        if(p.gesture()==ResidentWorkGesture.CLEAR || !same)RENDERED.remove(p.body());
    }
    static Frame frame(Entity actor,float partial){
        var c=CUES.get(actor.getUUID());if(c==null || c.payload().entityId()!=actor.getId() || c.payload().gesture()==ResidentWorkGesture.CLEAR
                || c.settled()<2 || actor instanceof LivingEntity living && !living.isAlive())return null;
        float age=clock-c.received()+partial;return age>=c.payload().duration()?null:new Frame(c.payload().gesture(),age,c.payload().duration());
    }
    static void rendered(Entity actor,Frame frame,ResidentWorkAnimation.Pose pose){
        RENDERED.put(actor.getUUID(),new Rendered(frame.kind().name(),frame.age(),pose.shoulder(),pose.elbow(),clock));
    }
    public static Rendered rendered(Entity actor){var p=RENDERED.get(actor.getUUID());return p!=null && clock-p.atTick()<=2?p:null;}
    public static void tick(){
        clock++;var level=Minecraft.getInstance().level;
        if(level==null){clear();return;}
        CUES.entrySet().removeIf(e->{
            var c=e.getValue();if(clock-c.received()>=c.payload().duration()){RENDERED.remove(e.getKey());return true;}
            var entity=level.getEntity(c.payload().entityId());
            if(entity!=null && entity.getUUID().equals(e.getKey())){
                var p=entity.position();int settled=c.position()!=null && c.position().distanceToSqr(p)<=1e-6?c.settled()+1:0;
                if(settled==0)RENDERED.remove(e.getKey());e.setValue(new Cue(c.payload(),c.received(),p,settled));
            }
            return false;
        });
        RENDERED.entrySet().removeIf(e->clock-e.getValue().atTick()>2);
    }
    public static void clear(){CUES.clear();RENDERED.clear();clock=0;}
    private PaleMirrorResidentWorkClient(){}
}
