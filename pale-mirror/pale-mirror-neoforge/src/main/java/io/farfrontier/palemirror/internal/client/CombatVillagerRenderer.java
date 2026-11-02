package io.farfrontier.palemirror.internal.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.entity.layers.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.npc.Villager;

/** Presentation extension; vanilla identities, appearance and noncombat rendering remain intact. */
final class CombatVillagerRenderer extends MobRenderer<Villager,CombatVillagerModel> {
    private static final ResourceLocation SKIN=ResourceLocation.withDefaultNamespace("textures/entity/villager/villager.png");
    CombatVillagerRenderer(EntityRendererProvider.Context context){
        super(context,new CombatVillagerModel(context.bakeLayer(PaleMirrorCombatRenderers.LAYER)),0.5f);
        addLayer(new CustomHeadLayer<>(this,context.getModelSet(),context.getItemInHandRenderer()));
        addLayer(new VillagerProfessionLayer<>(this,context.getResourceManager(),"villager"));
        addLayer(new CrossedArmsItemLayer<>(this,context.getItemInHandRenderer()){
            @Override public void render(PoseStack pose,MultiBufferSource buffer,int light,Villager actor,float swing,float amount,float partial,float age,float yaw,float pitch){
                if(!getParentModel().combat())super.render(pose,buffer,light,actor,swing,amount,partial,age,yaw,pitch);
            }
        });
        addLayer(new ItemInHandLayer<>(this,context.getItemInHandRenderer()){
            @Override public void render(PoseStack pose,MultiBufferSource buffer,int light,Villager actor,float swing,float amount,float partial,float age,float yaw,float pitch){
                if(getParentModel().combat())super.render(pose,buffer,light,actor,swing,amount,partial,age,yaw,pitch);
            }
        });
    }
    @Override protected void setupRotations(Villager actor,PoseStack pose,float age,float yaw,float partial,float scale){
        var frame=PaleMirrorCombatClient.frame(actor,partial);
        super.setupRotations(actor,pose,age,frame==null?yaw:MeleeAnimation.facingYaw(frame,yaw),partial,scale);
    }
    public ResourceLocation getTextureLocation(Villager actor){return SKIN;}
    @Override protected void scale(Villager actor,PoseStack pose,float partial){float factor=0.9375f*actor.getAgeScale();pose.scale(factor,factor,factor);}
    @Override protected float getShadowRadius(Villager actor){return actor.isBaby()?super.getShadowRadius(actor)*0.5f:super.getShadowRadius(actor);}
}
