package io.farfrontier.palemirror.visuals.client;

import io.farfrontier.palemirror.visuals.threat.ThreatHeartEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

final class ThreatHeartRenderer extends GeoEntityRenderer<ThreatHeartEntity> {
    ThreatHeartRenderer(EntityRendererProvider.Context context) { super(context, new ThreatHeartModel()); withScale(1.35F); }
}
