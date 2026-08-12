package io.farfrontier.palemirror.internal.presentation;

import io.farfrontier.palemirror.domain.FacilityStatus;
import io.farfrontier.palemirror.internal.economy.SettlementDepotRecord;
import io.farfrontier.palemirror.internal.world.CampaignRegionRecord;
import io.farfrontier.palemirror.internal.world.PaleMirrorSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Readable, non-persistent signal effects at PM-owned mine/depot points.
 * This never writes a block near an observed village and never discovers a new
 * region: markers merely make an already materialized PM footprint legible.
 */
public final class RegionalMarkerRuntime {
    private static final long INTERVAL_TICKS = 40L;
    private static final double RANGE_SQUARED = 96.0D * 96.0D;

    private RegionalMarkerRuntime() { }

    public static void tick(MinecraftServer server, PaleMirrorSavedData data) {
        if (server.overworld().getGameTime() % INTERVAL_TICKS != 0L) return;
        for (CampaignRegionRecord region : data.campaignRegions().values()) {
            ServerLevel level = null;
            for (ServerLevel candidate : server.getAllLevels()) {
                if (candidate.dimension().location().toString().equals(region.dimensionId())) {
                    level = candidate;
                    break;
                }
            }
            if (level == null) continue;
            var facility = data.worldState().livingRegion(region.id()).flatMap(value -> data.worldState().facility(value.primaryFacilityId()))
                    .orElse(null);
            if (region.primaryMineAnchor() != null) signalNearby(level, region.primaryMineAnchor().above(5),
                    facility != null && facility.status() == FacilityStatus.INFECTED ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.END_ROD);
            SettlementDepotRecord depot = data.worldState().livingRegion(region.id())
                    .map(value -> data.settlementDepots().get(value.communityId())).orElse(null);
            if (depot != null && data.worldState().site(depot.siteId()).map(site ->
                    site.operationalState() == io.farfrontier.palemirror.domain.OperationalState.OPERATIONAL).orElse(false)) {
                boolean crisis = data.worldState().community(depot.communityId()).map(value -> !"NONE".equals(value.crisisState().name()))
                        .orElse(false);
                signalNearby(level, depot.anchor().above(3), crisis ? ParticleTypes.SMOKE : ParticleTypes.HAPPY_VILLAGER);
            }
        }
    }

    private static void signalNearby(ServerLevel level, BlockPos position, ParticleOptions particle) {
        for (ServerPlayer player : level.players()) {
            if (player.blockPosition().distSqr(position) > RANGE_SQUARED) continue;
            level.sendParticles(player, particle, true, position.getX() + 0.5D, position.getY() + 0.2D,
                    position.getZ() + 0.5D, 7, 0.35D, 0.6D, 0.35D, 0.01D);
        }
    }
}
