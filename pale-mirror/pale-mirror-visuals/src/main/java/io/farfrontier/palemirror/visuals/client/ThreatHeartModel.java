package io.farfrontier.palemirror.visuals.client;

import io.farfrontier.palemirror.visuals.threat.ThreatHeartEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

final class ThreatHeartModel extends GeoModel<ThreatHeartEntity> {
    @Override public ResourceLocation getModelResource(ThreatHeartEntity entity) {
        return ResourceLocation.fromNamespaceAndPath("pale_mirror_visuals", "geo/threat_heart.geo.json");
    }
    @Override public ResourceLocation getTextureResource(ThreatHeartEntity entity) {
        return ResourceLocation.fromNamespaceAndPath("pale_mirror_visuals", "textures/entity/threat_heart.png");
    }
    @Override public ResourceLocation getAnimationResource(ThreatHeartEntity entity) {
        return ResourceLocation.fromNamespaceAndPath("pale_mirror_visuals", "animations/threat_heart.animation.json");
    }
}
