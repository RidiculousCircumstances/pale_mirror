package io.farfrontier.palemirror.internal.client;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.npc.Villager;
import java.util.*;
/** One final action layer over a rig's base locomotion. No gameplay or body actuation. */
public final class ResidentVisuals {
    private record Seen(String rig,long tick){}
    private static final Map<UUID,Seen> SEEN=new HashMap<>();
    private static long clock;
    static boolean apply(Villager actor,float partial,ResidentPoseRig rig){
        var frame=PaleMirrorCombatClient.frame(actor,partial);
        if(frame!=null){
            var pose=MeleeAnimation.sample(frame);var hand=actor.getMainArm();float mirror=hand==HumanoidArm.RIGHT?1:-1;
            rig.arm(hand,pose.shoulder(),pose.elbow(),mirror*pose.yaw(),mirror*pose.roll());
            rig.arm(hand.getOpposite(),-0.7f,-0.6f,0,-mirror*0.25f);
            rig.torso(mirror*pose.torso(),pose.lean());
            float nativeYaw=Mth.rotLerp(partial,actor.yBodyRotO,actor.yBodyRot);
            rig.look(Mth.clamp(Mth.wrapDegrees(frame.payload().facingYaw()-MeleeAnimation.facingYaw(frame,nativeYaw)),-60,60)*Mth.DEG_TO_RAD,
                frame.payload().facingPitch()*Mth.DEG_TO_RAD);
            PaleMirrorCombatClient.rendered(actor,frame,pose.shoulder(),pose.elbow());return true;
        }
        boolean held=false;
        for(var hand:HumanoidArm.values())if(!actor.getItemInHand(hand==actor.getMainArm()?net.minecraft.world.InteractionHand.MAIN_HAND:net.minecraft.world.InteractionHand.OFF_HAND).isEmpty()){
            rig.arm(hand,-0.8f,-0.35f,0,hand==HumanoidArm.RIGHT?0.08f:-0.08f);held=true;
        }
        return held;
    }
    static void rendered(Villager actor,String rig){if(SEEN.size()<256 || SEEN.containsKey(actor.getUUID()))SEEN.put(actor.getUUID(),new Seen(rig,clock));}
    public static String renderedRig(Villager actor){var value=SEEN.get(actor.getUUID());return value!=null && clock-value.tick()<=2?value.rig():"unobserved";}
    static void tick(){clock++;if(Minecraft.getInstance().level==null){SEEN.clear();return;}SEEN.values().removeIf(value->clock-value.tick()>2);}
    static void clear(){SEEN.clear();clock=0;if(net.neoforged.fml.ModList.get().isLoaded("entity_model_features"))HumanEraVisuals.clear();}
    private ResidentVisuals(){}
}
