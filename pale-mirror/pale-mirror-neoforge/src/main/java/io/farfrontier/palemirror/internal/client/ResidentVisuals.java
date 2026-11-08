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
        if(io.farfrontier.palemirror.internal.frontier.v3.PaleMirrorSeatEntities.isResidentSeat(actor.getVehicle())){
            rig.leg(true,-1.35f);rig.leg(false,-1.35f);
        }
        var frame=PaleMirrorCombatClient.frame(actor,partial);
        if(frame!=null){
            var pose=MeleeAnimation.sample(frame);var hand=actor.getMainArm();float mirror=hand==HumanoidArm.RIGHT?1:-1;
            rig.arm(hand,pose.shoulder(),pose.elbow(),mirror*pose.yaw(),mirror*pose.roll());
            rig.arm(hand.getOpposite(),-0.7f,-0.6f,0,-mirror*0.25f);
            rig.leg(true,pose.leg());
            rig.torso(mirror*pose.torso(),pose.lean());
            float nativeYaw=Mth.rotLerp(partial,actor.yBodyRotO,actor.yBodyRot);
            rig.look(Mth.clamp(Mth.wrapDegrees(frame.payload().facingYaw()-MeleeAnimation.facingYaw(frame,nativeYaw)),-60,60)*Mth.DEG_TO_RAD,
                frame.payload().facingPitch()*Mth.DEG_TO_RAD);
            PaleMirrorCombatClient.rendered(actor,frame,pose.shoulder(),pose.elbow());return true;
        }
        if(actor.getMainHandItem().getItem() instanceof net.minecraft.world.item.CrossbowItem
                && (actor.isUsingItem() || net.minecraft.world.item.CrossbowItem.isCharged(actor.getMainHandItem()))) {
            var main=actor.getMainArm();float mirror=main==HumanoidArm.RIGHT?1:-1;
            float reload=actor.isUsingItem()?Mth.clamp(actor.getTicksUsingItem()/25f,0,1):1;
            rig.arm(main,-1.45f,-0.15f,-mirror*0.2f,0);
            rig.arm(main.getOpposite(),-1.0f-0.3f*reload,-0.8f+0.5f*reload,mirror*(0.6f-0.3f*reload),0);
            return true;
        }
        var meal=PaleMirrorResidentMealClient.frame(actor,partial);
        if(meal!=null){
            var hand=actor.getMainArm().getOpposite();float mirror=hand==HumanoidArm.RIGHT?1:-1;
            float bite=(float)(.5+.5*Math.cos(meal.phase()*Math.PI/20));
            rig.arm(hand,-.9f-.5f*bite,-1.1f-.8f*bite,-mirror*.15f,mirror*.05f);
            rig.torso(0,.03f*bite);return true;
        }
        var routine=PaleMirrorResidentRoutineClient.frame(actor,partial);
        if(routine!=null){
            float phase=routine.age();
            if(routine.kind()==io.farfrontier.palemirror.frontier.v3.model.routine.ResidentRoutine.Kind.GREET){
                float fade=Math.min(1,phase/10f)*Math.min(1,Math.max(0,(80-phase)/20f));
                rig.arm(actor.getMainArm(),-1.5f*fade,0,0,(actor.getMainArm()==HumanoidArm.RIGHT?1:-1)*(.5f+.18f*(float)Math.sin(phase*.45))*fade);
            }else if(routine.kind()==io.farfrontier.palemirror.frontier.v3.model.routine.ResidentRoutine.Kind.STRETCH){
                float lift=(float)Math.pow(Math.sin(Math.min(1,phase/120f)*Math.PI),2);
                rig.arm(HumanoidArm.RIGHT,-2.4f*lift,0,0,.3f*lift);rig.arm(HumanoidArm.LEFT,-2.4f*lift,0,0,-.3f*lift);rig.torso(0,-.08f*lift);
            }
            return true;
        }
        boolean held=false;
        for(var hand:HumanoidArm.values())if(!actor.getItemInHand(hand==actor.getMainArm()?net.minecraft.world.InteractionHand.MAIN_HAND:net.minecraft.world.InteractionHand.OFF_HAND).isEmpty()){
            rig.arm(hand,-0.8f,-0.35f,0,hand==HumanoidArm.RIGHT?0.08f:-0.08f);held=true;
        }
        var work=PaleMirrorResidentWorkClient.frame(actor,partial);
        if(work!=null){
            var pose=ResidentWorkAnimation.sample(work.kind(),work.age()/work.duration());
            var hand=actor.getMainArm();float mirror=hand==HumanoidArm.RIGHT?1:-1;
            rig.arm(hand,pose.shoulder(),pose.elbow(),mirror*pose.yaw(),mirror*pose.roll());
            if(pose.twoHands())rig.arm(hand.getOpposite(),pose.shoulder(),pose.elbow(),-mirror*pose.yaw(),-mirror*pose.roll());
            rig.torso(mirror*pose.torso(),pose.lean());PaleMirrorResidentWorkClient.rendered(actor,work,pose);return true;
        }
        return held;
    }
    static void rendered(Villager actor,String rig){if(SEEN.size()<256 || SEEN.containsKey(actor.getUUID()))SEEN.put(actor.getUUID(),new Seen(rig,clock));}
    public static String renderedRig(Villager actor){var value=SEEN.get(actor.getUUID());return value!=null && clock-value.tick()<=2?value.rig():"unobserved";}
    static void tick(){clock++;if(Minecraft.getInstance().level==null){SEEN.clear();return;}SEEN.values().removeIf(value->clock-value.tick()>2);}
    static void clear(){SEEN.clear();clock=0;if(net.neoforged.fml.ModList.get().isLoaded("entity_model_features"))HumanEraVisuals.clear();}
    private ResidentVisuals(){}
}
