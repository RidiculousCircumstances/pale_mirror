package io.farfrontier.palemirror.internal.world;

import io.farfrontier.palemirror.api.PaleMirrorVisuals;
import io.farfrontier.palemirror.api.VisualStateProjection;
import io.farfrontier.palemirror.domain.ResourceKind;
import net.minecraft.server.MinecraftServer;

/** Read-only canonical projection. It grants no mutation authority to the visual provider. */
public final class VisualProjectionPublisher {
    private VisualProjectionPublisher() { }

    public static void publish(MinecraftServer server, PaleMirrorSavedData data) {
        var provider = PaleMirrorVisuals.provider().orElse(null);
        if (provider == null) return;
        for (var region : data.worldState().livingRegions().stream()
                .sorted(java.util.Comparator.comparing(value -> value.id())).toList()) {
            var facility = data.worldState().facility(region.primaryFacilityId()).orElse(null);
            var community = data.worldState().community(region.communityId()).orElse(null);
            var place = data.worldState().place(region.placeId()).orElse(null);
            var economy = data.worldState().economy(region.communityId()).orElse(null);
            var development = data.worldState().settlementDevelopment(region.communityId()).orElse(null);
            if (facility == null || community == null || place == null || economy == null) continue;
            var iron = economy.require(ResourceKind.IRON);
            provider.applyProjection(server.overworld(), new VisualStateProjection(facility.id().value(),
                    facility.desiredRevision(), facility.status().name(), place.structuralIntegrity().name(),
                    iron.availability().name(), community.crisisState().name(), development == null ? "NONE"
                    : Integer.toString(development.prosperity()), facility.threatTier().name()));
        }
    }
}
