package io.farfrontier.palemirror.internal.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.VillagerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.entity.layers.*;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemDisplayContext;

/** Retains the ordinary villager model layer and texture pipeline for CEM/ETF. */
final class HumanVillagerRenderer extends VillagerRenderer {
    private final ModelPart root;
    HumanVillagerRenderer(EntityRendererProvider.Context context){
        super(context);this.root=model.root();
        if(!HumanEraVisuals.supports(root))throw new IllegalStateException("The enabled Human Era profile does not match the baked villager rig");
        layers.removeIf(layer->layer instanceof CrossedArmsItemLayer<?,?>);
        addLayer(new RenderLayer<Villager,VillagerModel<Villager>>(this){
            @Override public void render(PoseStack pose,MultiBufferSource buffer,int light,Villager actor,float swing,float amount,float partial,float age,float yaw,float pitch){
                var rig=HumanEraVisuals.current(HumanVillagerRenderer.this.root).orElse(null);if(rig==null)return;
                for(var hand:HumanoidArm.values()){
                    var stack=PaleMirrorResidentMealClient.display(actor,hand,hand==actor.getMainArm()?actor.getMainHandItem():actor.getOffhandItem());if(stack.isEmpty())continue;
                    pose.pushPose();rig.hand(hand,pose);pose.mulPose(Axis.XP.rotationDegrees(-90));pose.mulPose(Axis.YP.rotationDegrees(180));
                    boolean left=hand==HumanoidArm.LEFT;// Human Era arm boxes point inward from the shoulder, unlike vanilla player arms.
                    pose.translate((left?1:-1)/16f,0.125,-0.625);
                    context.getItemInHandRenderer().renderItem(actor,stack,left?ItemDisplayContext.THIRD_PERSON_LEFT_HAND:ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,left,pose,buffer,light);
                    pose.popPose();
                }
            }
        });
    }
    @Override protected void scale(Villager actor,PoseStack pose,float partial){
        super.scale(actor,pose,partial);
        // Human Era/FA lowers its complete riding pose by six model pixels.
        // PM's chair already positions the actual passenger at seated hip height.
        // Compensate only this render stack; dismount/other actors inherit no pose.
        if(io.farfrontier.palemirror.internal.frontier.v3.PaleMirrorSeatEntities.isResidentSeat(actor.getVehicle()))
            pose.translate(0,-6.0/16.0,0);
    }
    @Override protected void setupRotations(Villager actor,PoseStack pose,float age,float yaw,float partial,float scale){
        super.setupRotations(actor,pose,age,ResidentVisuals.facingYaw(actor,partial,yaw),partial,scale);
    }
    static EntityRenderer<Villager> create(EntityRendererProvider.Context context){
        return new HumanVillagerRenderer(context);
    }
}
