package io.farfrontier.palemirror.internal.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.*;
import net.minecraft.client.model.geom.*;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.npc.Villager;

/** Vanilla body/profession layers with articulated arms only for an explicit server combat cue. */
final class CombatVillagerModel extends VillagerModel<Villager> implements ArmedModel {
    private final ModelPart right,left,rightForearm,leftForearm,folded,body;
    private boolean combat;
    CombatVillagerModel(ModelPart root){
        super(root);right=root.getChild("right_arm");left=root.getChild("left_arm");
        rightForearm=right.getChild("forearm");leftForearm=left.getChild("forearm");
        folded=root.getChild("arms");body=root.getChild("body");
    }
    static LayerDefinition layer(){
        var mesh=VillagerModel.createBodyModel();var root=mesh.getRoot();
        for(boolean right:new boolean[]{true,false}){
            var arm=root.addOrReplaceChild(right?"right_arm":"left_arm",CubeListBuilder.create().texOffs(44,22)
                    .mirror(!right).addBox(-2,-2,-2,4,4,4),PartPose.offset(right?-6:6,4,0));
            arm.addOrReplaceChild("forearm",CubeListBuilder.create().texOffs(44,26).mirror(!right)
                    .addBox(-2,0,-2,4,4,4),PartPose.offset(0,2,0));
        }
        return LayerDefinition.create(mesh,64,64);
    }
    boolean combat(){return combat;}
    @Override public void setupAnim(Villager actor,float swing,float amount,float age,float yaw,float pitch){
        root().getAllParts().forEach(ModelPart::resetPose);
        super.setupAnim(actor,swing,amount,age,yaw,pitch);
        var frame=PaleMirrorCombatClient.frame(actor,age-actor.tickCount);combat=frame!=null;
        right.visible=combat;left.visible=combat;folded.visible=!combat;
        if(!combat)return;
        float nativeYaw=net.minecraft.util.Mth.rotLerp(age-actor.tickCount,actor.yBodyRotO,actor.yBodyRot);
        getHead().yRot=net.minecraft.util.Mth.clamp(net.minecraft.util.Mth.wrapDegrees(frame.payload().facingYaw()-MeleeAnimation.facingYaw(frame,nativeYaw)),-60,60)*(float)Math.PI/180;
        getHead().xRot=frame.payload().facingPitch()*(float)Math.PI/180;
        var pose=MeleeAnimation.sample(frame);boolean rightHand=actor.getMainArm()==HumanoidArm.RIGHT;
        var weapon=rightHand?right:left;var forearm=rightHand?rightForearm:leftForearm;var off=rightHand?left:right;
        float mirror=rightHand?1:-1;
        weapon.xRot=pose.shoulder();weapon.yRot=mirror*pose.yaw();weapon.zRot=mirror*pose.roll();forearm.xRot=pose.elbow();
        off.xRot=-0.7f;off.zRot=-mirror*0.25f;(rightHand?leftForearm:rightForearm).xRot=-0.6f;
        body.yRot=mirror*pose.torso();body.xRot=pose.lean();
        weapon.z-=2*pose.lean();off.z-=2*pose.lean();
        PaleMirrorCombatClient.rendered(actor,frame,pose.shoulder(),pose.elbow());
    }
    @Override public void translateToHand(HumanoidArm arm,PoseStack poses){
        (arm==HumanoidArm.RIGHT?right:left).translateAndRotate(poses);
        (arm==HumanoidArm.RIGHT?rightForearm:leftForearm).translateAndRotate(poses);
        poses.translate(0,4/16f,0);
    }
}
