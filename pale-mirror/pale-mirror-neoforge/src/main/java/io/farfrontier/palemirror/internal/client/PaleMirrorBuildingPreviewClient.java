package io.farfrontier.palemirror.internal.client;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.internal.network.BuildingPreviewPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/** Real registered block models over the live building, only for the requesting operator.
 * No client blocks/entities/collisions are inserted. One replaced preview expires after 600 client ticks. */
@EventBusSubscriber(modid=PaleMirrorMod.MOD_ID, value=Dist.CLIENT)
public final class PaleMirrorBuildingPreviewClient {
    private static BuildingPreviewPayload preview;
    private static int remaining;
    private static net.minecraft.client.multiplayer.ClientLevel receivingWorld;
    public static void receive(BuildingPreviewPayload value){preview=value;receivingWorld=Minecraft.getInstance().level;remaining=600;}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        var level=Minecraft.getInstance().level;
        if(level==null || level!=receivingWorld || remaining--<=0 || preview!=null && !preview.dimension().equals(level.dimension().location()))preview=null;
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event){
        var value=preview; var client=Minecraft.getInstance();
        if(value==null || client.level==null || client.level!=receivingWorld || event.getStage()!=RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS
                || !value.dimension().equals(client.level.dimension().location()))return;
        var pose=event.getPoseStack();var camera=event.getCamera().getPosition();
        var buffers=client.renderBuffers().bufferSource();
        for(var equipment:value.equipment()){
            var p=equipment.position();
            if(p.distToCenterSqr(camera.x,camera.y,camera.z)>128*128 || !client.level.hasChunkAt(p))continue;
            pose.pushPose();
            // A slight enlargement distinguishes the draft model from the current physical block
            // without changing either world's state or hiding its structural neighbours.
            pose.translate(p.getX()-camera.x-.002,p.getY()-camera.y-.002,p.getZ()-camera.z-.002);pose.scale(1.004f,1.004f,1.004f);
            client.getBlockRenderer().renderSingleBlock(equipment.state(),pose,buffers,LightTexture.FULL_BRIGHT,OverlayTexture.NO_OVERLAY);
            pose.popPose();
        }
        buffers.endBatch();
    }
    private PaleMirrorBuildingPreviewClient(){}
}
