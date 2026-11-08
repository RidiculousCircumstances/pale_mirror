package io.farfrontier.palemirror.internal.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.*;
import net.minecraft.client.model.geom.*;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.npc.Villager;

/** Vanilla fallback with articulated arms for held items and explicit server combat cues. */
final class CombatVillagerModel extends VillagerModel<Villager> implements ArmedModel,ResidentPoseRig {
    private final ModelPart right,left,rightForearm,leftForearm,folded,body,rightLeg,leftLeg;
    private boolean combat;
    CombatVillagerModel(ModelPart root){
        super(root);right=root.getChild("right_arm");left=root.getChild("left_arm");
        rightForearm=right.getChild("forearm");leftForearm=left.getChild("forearm");
        rightLeg=root.getChild("right_leg");leftLeg=root.getChild("left_leg");folded=root.getChild("arms");body=root.getChild("body");
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
        combat=ResidentVisuals.apply(actor,age-actor.tickCount,this);
        right.visible=combat;left.visible=combat;folded.visible=!combat;
        ResidentVisuals.rendered(actor,"vanilla_articulated");
    }
    public void arm(HumanoidArm side,float shoulder,float elbow,float yaw,float roll){
        var arm=side==HumanoidArm.RIGHT?right:left;var forearm=side==HumanoidArm.RIGHT?rightForearm:leftForearm;
        arm.xRot=shoulder;arm.yRot=yaw;arm.zRot=roll;forearm.xRot=elbow;
    }
    public void leg(boolean right,float pitch){if(pitch!=0)(right?rightLeg:leftLeg).xRot=pitch;}
    public void torso(float yaw,float lean){body.yRot=yaw;body.xRot=lean;right.z-=2*lean;left.z-=2*lean;}
    public void look(float yaw,float pitch){getHead().yRot=yaw;getHead().xRot=pitch;}
    @Override public void translateToHand(HumanoidArm arm,PoseStack poses){
        (arm==HumanoidArm.RIGHT?right:left).translateAndRotate(poses);
        (arm==HumanoidArm.RIGHT?rightForearm:leftForearm).translateAndRotate(poses);
        poses.translate(0,4/16f,0);
    }
}
